import Cocoa

// Exercises real HTTPS search/forecast on public city coordinates, never device location.
let weather=WeatherService()
DispatchQueue.main.asyncAfter(deadline:.now()+45) { print("Weather API timed out");exit(2) }
weather.handle("weatherSearch",["query":"Lagos"]) { result in
    guard let cities=result["features"] as? [[String:Any]], !cities.isEmpty else { print("City search failed: \(result)");exit(1) }
    weather.handle("weatherForecast",["latitude":6.52,"longitude":3.38]) { result in
        guard let current=result["current"] as? [String:Any], current["temperature_2m"] is Double, let daily=result["daily"] as? [String:Any], let dates=daily["time"] as? [String], dates.count == 3 else { print("Forecast failed: \(result)");exit(1) }
        print("PASS native HTTPS city search and three-day forecast; no device location requested");exit(0)
    }
}
RunLoop.main.run()
