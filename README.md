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
- **Mileage tracking** — a foreground `Service` using `FusedLocationProviderClient`
  accumulates GPS distance while tracking is active, converts it to a mock donation
  (`miles * rate`, capped at the daily cap) and stores it in Room, keyed by date
  (`location/`, `data/`)
- **Mock ledger** — Room database (`daily_donations` table, one row per day) is the local
  "ledger"; DataStore Preferences holds account/settings state. No network calls anywhere.

## Project structure

```
app/src/main/java/com/driveforchange/app/
  data/            UserPreferencesRepository (DataStore), Room entities/DAO/DB,
                   DonationRepository (business logic), Charity list, AppContainer (manual DI)
  location/        LocationTrackingService (foreground service) + controller
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
3. Grant location (and, on Android 13+, notification) permission when prompted on the
   dashboard — mileage tracking won't start without it.

Minimum SDK 26, target/compile SDK 34. Kotlin 1.9.24, Compose BOM 2024.06.00, AGP 8.5.2.

## Known simplifications (intentional, prototype scope)

- No real payment processing — donation totals are a local mock ledger only.
- No Google Sign-In — email/password only, stored on-device, not validated against any
  server.
- Tracking does not resume automatically after a device reboot (no boot receiver) — the
  user just needs to reopen the app once.
- Location updates use a 4-second interval / 5-meter minimum distance as a simple,
  battery-reasonable default — not tuned for accuracy on foot vs. highway speeds. GPS
  jumps implying a driving speed over ~120 mph are discarded rather than counted, to
  guard against GPS glitches and cold-start/emulator location artifacts.
- Today's donation is always recomputed from today's *total* cumulative miles at the
  *current* rate and cap — so changing the rate or cap mid-day re-prices the whole day's
  driving so far, not just miles from that point forward. Saving a lower cap also
  immediately re-clamps today's already-stored total, rather than waiting for the next
  GPS update to apply it.
