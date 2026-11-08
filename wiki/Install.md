# Install

1. On your phone, open the [latest release](https://github.com/bornaradusin/CarTracker/releases/latest) and download the `.apk` file.
2. Open the downloaded file. If Android asks, allow your browser or file manager to install apps.
3. Tap **Install**, then **Open**.

The first time it opens, CarTracker asks to send notifications. Allow it, or alerts can't reach you.

## Updating

From 1.0.18, CarTracker updates itself. About once a day it looks on GitHub for a newer release and shows what's new, with **Update** and **Not now**:

- **Update** downloads the new APK, checks it against the release's `SHA256SUMS.txt`, and opens Android's installer. The first time, Android asks you to allow CarTracker to install apps: turn on **Allow from this source** and come back. It installs over the version you have and keeps your sign-in and settings.
- The downloaded APK is deleted as soon as the update is in.
- **Not now** stops that version being offered by itself. It's still under **More → Updates**, which also has **Check now** and the switch to stop looking.

Only published releases count, never drafts. You can still update by hand: download the newer `.apk` from [Releases](https://github.com/bornaradusin/CarTracker/releases) and install it the same way.

Versions up to 1.0.4 were unsigned test builds. If Android says the app can't be installed because it conflicts with one already there, uninstall CarTracker once, then install the new version. After that, every update installs over the last.

## Checking a download

Every release lists the APK's SHA-256 checksum in `SHA256SUMS.txt`, so you can check the file you downloaded is the one GitHub built.
