# Implementation Plan - Switch Weather Provider to YR.no (MET Norway)

Upgrade the weather fetching logic to use the MET Norway Weather API (YR.no) for better accuracy and real-time data.

## Proposed Changes

### Weather Data Layer

#### [WeatherResponse.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/utils/weather/WeatherResponse.kt)
- Update data classes to match MET Norway's GeoJSON structure.
- Add `MetNorwayResponse`, `MetProperties`, `Timeseries`, `MetData`, `Instant`, and `NextHours` classes.
- Keep or adapt the `Current` class as a convenience mapper for the UI.

#### [WeatherManager.kt](file:///C:/AndroidApps/VoiceTally/app/src/main/java/com/yvesds/vt5/utils/weather/WeatherManager.kt)
- Update `fetchCurrent` to:
    - Call `https://api.met.no/weatherapi/locationforecast/2.0/complete`. (Using `complete` to ensure we get `visibility` if available, otherwise `compact`).
    - Add mandatory `User-Agent` header.
    - Truncate lat/lon to 4 decimal places.
    - Parse the first entry in `timeseries`.
- Refine mapping logic for:
    - **Visibility**: Map from `visibility` (if in `complete` API) or handle its absence.
    - **Cloud Cover**: Map `cloud_area_fraction` (percentage) to octants.
    - **Precipitation**: Use `next_1_hours.details.precipitation_amount`.

---

## Verification Plan

### Manual Verification
- **Fetch Weather**: In the metadata screen, tap "Auto" and verify that weather data is populated.
- **Accuracy Check**: Compare the results with the official YR.no website/app for the same location.
- **Log Inspection**: Check logs for the User-Agent header and the response status (should be 200 OK).
- **Error Handling**: Temporarily change the User-Agent to a generic one and verify the app handles the error gracefully (e.g., showing a toast instead of crashing).

### Automated Tests
- I will check for existing unit tests in `com.yvesds.vt5.utils.weather`. If they exist, I will update them to test the new parsing logic with a mock JSON response.
