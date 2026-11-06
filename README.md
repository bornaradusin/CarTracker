# CarTracker

An Android app for your own [Traccar](https://www.traccar.org) server. See where your car is, get alerts the moment something happens, look back at trips and control the tracker, all without Google keys, Firebase or a third-party cloud.

## Features

- **Live map** of your cars with their address, speed, ignition and how long they have been parked
- **Map styles**: Standard, Clean, Black & white and Terrain, following the phone's light or dark theme
- **Google Maps shortcuts** for live traffic, Street View and directions, with no API key
- **Alerts** for ignition, arriving at and leaving places, speeding, the tracker being unplugged, vibration, low battery, SOS and more
- **Live alerts** over the server's own WebSocket, so they arrive within seconds even when the app is closed. The "Live connection" notification can be hidden in Android's settings; turning live alerts off checks every 15 minutes instead
- **Trips** for the last 7 days with distance, time and a route sketch, plus a route replay at 1×, 4× or 16×
- **Controls**: immobiliser (shown in orange while the car can't start), alarm arming, auto-arm, tracker alerts, speed limit, locate now, update rate and restart
- **Advanced commands** (off by default): every command Traccar offers for your tracker, the server's saved commands and a custom command box
- **Places**: add a place where the car is now and get alerts when it arrives or leaves
- **Your look**: car icon, icon colour and shape, accent colour, and light, dark or follow-phone theme

MiCODUS trackers (MV710G and other JT808 models) get their own command set. Other trackers use Traccar's standard commands.

## Requirements

- Android 8.0 or newer
- A Traccar server (version 5.0 or newer; 6.x recommended) reachable over HTTPS. Plain HTTP is accepted only for home-network addresses

## Install

Download the latest APK from [Releases](https://github.com/bornaradusin/cartracker/releases), open it on your phone and allow installs from your browser or file manager if Android asks.

Sign in with your server's address and your Traccar email and password. The app keeps only a sign-in token, never your password.

## Privacy

CarTracker talks only to your Traccar server, to [OpenFreeMap](https://openfreemap.org) and [OpenTopoMap](https://opentopomap.org) for map tiles, and to Android's built-in geocoder for street names. There is no analytics, no tracking and no account with anyone but your own server.

## Building

GitHub Actions builds every push to `main` and attaches the APK to a draft release. To build locally:

```
./gradlew assembleDebug
```

Release builds are signed when these repository secrets are set:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The keystore file, base64 encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias (defaults to `cartracker`) |
| `ANDROID_KEY_PASSWORD` | Key password |

## License

[GPL-3.0](LICENSE). Made by [bornaradusin](https://github.com/bornaradusin).
