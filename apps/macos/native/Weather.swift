import Foundation
import CoreLocation
import Cocoa

// One-shot, foreground location. Coordinates are rounded before leaving the device.
final class WeatherService: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private var locationReply: (([String: Any]) -> Void)?
    private var timer: Timer?
    private var task: URLSessionDataTask?
    private let session = URLSession(configuration: .ephemeral)
    override init() { super.init(); manager.delegate = self; manager.desiredAccuracy = kCLLocationAccuracyKilometer }
    func cancel() {
        timer?.invalidate(); timer = nil; manager.stopUpdatingLocation()
        let reply = locationReply; locationReply = nil; reply?(["error": "Location request canceled."])
        task?.cancel(); task = nil
    }
    func handle(_ action: String, _ body: [String: Any], reply: @escaping ([String: Any]) -> Void) {
        if action == "weatherCancel" { cancel(); reply([:]); return }
        if action == "weatherSource" { NSWorkspace.shared.open(URL(string: "https://open-meteo.com/en/licence")!); reply([:]); return }
        if action == "weatherLocation" {
            cancel(); locationReply = reply
            timer = Timer.scheduledTimer(withTimeInterval: 20, repeats: false) { [weak self] _ in self?.finishLocation(["error": "Location timed out. Search for a city instead."]) }
            switch manager.authorizationStatus {
            case .notDetermined: manager.requestWhenInUseAuthorization()
            case .authorized: manager.requestLocation()
            default: finishLocation(["error": "Location access is disabled. Search for a city or enable PlateLab in System Settings → Privacy & Security → Location Services."])
            }
            return
        }
        var url: URLComponents
        if action == "weatherSearch" {
            guard let query = body["query"] as? String, (2...100).contains(query.trimmingCharacters(in: .whitespacesAndNewlines).count) else { reply(["error": "Enter a city name."]); return }
            url = URLComponents(string: "https://geocoding-api.open-meteo.com/v1/search")!
            url.queryItems = [URLQueryItem(name: "name", value: query), URLQueryItem(name: "count", value: "5"), URLQueryItem(name: "language", value: "en"), URLQueryItem(name: "format", value: "json")]
        } else {
            guard action == "weatherForecast", let lat = body["latitude"] as? Double, let lon = body["longitude"] as? Double, lat.isFinite, lon.isFinite, abs(lat) <= 90, abs(lon) <= 180 else { reply(["error": "Choose a valid location."]); return }
            url = URLComponents(string: "https://api.open-meteo.com/v1/forecast")!
            url.queryItems = [URLQueryItem(name:"latitude",value:String((lat*100).rounded()/100)),URLQueryItem(name:"longitude",value:String((lon*100).rounded()/100)),URLQueryItem(name:"current",value:"temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"),URLQueryItem(name:"daily",value:"weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"),URLQueryItem(name:"timezone",value:"auto"),URLQueryItem(name:"forecast_days",value:"3"),URLQueryItem(name:"temperature_unit",value:"celsius"),URLQueryItem(name:"wind_speed_unit",value:"kmh")]
        }
        task?.cancel()
        var request = URLRequest(url: url.url!); request.timeoutInterval = 18; request.cachePolicy = .reloadIgnoringLocalCacheData
        task = session.dataTask(with: request) { data, response, error in
            var result: [String: Any]
            let status = (response as? HTTPURLResponse)?.statusCode
            if status == 429 { result = ["error":"Weather service is busy. Try again later."] }
            else if error != nil || status != 200 { result = ["error":"Could not load weather. Check your connection and try again."] }
            else if let data = data, data.count < 2_000_000, let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any], json["error"] as? Bool != true { result = json }
            else { result = ["error":"Weather data is unavailable. Try again later."] }
            DispatchQueue.main.async { reply(result) }
        }
        task?.resume()
    }
    private func finishLocation(_ result: [String: Any]) {
        timer?.invalidate(); timer = nil; manager.stopUpdatingLocation()
        let reply = locationReply; locationReply = nil; reply?(result)
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        guard locationReply != nil else { return }
        switch manager.authorizationStatus {
        case .authorized: manager.requestLocation()
        case .denied, .restricted: finishLocation(["error":"Location permission was not granted. Search for a city instead."])
        default: break
        }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last, location.horizontalAccuracy >= 0, abs(location.timestamp.timeIntervalSinceNow) < 300 else { finishLocation(["error":"A recent location is unavailable. Search for a city instead."]); return }
        finishLocation(["latitude": (location.coordinate.latitude*100).rounded()/100, "longitude": (location.coordinate.longitude*100).rounded()/100])
    }
    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) { finishLocation(["error":"Location unavailable. Check Location Services or search for a city."]) }
}
