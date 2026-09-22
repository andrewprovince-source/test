# Drive for Change (v1 Skeleton / MVP)

A native Android (Kotlin + Jetpack Compose) prototype that turns everyday driving into a
tracked, mock charitable donation total. Built from the app spec in this repo — no real
payment processing, no backend. See the spec at the top of this repo's task/issue for full
product details.

## What's implemented

- **Onboarding** — 3-page swipeable intro ending in "Get Started" (`ui/onboarding`)
- **Sign up** — email + password, stored locally, no backend (`ui/auth`)
- **Preferences setup** — per-mile rate slider ($0.01–$0.50, presets $0.05/$0.10/$0.25),
  daily cap slider ($1–$20, presets $5/$10/$15), single-select charity list with a demo
  disclaimer (`ui/preferences`)
- **Home dashboard** — today's donation (large), today's miles, lifetime total with
  this-month/this-year breakdown, current charity tag, pause/resume tracking toggle
  (`ui/dashboard`)
- **Settings** — change charity, rate, or cap at any time (`ui/settings`)
- **Background mileage tracking** — drives are detected and counted with the app closed.
  While armed, `LocationTrackingController` holds two low-power subscriptions that Play
  services keeps outside our process, either of which can cold-start the app and launch
  `LocationTrackingService` (both are allowed to start a location foreground service from
  the background):
  - an Activity Transition subscription for `IN_VEHICLE` enter/exit (`DriveTransitionReceiver`)
  - a 200 m geofence around where the last drive ended (`ParkingGeofenceReceiver`) — the
    dependable trigger, since the classifier's transitions are slow and sometimes never fire.

  The geofence can't tell driving away from walking away, so the service can start
  unconfirmed. Distance traveled before the classifier confirms `IN_VEHICLE` is held as
  *pending*: credited in full once a drive is confirmed, discarded if it isn't within 5
  minutes. Periodic classifications (`ActivityRecognitionReceiver`) confirm drives and end
  ones whose `EXIT` transition never arrived (a confident on-foot reading; never `STILL`,
  which is what red lights look like). GPS only runs during a possible drive; when it stops,
  the geofence is re-planted where the drive ended. `BootCompletedReceiver` re-arms after a
  reboot or app update. Credited miles become a mock donation (`miles * rate`, capped at the
  daily cap) stored in Room, keyed by date.
- **Tracking log** — every step of drive detection is written to an on-device log
  (`TrackingLog`, no coordinates), viewable and copyable from "View tracking log" on the
  dashboard. Background failures are otherwise invisible, so this is the first thing to
  check when a drive isn't counted.
- **Mock ledger** — Room database (`daily_donations` table, one row per day) is the local
  "ledger"; DataStore Preferences holds account/settings state. No network calls anywhere.

## Project structure

```
app/src/main/java/com/driveforchange/app/
  data/            UserPreferencesRepository (DataStore), Room entities/DAO/DB,
                   DonationRepository (business logic), Charity list, AppContainer (manual DI)
  location/        LocationTrackingController (arms/disarms drive detection),
                   LocationTrackingService (per-drive foreground service), and the
                   drive-transition / periodic-classification / boot receivers
  ui/              Compose screens per flow step, theme, navigation graph
  viewmodel/       One ViewModel per screen + a small factory
```

## Building

This was developed without a local Android SDK available in the build sandbox, so it has
**not been compiled or run** in this environment — treat it as ready-to-open, not
build-verified. To build it yourself:

1. Open the repo root in Android Studio (Koala/2024.1+ recommended) and let it sync, **or**
   run from the command line with an Android SDK installed and `ANDROID_HOME`/
   `local.properties` (`sdk.dir=...`) set:
   ```
   ./gradlew assembleDebug
   ```
2. Run on a device/emulator with Google Play services (needed for
   `play-services-location`).
3. Grant location, physical activity (Android 10+), and notification (Android 13+)
   permission when prompted on the dashboard — mileage tracking won't count anything
   without location, and won't detect drives at all without physical activity permission
   (see below).
4. Then follow the dashboard's setup cards: location **"Allow all the time"**, and
   exempting the app from battery optimization (Samsung and other OEMs otherwise put it to
   sleep and it never hears that a drive started). Set location access to **"Allow all the time"** (`ACCESS_BACKGROUND_LOCATION`).
   Android requires this to be granted separately from the first prompt, and on Android
   11+ only from the app's system settings page, so the dashboard shows a card that links
   there. Without it, a drive detected while the app is closed starts the service but
   Android hands it no locations — i.e. tracking silently falls back to app-open-only.
   Note that shipping this on Google Play would require the background location
   declaration form; it is not needed for local builds.

Minimum SDK 26, target/compile SDK 34. Kotlin 1.9.24, Compose BOM 2024.06.00, AGP 8.5.2.

## Known simplifications (intentional, prototype scope)

- No real payment processing — donation totals are a local mock ledger only.
- No Google Sign-In — email/password only, stored on-device, not validated against any
  server.
- The parking geofence is first planted wherever the phone is when tracking arms (opening
  the app, or a reboot), then re-planted where each drive ends. Android delivers background
  geofence exits with a delay (often 1–3 minutes), and the miles covered before tracking
  starts can't be recovered.
- Walking more than 200 m from where the car is parked triggers up to 5 minutes of GPS
  before it's ruled out as a drive (sooner if the classifier reports on-foot).
- If an `EXIT_IN_VEHICLE` transition is missed, an idle watchdog in the service stops GPS
  after 10 minutes without recorded movement rather than letting it run for the rest of the
  day. A long traffic jam could therefore end a drive early.
- Force-stopping the app clears the transition subscription and Android sends no broadcast
  for it, so tracking stays off until the app is opened once. Reboots and app updates are
  handled; force-stop can't be.
- Aggressive OEM battery managers (Xiaomi, Huawei, OnePlus, Samsung's "deep sleep") can
  drop the subscription too. Shipping this for real would want an "exclude from battery
  optimization" prompt, which isn't implemented.
- Location updates use a 4-second interval / 5-meter minimum distance as a simple,
  battery-reasonable default — not tuned for accuracy on foot vs. highway speeds. GPS
  jumps implying a driving speed over ~120 mph are discarded rather than counted, to
  guard against GPS glitches and cold-start/emulator location artifacts.
- Today's donation is always recomputed from today's *total* cumulative miles at the
  *current* rate and cap — so changing the rate or cap mid-day re-prices the whole day's
  driving so far, not just miles from that point forward. Saving a lower cap also
  immediately re-clamps today's already-stored total, rather than waiting for the next
  GPS update to apply it.
- If physical activity permission is denied, no drive is ever detected and no distance is
  ever counted (fails closed rather than falling back to tracking all movement), and the
  dashboard shows a hint explaining why.
