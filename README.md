# Bing Palette

Sets today's Bing wallpaper (home + lock) and then taps Samsung's
Wallpaper and style > Colour palette > Apply, automatically on the first
unlock of each day.

## Build the APK (no Android Studio needed)
1. Create a new GitHub repo and upload this whole folder (keep `.github/`).
2. Open the repo > Actions > "Build APK" > Run workflow (it also runs on every push).
3. When it finishes, download the artifact `BingPalette-apk`, unzip, install `app-debug.apk`.

## Set up on the phone
1. Settings > Apps > Bing Palette > three dots > **Allow restricted settings**
   (needed for sideloaded apps before an accessibility service can be turned on).
2. Open the app > "Enable accessibility service" > Bing Palette > On.
3. Settings > Battery > Background usage limits: add Bing Palette to "Never auto sleeping apps".
4. Tap "Run now (test)" and watch it work.

## Settings
Open Settings in the app: start time dial, image country, home/lock target, Colour palette (on/off and when: daily auto run, Run now, or both), and which fold screens to update. "Wallpaper only" on Home skips the palette step.

## Notes
- Labels are matched in English ("Colour palette", "Apply", "Wallpaper and style").
  If you change the phone language, edit the lists at the top of `PaletteService.kt`.
- If One UI changes the layout, the palette step may time out; the wallpaper is still set.

## Licence
GPL-3.0-only, see `LICENSE`. Not affiliated with Microsoft or Samsung.

## F-Droid
See `docs/FDROID_CHECKLIST.md`.
