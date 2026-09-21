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
- **Background mileage tracking** — drives are detected and counted on their own, with
  the app closed. While tracking is armed, the app holds a low-power Activity Transition
  subscription (`LocationTrackingController`); Play services keeps it outside our process,
  so an `ENTER_IN_VEHICLE` transition cold-starts the app and launches
  `LocationTrackingService` (`DriveTransitionReceiver`). That foreground service runs
  `FusedLocationProviderClient` for the length of the drive, accumulates GPS distance,
  converts it to a mock donation (`miles * rate`, capped at the daily cap) and stores it in
  Room, keyed by date. An `EXIT_IN_VEHICLE` transition ends the drive and shuts GPS back
  down, so GPS is never running outside a drive. A periodic classification receiver
  (`ActivityRecognitionReceiver`) is kept as a backstop that can start a drive whose `ENTER`
  transition was missed — it deliberately can't end one, since periodic classifications
  report `STILL` at red lights. `BootCompletedReceiver` re-subscribes after a reboot or an
  app update, both of which clear the subscription.
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
4. Then set location access to **"Allow all the time"** (`ACCESS_BACKGROUND_LOCATION`).
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
- Drive detection depends on Android's activity classifier, which takes a moment to catch
  on and occasionally misses a transition entirely. In practice the first ~30–60 seconds of
  a drive may go uncounted, and a short trip can be missed. The periodic backstop narrows
  this but doesn't eliminate it.
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
