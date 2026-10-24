# KalaClock — Plan

Native Android flip clock app, OLED-aware. Android first.

## Name

**KalaClock**. "Kala" means "time" in Sanskrit/Indonesian; "Clock" makes the purpose obvious in the store. Package: `id.tera.kalaclock`.

## OLED strategy (core feature)

A clock left on a desk for hours is the worst case for OLED burn-in, so this is built in from the start:

- Background is pure `#000000` so pixels are off (also saves battery).
- Two card styles: filled card (`#111`, as in the reference) or card-less (digits only on black). Card-less is the safest for OLED.
- Pixel shift: all content drifts a few px every 1-2 minutes, slowly, so it is not noticeable.
- Auto dim: optional low brightness via `window.attributes.screenBrightness`; digits and artwork are dimmed too.

## Stack

- Kotlin + Jetpack Compose, single module, simple MVVM.
- `minSdk 26`, `compileSdk 36` (installed locally). Re-check AGP / Kotlin / Compose BOM versions at scaffold time; research results were inconsistent.
- DataStore for settings. No heavy libraries.
- Local env already has: Android SDK 36, build-tools 36, JDK 17, Android Studio, an API 35 emulator.

## Features

1. **Flip clock UI**
   - Flip animation with `graphicsLayer { rotationX }` + `cameraDistance`; two clipped half-cards with a center line.
   - 12/24h, AM/PM, date, weekday, battery.
   - Fullscreen immersive, keep screen on (`FLAG_KEEP_SCREEN_ON`).
2. **Portrait / landscape**
   - Handle `configChanges` to avoid activity recreation and animation breaks.
   - Landscape: cards side by side. Portrait: cards stacked.
   - Card size computed from `BoxWithConstraints`.
3. **Modes: Clock, Stopwatch, Countdown, Pomodoro**
   - Assumption: "timer" = stopwatch (count up), "countdown" = count down from a duration. Confirm.
   - Pomodoro: focus 25 / break 5 / long break 15 after 4 sessions; configurable.
   - Time derived from `elapsedRealtime` timestamps, not tick counting, to avoid drift.
   - Background: foreground service + notification. Finish alarm via `AlarmManager.setExactAndAllowWhileIdle` with `USE_EXACT_ALARM` (permitted for timer/alarm apps).
4. **Now playing (artist, title, artwork)**
   - `NotificationListenerService` + `MediaSessionManager.getActiveSessions()`; read `MediaMetadata` (artist, title, art bitmap) and `PlaybackState`.
   - User must grant Notification access once; onboarding screen explains why and deep-links to the settings page.
   - Artwork small, grayscale/dimmed, included in pixel shift. Fall back to text only when no art.
5. **Settings:** card style, brightness, pixel shift, 12/24h, pomodoro durations, media display.

## Structure

`ui/` (flip, clock, timer, pomodoro, settings), `domain/` (timer engine), `media/` (listener service), `service/` (foreground timer, alarm), `data/` (DataStore).

## Milestones

1. Scaffold project, black theme, fullscreen, keep-awake.
2. FlipCard + clock, portrait/landscape.
3. Pixel shift + OLED dim.
4. Timer engine (stopwatch, countdown, pomodoro), then service + alarm.
5. Media listener + now playing UI.
6. Settings, permission onboarding, polish, test on emulator and a real device.

## Risks

- Some vendors (Xiaomi, Oppo) kill background services; needs battery-whitelist guidance.
- Pixel shift needs tuning so it is not distracting.
- Artwork availability depends on the player; some do not send a bitmap.

## Open questions

- Confirm timer = stopwatch + countdown.
