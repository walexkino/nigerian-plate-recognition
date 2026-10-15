# Optional local weather — 0.4.0

Open Weather on macOS or Local weather beside the Android appearance control. Use my location requests a single approximate location; city search is available when location is denied, disabled, or unavailable. Search returns up to five named places, requiring a selection instead of assuming the first match. Refresh uses the selected area; it does not continuously track a moving device.

Current modeled conditions include temperature, feels-like, humidity and wind. The three-day forecast includes conditions, high/low temperatures and precipitation probability. Temperature is Celsius, wind is km/h. Forecast dates and time use the provider's local timezone; missing values display an em dash. Last results stay visible during a failed refresh with an explicit previous-forecast message. Weather is informational and is not used to infer a plate's accuracy.

## Data flow and provider

- Native macOS uses Core Location; Android requests `ACCESS_COARSE_LOCATION` only. No background-location permission is requested. The browser preview uses its geolocation API when available.
- Coordinates are rounded to two decimal places before HTTPS requests to `api.open-meteo.com`. Searches go to `geocoding-api.open-meteo.com`. The provider also receives normal connection information, including IP address.
- Requests are bounded and cancelable. Closing the panel cancels pending work; Android also cancels when leaving the foreground. Late responses cannot replace a closed or newer weather screen.
- The weather feature keeps selection and responses in memory only. It does not write a location preference, image, plate reading, or report to the provider or append weather to JSON recognition reports.
- Weather needs internet. Existing OCR remains on device and works without weather or location permission.

Weather data is attributed to [Open-Meteo](https://open-meteo.com/) under [CC BY 4.0](https://open-meteo.com/en/licence); location lookup uses GeoNames through Open-Meteo. The no-key API is for noncommercial use and has rate limits. Review [provider terms](https://open-meteo.com/en/terms) before commercial distribution or scaling; no paid account has been created.

## Verification recorded on 15 October 2026

- 40 Node tests passed, including denied location, late-response cancellation, explicit city selection, rounded coordinates, null values and invalid responses.
- 13 Android unit tests and lint passed with no errors. Ten Android 15 emulator tests passed, covering weather rendering in both themes and no permission request on opening, as well as existing onboarding, appearance, OCR, review/persistence, photo and camera workflows.
- Native WebKit weather layout checks passed at 320 px in both themes, 768 px dark, and 1440 px light, with no horizontal overflow and the expected forecast, timezone and missing-data display.
- A native macOS integration check successfully called real city search and a three-day forecast using public Lagos coordinates. It did not request the user's device location.
- Both local packages were rebuilt as 0.4.0. Device-location availability depends on the user's Location Services and permission settings. Real GPS acquisition on a physical phone has not been verified.

Routine checks:

```sh
node --test apps/macos/tests/*.test.js
python3 apps/android/scripts/build.py --check
# With the Android SDK/JDK configured, from apps/android/:
./gradlew connectedDebugAndroidTest
```

Optional macOS native checks, from the repository root with a compatible Swift SDK:

```sh
mkdir -p build/weather-qa outputs/weather
swiftc apps/macos/tests/weather_webkit.swift -o build/weather-qa/weather-webkit
build/weather-qa/weather-webkit apps/macos/ui outputs/weather
swiftc apps/macos/native/Weather.swift apps/macos/tests/weather-native/main.swift -o build/weather-qa/weather-native
build/weather-qa/weather-native
```

The WebKit check uses an invented fixture, not real weather or private photographs. Screenshots and runtime outputs remain in ignored local folders.

## Street suggestions — 0.5.0

Typing three or more characters starts a search after a 650 ms pause. Search also accepts an explicit submission. Photon, a search-as-you-type geocoder using OpenStreetMap data, supplies streets, addresses, and cities. The current session's approximate selection can bias matches nearby; adding a city is useful where street names repeat. Selecting a result fetches weather for its rounded coordinates. Search remains editable while suggestions load. Older responses and pending timers are canceled or ignored when the text changes or the panel closes. Up to 20 query responses are cached in memory only.

Search text and any selected approximate-area bias are sent to `photon.komoot.io`; weather requests go to Open-Meteo. Providers receive normal connection information, including IP addresses. Attribution: [Photon](https://github.com/komoot/photon) and [OpenStreetMap contributors](https://www.openstreetmap.org/copyright). The public demo API allows reasonable use but can throttle requests and provides no availability guarantee. It may not cover every street. The public Nominatim API is not used for autocomplete.
