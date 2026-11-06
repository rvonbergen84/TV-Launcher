# TV-Launcher

A custom home screen for Google TV / Android TV. First target: Sony Bravia.

**Status: test build.** The goal right now is to find out whether this TV lets a third-party
app take over the remote's Home button. The UI is deliberately basic.

## What's in the test build
- App grid (TV banners where the app has one)
- Inputs row: HDMI inputs, using the names from the TV's settings
- TV settings shortcut
- Setup screen with a live log of what the TV reports when you press Home
- An accessibility service that tries two ways to own the Home button:
  - **Method A**: catch the Home key press itself
  - **Method B**: when the stock home screen appears, open ours on top of it (like Projectivy's "override")

## Install on the TV (no computer needed)
1. On the TV, install **Downloader** (by AFTVnews) from the Play Store.
2. In Downloader, enter this URL (the Google TV app on your phone has a keyboard for typing it):
   `https://raw.githubusercontent.com/rvonbergen84/tv-launcher/claude/home-button-spike/dist/tv-launcher.apk`
3. When the TV blocks the install, choose **Settings** on the prompt, turn on
   "Allow from this source" for Downloader, go back, and choose **Install**.
   (Menu paths differ by brand. On Sony it's usually Settings → Privacy → Security & restrictions,
   or Settings → Apps → Special app access → Install unknown apps. The list only shows
   apps that can install others, so Downloader has to be installed first.)
4. Open **TV Launcher** from your apps.

## Test
1. Open **Setup** → **Turn on the Home button service** → turn on "TV Launcher Home button".
2. Press Home on the remote.
3. Open Setup again and look at the log (or take a photo of the screen).

## Undo
Settings → Accessibility → turn off "TV Launcher Home button", or uninstall the app.
Nothing in this build changes the TV's own launcher.

## Install with adb instead
```
adb connect <tv-ip>:5555
adb install -r dist/tv-launcher.apk
# If the TV won't let you turn the service on in Settings:
adb shell settings put secure enabled_accessibility_services io.github.rvonbergen84.tvlauncher/.HomeButtonService
adb shell settings put secure accessibility_enabled 1
```
(The first `settings put` replaces any other accessibility services you have turned on.)

## Build
Android Studio, or `./gradlew assembleRelease` with the Android SDK installed (API 35).
The signing key in `keystore/` is shared on purpose so any build can update the installed app.
It is public, so treat builds as personal test builds only.
