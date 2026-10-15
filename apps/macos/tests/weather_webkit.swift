import Cocoa
import WebKit

// Publicly invented weather fixture; no device location or dataset imagery is requested.
final class WeatherQA: NSObject, NSApplicationDelegate, WKNavigationDelegate {
    var web: WKWebView!; var window: NSWindow!; var index = 0; var failures = 0
    let cases = [(320,"dark"),(320,"light"),(768,"dark"),(1440,"light")]
    let fixture = "{current:{temperature_2m:28.4,apparent_temperature:31,weather_code:63,relative_humidity_2m:80,wind_speed_10m:9,time:'2026-10-15T14:00'},timezone_abbreviation:'WAT',daily:{time:['2026-10-15','2026-10-16','2026-10-17'],weather_code:[63,2,0],temperature_2m_max:[30,31,32],temperature_2m_min:[24,25,25],precipitation_probability_max:[70,null,0]}}"
    func applicationDidFinishLaunching(_ n: Notification) {
        DispatchQueue.main.asyncAfter(deadline:.now()+50) { exit(2) }
        web = WKWebView(frame:.zero); web.navigationDelegate = self
        window = NSWindow(contentRect:NSRect(x:0,y:0,width:1440,height:900),styleMask:[.titled,.resizable],backing:.buffered,defer:false);window.contentView=web;window.orderFront(nil)
        let root=URL(fileURLWithPath:CommandLine.arguments[1]);web.loadFileURL(root.appendingPathComponent("index.html"),allowingReadAccessTo:root)
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { run() }
    func run() {
        if index == cases.count { print("Weather WebKit: \(cases.count) layouts, \(failures) failures");exit(failures == 0 ? 0 : 1) }
        let (width,theme)=cases[index];window.setContentSize(NSSize(width:width,height:900))
        let js="""
        (async()=>{if(document.documentElement.dataset.theme!=='\(theme)')document.querySelector('#theme-toggle').click();window.fetch=async(url)=>({ok:true,json:async()=>String(url).includes('photon')?{results:[{name:'Lagos',country:'Nigeria',latitude:6.52,longitude:3.38}]}:\(fixture)});document.querySelector('#weather').click();document.querySelector('.weather-search input').value='Lagos';document.querySelector('.weather-search').dispatchEvent(new Event('submit',{cancelable:true}));})()
        """
        web.evaluateJavaScript(js) { _,_ in DispatchQueue.main.asyncAfter(deadline:.now()+0.15) {
            self.web.evaluateJavaScript("document.querySelector('.weather-city').click()") { _,_ in DispatchQueue.main.asyncAfter(deadline:.now()+0.15) { self.check(width,theme) } }
        } }
    }
    func check(_ width:Int,_ theme:String) {
        web.evaluateJavaScript("JSON.stringify({days:document.querySelectorAll('.weather-day').length,temp:document.querySelector('.weather-temperature')?.textContent,missing:[...document.querySelectorAll('.weather-day small')].map(x=>x.textContent),overflow:document.querySelector('dialog').scrollWidth>document.querySelector('dialog').clientWidth,body:document.documentElement.scrollWidth>innerWidth,zone:document.querySelector('.weather-time')?.textContent})") { result,error in
            var ok=false
            if let s=result as? String, let d=s.data(using:.utf8), let j=try? JSONSerialization.jsonObject(with:d) as? [String:Any] {
                ok=j["days"] as? Int == 3 && j["temp"] as? String == "28°C" && j["overflow"] as? Bool == false && j["body"] as? Bool == false && (j["missing"] as? [String]) == ["Rain chance 70%","Rain chance —","Rain chance 0%"]
                print("\(ok ? "PASS" : "FAIL") weather \(width) \(theme): \(s)")
            } else { print(String(describing:error)) }
            if !ok { self.failures += 1 }
            self.web.takeSnapshot(with:nil) { image,_ in
                if let tiff=image?.tiffRepresentation, let bitmap=NSBitmapImageRep(data:tiff), let png=bitmap.representation(using:.png,properties:[:]) {try? png.write(to:URL(fileURLWithPath:CommandLine.arguments[2]).appendingPathComponent("weather-\(width)-\(theme).png"))}
                self.web.evaluateJavaScript("document.querySelector('#close-dialog').click()") { _,_ in self.index += 1;self.run() }
            }
        }
    }
}
let delegate=WeatherQA();NSApplication.shared.delegate=delegate;NSApplication.shared.run()
