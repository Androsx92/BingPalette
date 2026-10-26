# Bing Palette

Sets today's Bing image of the day as your wallpaper and then makes Samsung One UI update its **Colour palette** to match it, automatically, once a day.

## Why this exists

I used to have a Google Pixel. Every morning my phone set the daily Bing wallpaper and Android's Material You picked new system colours from it, with no effort on my part. I liked that a lot.

Then I switched to a Samsung foldable (Galaxy Z Fold). One UI does not refresh the colour palette by itself when a wallpaper is set by another app. After every wallpaper change I had to open Settings > Wallpaper and style > Colour palette, switch it off, press Apply, switch it on and press Apply again. Every single morning, on two screens. I wanted the Pixel behaviour back, so I built this app.

## An honest note: this is vibe coded

**I cannot code.** This app was written entirely by an AI (Claude, by Anthropic) from my descriptions, screen recordings and screenshots of what went wrong on my phone. I test it on my own device (Galaxy Z Fold, One UI, English) and report back until it works. I do not fully understand the code myself.

What that means for you:
- It works on my phone. I cannot promise it works on yours, on other One UI versions, or after a Samsung update changes the Settings screens.
- Please read the code before you trust it, especially since it uses an accessibility service. It is small and the whole thing is in `app/src/main/java/app/bingpalette/`.
- Bug reports and pull requests are very welcome. If you can code, please help me improve it.

## What it does

- Downloads the Bing image of the day (choose your country/language) and sets it as home screen, lock screen or both.
- Opens Settings > Wallpaper and style > Colour palette, switches the palette off, presses Apply, switches it on, presses Apply again.
- Runs once a day after a time you choose (on the first unlock), or at any time with the **Run** button.
- Foldable aware: update the inner screen, the cover screen or both.
- Optional full-screen cover with today's image while the palette is updated, with a Cancel button.
- Speed setting (Safe, Fast, Ultra fast), palette on/off, and when the palette step runs (daily automatic run, Run button, or both).
- Material You interface with a preview of today's image and its dominant colours.

## Privacy and the accessibility service

The colour palette has no public Android API, so the app uses an **accessibility service** to tap through Samsung's Settings screens like a finger would. It only acts while Settings is in front, reads nothing else, stores nothing and sends nothing anywhere. The only network access is downloading the Bing image and its description from Microsoft's servers. There are no ads, no analytics, no accounts.

## Requirements

- Android 10 or newer. The wallpaper part works on any phone.
- The palette step is made for **Samsung One UI in English** (it looks for the labels "Colour palette" and "Apply"). Other languages need the label lists at the top of `PaletteService.kt` edited.

## Build the APK (no Android Studio needed)

1. Fork or upload this project to your own GitHub repo (keep `.github/`).
2. Open the repo > Actions > "Build APK" > Run workflow (it also runs on every push).
3. When it finishes, download the artifact `BingPalette-apk`, unzip it and install `app-debug.apk`.

## Set up on the phone

1. Settings > Apps > Bing Palette > three dots > **Allow restricted settings** (needed for sideloaded apps before an accessibility service can be turned on).
2. Open the app > Enable > Bing Palette > turn the service on.
3. Settings > Battery > Background usage limits: add Bing Palette to "Never auto sleeping apps".
4. Tap **Run** and watch it work.

## Notes

- If One UI changes the layout of the palette page, the palette step may time out. The wallpaper is still set.
- Bing images belong to their owners. The app only sets them as your personal wallpaper and does not store or share them.
- Not affiliated with Microsoft or Samsung.

## Licence

GPL-3.0-only, see `LICENSE`. F-Droid notes are in `docs/FDROID_CHECKLIST.md`.
