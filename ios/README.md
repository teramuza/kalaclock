# KalaClock iOS

SwiftUI port of the Android app (see `../PLAN.md`). iOS 16+.

- Open `KalaClock.xcodeproj` in Xcode and run the `KalaClock` scheme.
- The project is generated from `project.yml` with [XcodeGen](https://github.com/yonaskolb/XcodeGen). After changing it, run `xcodegen generate`.
- Tests: `KalaClockTests` (timer engine), run with Cmd+U.

## Differences from Android

- Timer finish alert is a local notification (iOS has no foreground service); time is still derived from monotonic timestamps.
- Now playing reads the Apple Music track only. iOS does not let apps read other players (Spotify, YouTube Music).
- Brightness is system-wide, so auto dim restores the original brightness when the app leaves the foreground.
- There is no system back button, so back-to-clock is not ported.
