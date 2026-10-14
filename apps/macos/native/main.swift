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

func videoFrame(_ url: URL, at seconds: Double) throws -> [String: Any] {
    let generator = AVAssetImageGenerator(asset: AVURLAsset(url: url))
    generator.appliesPreferredTrackTransform = true
    generator.maximumSize = CGSize(width: 1280, height: 1280)
    generator.requestedTimeToleranceBefore = .zero
    generator.requestedTimeToleranceAfter = .zero
    var actualTime = CMTime.zero
    let image = try generator.copyCGImage(at: CMTime(seconds: seconds, preferredTimescale: 600), actualTime: &actualTime)
    return ["image": jpegURL(image), "time": CMTimeGetSeconds(actualTime)]
}

if CommandLine.arguments.count > 2 && ["--ocr", "--ocr-baseline"].contains(CommandLine.arguments[1]) {
    do {
        let data = try Data(contentsOf: URL(fileURLWithPath: CommandLine.arguments[2]))
        let result = try CommandLine.arguments[1] == "--ocr-baseline" ? recognizeBaseline(data) : recognize(data)
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
        var importing = false
        var videoURL: URL?
        let liveFeed = LiveFeed()
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
            for name in ["recognize", "exportReport", "importMedia", "videoFrame", "connectLive", "liveFrame", "disconnectLive", "supportWallet", "copySupportAddress"] { controller.add(self, name: name) }
            let configuration = WKWebViewConfiguration(); configuration.userContentController = controller
            configuration.mediaTypesRequiringUserActionForPlayback = []
            web = WKWebView(frame: .zero, configuration: configuration); web.uiDelegate = self
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1380, height: 940), styleMask: [.titled, .closable, .miniaturizable, .resizable], backing: .buffered, defer: false)
            window.title = "PlateLab · Recognition Studio"; window.minSize = NSSize(width: 360, height: 520)
            window.contentView = web; window.center(); window.makeKeyAndOrderFront(nil)
            let resource = Bundle.main.resourceURL!
            web.loadFileURL(resource.appendingPathComponent("index.html"), allowingReadAccessTo: resource)
            NSApp.activate(ignoringOtherApps: true)
        }
        func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { true }
        func applicationWillTerminate(_ notification: Notification) { liveFeed.stop() }
        func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping ([URL]?) -> Void) {
            let panel = NSOpenPanel(); panel.canChooseDirectories = false; panel.allowsMultipleSelection = false
            panel.begin { response in completionHandler(response == .OK ? panel.urls : nil) }
        }
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if ["supportWallet", "copySupportAddress"].contains(message.name) {
                guard let body = message.body as? [String: Any], let id = body["id"] as? String else { return }
                let address = "3DhdtBbkE7TsiZzAU5JJuV3M7dYzygj8R2"
                if message.name == "copySupportAddress" {
                    NSPasteboard.general.clearContents()
                    let copied = NSPasteboard.general.setString(address, forType: .string)
                    deliver("nativeResult", copied ? ["id": id] : ["id": id, "error": "Could not copy the address."])
                } else {
                    let walletURL = URL(string: "bitcoin:" + address)!
                    let opened = NSWorkspace.shared.urlForApplication(toOpen: walletURL) != nil && NSWorkspace.shared.open(walletURL)
                    deliver("nativeResult", opened ? ["id": id] : ["id": id, "error": "No Bitcoin wallet is available. Scan the QR code with your wallet, or copy the address."])
                }
                return
            }
            if ["connectLive", "liveFrame", "disconnectLive"].contains(message.name) {
                guard let body = message.body as? [String: Any], let id = body["id"] as? String else { return }
                if message.name == "disconnectLive" {
                    liveFeed.stop(); deliver("nativeResult", ["id": id]); return
                }
                if message.name == "liveFrame" {
                    switch liveFeed.frame(after: body["sequence"] as? Int ?? 0) {
                    case .success(var payload): payload["id"] = id; deliver("nativeResult", payload)
                    case .failure(let error): deliver("nativeResult", ["id": id, "error": error.localizedDescription])
                    }
                    return
                }
                guard !busy && !importing, let text = body["url"] as? String, let url = URL(string: text),
                      let resource = Bundle.main.resourceURL else {
                    deliver("nativeResult", ["id": id, "error": "Enter a valid camera URL when the app is ready."]); return
                }
                liveFeed.start(url: url, decoder: resource.appendingPathComponent("ffmpeg")) { result in
                    switch result {
                    case .success(var payload): payload["id"] = id; self.deliver("nativeResult", payload)
                    case .failure(let error): self.deliver("nativeResult", ["id": id, "error": error.localizedDescription])
                    }
                }
                return
            }
            if message.name == "importMedia" {
                guard !busy && !importing else { return }
                importing = true
                let panel = NSOpenPanel(); panel.canChooseDirectories = false; panel.allowsMultipleSelection = false
                panel.allowedContentTypes = [.image, .movie]
                panel.beginSheetModal(for: window) { response in
                    guard response == .OK, let url = panel.url else { self.importing = false; return }
                    DispatchQueue.global(qos: .userInitiated).async {
                      let payload: [String: Any] = autoreleasepool {
                       do {
                        let type = try url.resourceValues(forKeys: [.contentTypeKey]).contentType
                        if type?.conforms(to: .movie) == true {
                            let asset = AVURLAsset(url: url)
                            let duration = CMTimeGetSeconds(asset.duration)
                            guard duration.isFinite && duration > 0 else { throw NSError(domain: "PlateLab", code: 2, userInfo: [NSLocalizedDescriptionKey: "Video duration unavailable"]) }
                            var result = try videoFrame(url, at: 0)
                            result["name"] = url.lastPathComponent
                            result["type"] = "video"
                            result["duration"] = duration
                            return result
                        } else {
                            guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                                  let image = CGImageSourceCreateThumbnailAtIndex(source, 0, [kCGImageSourceCreateThumbnailFromImageAlways: true, kCGImageSourceCreateThumbnailWithTransform: true, kCGImageSourceThumbnailMaxPixelSize: 1280] as CFDictionary) else { throw NSError(domain: "PlateLab", code: 3, userInfo: [NSLocalizedDescriptionKey: "Cannot decode image"]) }
                            return ["name": url.lastPathComponent, "type": "image", "image": jpegURL(image)]
                        }
                       } catch { return ["error": error.localizedDescription] }
                      }
                      DispatchQueue.main.async {
                        self.importing = false
                        if payload["error"] == nil { self.videoURL = payload["type"] as? String == "video" ? url : nil }
                        self.deliver("nativeMedia", payload)
                      }
                    }
                }
                return
            }
            if message.name == "videoFrame" {
                guard let body = message.body as? [String: Any], let id = body["id"] as? String else { return }
                guard !importing, let seconds = body["time"] as? Double, seconds.isFinite, seconds >= 0, let url = videoURL else {
                    deliver("nativeResult", ["id": id, "error": "No video is ready for the requested frame"])
                    return
                }
                DispatchQueue.global(qos: .userInitiated).async {
                    let payload: [String: Any] = autoreleasepool {
                        do {
                            var result = try videoFrame(url, at: seconds)
                            result["id"] = id
                            return result
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
            guard message.name == "recognize", let body = message.body as? [String: Any], let id = body["id"] as? String else { return }
            guard !busy && !importing else {
                deliver("nativeResult", ["id": id, "error": "Wait for the current operation to finish"])
                return
            }
            guard let base64 = body["image"] as? String, base64.count < 12_000_000,
                  let data = Data(base64Encoded: base64) else {
                deliver("nativeResult", ["id": id, "error": "Invalid recognition image"])
                return
            }
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
