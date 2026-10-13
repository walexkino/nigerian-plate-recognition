import Cocoa
import WebKit
import Vision
import ImageIO
import AVFoundation
import UniformTypeIdentifiers

func jpegURL(_ image: CGImage) -> String {
    let bitmap = NSBitmapImageRep(cgImage: image)
    let data = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.94])!
    return "data:image/jpeg;base64," + data.base64EncodedString()
}

func recognize(_ data: Data) throws -> [[String: Any]] {
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

if CommandLine.arguments.count > 2 && CommandLine.arguments[1] == "--ocr" {
    do {
        let result = try recognize(Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2])))
        let encoded = try JSONSerialization.data(withJSONObject: result, options: [.sortedKeys])
        print(String(data: encoded, encoding: .utf8)!)
    } catch {
        fputs("Recognition failed: \(error.localizedDescription)\n", stderr)
        exit(1)
    }
} else {
    final class AppDelegate: NSObject, NSApplicationDelegate, WKScriptMessageHandler, WKUIDelegate {
        var window: NSWindow!
        var web: WKWebView!
        var busy = false
        var videoURL: URL?
        func deliver(_ function: String, _ payload: [String: Any]) {
            guard let data = try? JSONSerialization.data(withJSONObject: payload), let json = String(data: data, encoding: .utf8) else { return }
            web.evaluateJavaScript("window.\(function)(\(json))", completionHandler: nil)
        }
        func applicationDidFinishLaunching(_ notification: Notification) {
            let menu = NSMenu(); let item = NSMenuItem(); menu.addItem(item)
            let appMenu = NSMenu(); appMenu.addItem(withTitle: "Quit PlateLab", action: #selector(NSApplication.terminate(_:)), keyEquivalent: "q"); item.submenu = appMenu
            let editItem = NSMenuItem(); menu.addItem(editItem)
            let editMenu = NSMenu(title: "Edit")
            for (title, action, key) in [("Copy", "copy:", "c"), ("Cut", "cut:", "x"), ("Paste", "paste:", "v"), ("Select All", "selectAll:", "a")] {
                editMenu.addItem(withTitle: title, action: Selector(action), keyEquivalent: key)
            }
            editItem.submenu = editMenu
            NSApp.mainMenu = menu
            let controller = WKUserContentController()
            for name in ["recognize", "exportReport", "importMedia", "videoFrame"] { controller.add(self, name: name) }
            let configuration = WKWebViewConfiguration(); configuration.userContentController = controller
            configuration.mediaTypesRequiringUserActionForPlayback = []
            web = WKWebView(frame: .zero, configuration: configuration); web.uiDelegate = self
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1380, height: 940), styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
            window.title = "PlateLab · Recognition Studio"; window.minSize = NSSize(width: 900, height: 680)
            window.contentView = web; window.center(); window.makeKeyAndOrderFront(nil)
            let resource = Bundle.main.resourceURL!
            web.loadFileURL(resource.appendingPathComponent("index.html"), allowingReadAccessTo: resource)
            NSApp.activate(ignoringOtherApps: true)
        }
        func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
        func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping ([URL]?) -> Void) {
            let panel = NSOpenPanel(); panel.canChooseDirectories = false; panel.allowsMultipleSelection = false
            panel.begin { response in completionHandler(response == .OK ? panel.urls : nil) }
        }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if message.name == "importMedia", !busy {
                let panel = NSOpenPanel(); panel.canChooseDirectories = false; panel.allowsMultipleSelection = false
                panel.allowedContentTypes = [.image, .movie]
                panel.beginSheetModal(for: window) { response in
                    guard response == .OK, let url = panel.url else { return }
                    do {
                        let type = try url.resourceValues(forKeys: [.contentTypeKey]).contentType
                        if type?.conforms(to: .movie) == true {
                            self.videoURL = url
                            let asset = AVURLAsset(url: url)
                            let duration = CMTimeGetSeconds(asset.duration)
                            guard duration.isFinite && duration > 0 else { throw NSError(domain: "PlateLab", code: 2, userInfo: [NSLocalizedDescriptionKey: "Video duration unavailable"]) }
                            self.deliver("nativeMedia", ["name": url.lastPathComponent, "type": "video", "duration": duration])
                        } else {
                            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                                  let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 1280] as CFDictionary) else { throw NSError(domain: "PlateLab", code: 3, userInfo: [NSLocalizedDescriptionKey: "Cannot decode image"]) }
                            self.videoURL = nil
                            self.deliver("nativeMedia", ["name": url.lastPathComponent, "type": "image", "image": jpegURL(image)])
                        }
                    } catch { self.deliver("nativeMedia", ["error": error.localizedDescription]) }
                }
                return
            }
            if message.name == "videoFrame", let body = message.body as? [String: Any], let id = body["id"] as? String, let seconds = body["time"] as? Double, seconds.isFinite, seconds >= 0, let url = videoURL {
                DispatchQueue.global(qos: .userInitiated).async {
                    let payload: [String: Any] = autoreleasepool {
                        do {
                            let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
                            generator.appliesPreferredTrackTransform = true
                            generator.maximumSize = CGSize(width: 1280, height: 1280)
                            generator.requestedTimeToleranceBefore = .zero; generator.requestedTimeToleranceAfter = .zero
                            let image = try generator.copyCGImage(at: CMTime(seconds: seconds, preferredTimescale: 600), actualTime: nil)
                            return ["id": id, "image": jpegURL(image)]
                        } catch { return ["id": id, "error": error.localizedDescription] }
                    }
                    DispatchQueue.main.async { self.deliver("nativeResult", payload) }
                }
                return
            }
            if message.name == "exportReport", let report = message.body as? String {
                let panel = NSSavePanel(); panel.nameFieldStringValue = "platelab-results.json"
                panel.begin { response in
                    if response == .OK, let url = panel.url {
                        do { try report.write(to: url, atomically: true, encoding: .utf8) }
                        catch { let alert = NSAlert(); alert.messageText = "Could not save report"; alert.informativeText = error.localizedDescription; alert.runModal() }
                    }
                }
                return
            }
            guard !busy, let body = message.body as? [String: Any], let id = body["id"] as? String,
                  let base64 = body["image"] as? String, base64.count < 12_000_000,
                  let data = Data(base64Encoded: base64) else { return }
            busy = true
            DispatchQueue.global(qos: .userInitiated).async {
                let start = Date()
                let response: [String: Any] = autoreleasepool {
                    do { return ["id": id, "observations": try recognize(data), "elapsedMs": Date().timeIntervalSince(start)*1000] }
                    catch { return ["id": id, "error": error.localizedDescription] }
                }
                let json = try! JSONSerialization.data(withJSONObject: response, options: [.sortedKeys])
                let text = String(data: json, encoding: .utf8)!
                DispatchQueue.main.async {
                    self.busy = false
                    self.web.evaluateJavaScript("window.nativeResult(\(text))", completionHandler: nil)
                }
            }
        }
    }
    let app = NSApplication.shared
    let delegate = AppDelegate(); app.delegate = delegate
    app.setActivationPolicy(.regular); app.run()
}
