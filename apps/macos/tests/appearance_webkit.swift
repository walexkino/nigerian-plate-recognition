import Cocoa
import WebKit

// Native WebKit layout QA uses generated/empty UI only, never dataset images.
final class AppearanceQA: NSObject, NSApplicationDelegate, WKNavigationDelegate {
    var window: NSWindow!
    var web: WKWebView!
    var index = 0
    let cases = [320, 360, 768, 1024, 1440].flatMap { width in ["dark", "light"].map { (width, $0) } }
    var failures = 0
    func applicationDidFinishLaunching(_ note: Notification) {
        DispatchQueue.main.asyncAfter(deadline: .now()+60) { print("WebKit QA timed out"); exit(2) }
        web = WKWebView(frame: .zero); web.navigationDelegate = self
        window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 1440, height: 900), styleMask: [.titled, .resizable], backing: .buffered, defer: false)
        window.contentView = web; window.orderFront(nil)
        let root = URL(fileURLWithPath: CommandLine.arguments[1])
        web.loadFileURL(root.appendingPathComponent("index.html"), allowingReadAccessTo: root)
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { runCase() }
    func runCase() {
        if index == cases.count { print("WebKit appearance/layout cases: \(cases.count), failures: \(failures)"); exit(failures == 0 ? 0 : 1) }
        let (width, theme) = cases[index]
        window.setContentSize(NSSize(width: width, height: 900))
        web.evaluateJavaScript("if(document.documentElement.dataset.theme !== '\(theme)') document.querySelector('#theme-toggle').click();") { _, error in
            if let error = error { print(error); self.failures += 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { self.check(width, theme, modal: false) }
        }
    }
    func check(_ width: Int, _ theme: String, modal: Bool) {
        let script = """
        (()=>{const root=document.documentElement, rail=document.querySelector('.ps-rail'), buttons=[...document.querySelectorAll('.ps-header-actions button')]; return JSON.stringify({width:innerWidth,scroll:root.scrollWidth,theme:root.dataset.theme,bg:getComputedStyle(document.querySelector('#plate-studio')).backgroundColor,nav:getComputedStyle(rail).display,buttons:buttons.every(b=>b.getBoundingClientRect().right<=innerWidth&&b.getBoundingClientRect().width>0),dialog:!document.querySelector('dialog').open||document.querySelector('dialog').scrollWidth<=document.querySelector('dialog').clientWidth});})()
        """
        web.evaluateJavaScript(script) { result, error in
            var passed = false
            if let value = result as? String, let bytes = value.data(using: .utf8), let data = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any] {
                passed = (data["scroll"] as? Int ?? Int.max) <= width + 1 && data["theme"] as? String == theme && data["nav"] as? String != "none" && data["buttons"] as? Bool == true && data["dialog"] as? Bool == true && data["bg"] as? String == (theme == "light" ? "rgb(245, 247, 247)" : "rgb(16, 19, 21)")
                print("\(passed ? "PASS" : "FAIL") \(width)px \(theme) \(modal ? "support" : "workspace"): \(value)")
            } else { print("JS failed: \(String(describing: error))") }
            if !passed { self.failures += 1 }
            if !modal {
                self.web.evaluateJavaScript("document.querySelector('#support').click()") { _, _ in DispatchQueue.main.asyncAfter(deadline: .now()+0.1) {self.check(width, theme, modal: true)} }
            } else {
                self.web.evaluateJavaScript("document.querySelector('#close-dialog').click()") { _, _ in
                    if (width == 360 && theme == "dark") || (width == 1440 && theme == "light") {
                        self.web.takeSnapshot(with: nil) { image, _ in
                            if let image=image, let tiff=image.tiffRepresentation, let bitmap=NSBitmapImageRep(data:tiff), let png=bitmap.representation(using:.png,properties:[:]) { try? png.write(to:URL(fileURLWithPath:CommandLine.arguments[2]).appendingPathComponent("webkit-\(width)-\(theme).png")) }
                            self.index += 1; self.runCase()
                        }
                    } else { self.index += 1; self.runCase() }
                }
            }
        }
    }
}
let delegate = AppearanceQA()
NSApplication.shared.delegate = delegate
NSApplication.shared.run()
