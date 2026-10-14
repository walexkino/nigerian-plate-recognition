import Cocoa
import Vision
import CoreImage
import ImageIO

func recognizeBaseline(_ data: Data) throws -> [[String: Any]] {
    guard let source = CGImageSourceCreateWithData(data as CFData, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        throw NSError(domain: "PlateLab", code: 1, userInfo: [NSLocalizedDescriptionKey: "Cannot decode image"])
    }
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.recognitionLanguages = ["en-US"]
    request.usesLanguageCorrection = false
    request.minimumTextHeight = 0.008
    try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
    return (request.results ?? []).map { observation in
        let b = observation.boundingBox
        return ["box": [b.minX, 1-b.maxY, b.width, b.height],
                "alternatives": observation.topCandidates(3).map {
                    ["text": $0.string, "confidence": Double($0.confidence)] as [String: Any]
                }] as [String: Any]
    }
}

// Variants only resample/filter observed pixels. They do not synthesize hidden text.
private let pixelContext = CIContext(options: [.cacheIntermediates: false])

private func readText(_ image: CGImage, region: CGRect, pass: String) throws -> [[String: Any]] {
    let request = VNRecognizeTextRequest()
    request.recognitionLevel = .accurate
    request.recognitionLanguages = ["en-US"]
    request.usesLanguageCorrection = false
    request.minimumTextHeight = 0.006
    try VNImageRequestHandler(cgImage: image, options: [:]).perform([request])
    return (request.results ?? []).map { observation in
        let b = observation.boundingBox
        return ["box": [region.minX+b.minX*region.width,region.minY+(1-b.maxY)*region.height,b.width*region.width,b.height*region.height].map(Double.init),
                "pass": pass,
                "alternatives": observation.topCandidates(3).map { ["text": $0.string, "confidence": Double($0.confidence)] as [String: Any] }]
    }
}

private func resample(_ image: CGImage, scale: Double, contrast: Bool, redChannel: Bool = false) -> CGImage? {
    var ci = CIImage(cgImage: image)
    if redChannel {
        let red = CIVector(x: 1, y: 0, z: 0, w: 0)
        ci = ci.applyingFilter("CIColorMatrix", parameters: ["inputRVector": red, "inputGVector": red, "inputBVector": red])
    }
    if contrast {
        ci = ci.applyingFilter("CIColorControls", parameters: [kCIInputSaturationKey: 0.0, kCIInputContrastKey: 1.35])
    }
    ci = ci.applyingFilter("CILanczosScaleTransform", parameters: [kCIInputScaleKey: scale, kCIInputAspectRatioKey: 1.0])
    return pixelContext.createCGImage(ci, from: ci.extent)
}

private func textHasPlateSignal(_ text: String) -> Bool {
    let chars = text.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }
    let digits = chars.filter { CharacterSet.decimalDigits.contains($0) }.count
    return chars.count >= 5 && chars.count <= 12 && digits >= 2 && digits < chars.count
}

func recognize(_ data: Data) throws -> [[String: Any]] {
    guard let source = CGImageSourceCreateWithData(data as CFData, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        throw NSError(domain: "PlateLab", code: 1, userInfo: [NSLocalizedDescriptionKey: "Cannot decode image"])
    }
    let full = CGRect(x: 0, y: 0, width: 1, height: 1)
    var observations = try readText(image, region: full, pass: "original")
    // A second scale helps text detection before spending work on small regions.
    let scale = min(2.5, 1792.0 / Double(max(image.width,image.height)))
    if let enlarged = resample(image, scale: max(1,scale), contrast: false) {
        observations += try readText(enlarged, region: full, pass: "scaled")
    }
    // Blue/green ink can retain stronger contrast in the red sensor channel.
    if let red = resample(image, scale: max(1,scale), contrast: true, redChannel: true) {
        observations += try readText(red, region: full, pass: "red-channel")
    }
    var regions: [CGRect] = []
    for observation in observations {
        guard let alternatives = observation["alternatives"] as? [[String: Any]],
              alternatives.contains(where: { textHasPlateSignal($0["text"] as? String ?? "") }),
              let b = observation["box"] as? [Double] else { continue }
        let region = CGRect(x: b[0]-b[2]*0.18, y: b[1]-b[3]*0.45, width: b[2]*1.36, height: b[3]*1.9).intersection(full)
        if !regions.contains(where: { $0.intersection(region).width*$0.intersection(region).height > min($0.width*$0.height,region.width*region.height)*0.5 }) {
            regions.append(region)
        }
    }
    // No detection is not proof of no plate: inspect overlapping tiles as a fallback.
    if regions.isEmpty && image.height > 150 && Double(image.width)/Double(image.height) < 3 {
        for (index, region) in [CGRect(x:0,y:0,width:0.65,height:0.7),CGRect(x:0.35,y:0,width:0.65,height:0.7),CGRect(x:0,y:0.3,width:0.65,height:0.7),CGRect(x:0.35,y:0.3,width:0.65,height:0.7)].enumerated() {
            let pixels = CGRect(x:region.minX*Double(image.width),y:region.minY*Double(image.height),width:region.width*Double(image.width),height:region.height*Double(image.height)).integral
            if let crop = image.cropping(to: pixels), let enlarged = resample(crop, scale: min(3,1280/Double(crop.width)), contrast: true) {
                let mapped = CGRect(x:pixels.minX/Double(image.width),y:pixels.minY/Double(image.height),width:pixels.width/Double(image.width),height:pixels.height/Double(image.height))
                observations += try readText(enlarged, region: mapped, pass: "tile-\(index)")
            }
        }
    } else {
        for (index, region) in regions.prefix(3).enumerated() {
            let pixels = CGRect(x:region.minX*Double(image.width),y:region.minY*Double(image.height),width:region.width*Double(image.width),height:region.height*Double(image.height)).integral.intersection(CGRect(x:0,y:0,width:image.width,height:image.height))
            guard let crop = image.cropping(to: pixels) else { continue }
            let mapped = CGRect(x:pixels.minX/Double(image.width),y:pixels.minY/Double(image.height),width:pixels.width/Double(image.width),height:pixels.height/Double(image.height))
            for contrast in [false,true] {
                if let enlarged = resample(crop, scale: min(5,960/Double(crop.width)), contrast: contrast) {
                    observations += try readText(enlarged, region: mapped, pass: "crop-\(index)-\(contrast ? "contrast" : "colour")")
                }
            }
        }
    }
    return observations
}
