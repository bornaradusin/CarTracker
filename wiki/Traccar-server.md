# Your Traccar server

CarTracker works with any [Traccar](https://www.traccar.org) server, version 5 or newer. Everything it shows and does comes from the server through Traccar's own API, so the server stays the one place your data lives.

## Reaching it from your phone

The app needs the server's web address, the one you open Traccar's web page with, over HTTPS. If your server only has plain http, put it behind a reverse proxy with a certificate (Nginx Proxy Manager, Caddy, Traefik or similar). Plain http is accepted only on your home network: see [Sign in](Sign-in#plain-http).

## Accounts

Each person signs in with their own Traccar account and sees only the cars linked to it.

**To give someone access to a car**, as an admin in Traccar's web page:

1. **Settings → Users** → the person's account → the link icon.
2. Under **Devices**, tick the car.

An account with no cars linked signs in fine but shows "No cars yet".

### Everyday accounts with limited commands

Setting **Limit commands** on an account in Traccar lets it send only the server's **saved commands**. That keeps commands the tracker doesn't support out of sight.

For CarTracker's buttons (fuel, alarm, alerts and so on) to work on such an account, each saved command has to be linked to **both the account and the car**, under the link icon's **Saved commands**. The app finds the saved command that matches each button and sends that one.

## What the app changes on the server

| In the app | On the server |
|---|---|
| Renaming the car | The device's name |
| Choosing a vehicle | The device's category |
| Speeding limit | The device's `speedLimit` attribute, used by Traccar's overspeed alerts |
| Places | Geofences, linked to the car, with their icon in the `icon` attribute |
| Commands | Sent through Traccar, like its web page does |
| Odometer | Traccar's total distance for the car, set from **More → Your car → Odometer** |
| Live links | A temporary, read-only account for the car, which Traccar removes after the link ends |

Everything else (car and glow colours, theme, which alerts you get) is kept on your phone.

## Alerts and events

Traccar records events (ignition, places, overspeed, alarms) for every car whether or not any notification is set up in Traccar. CarTracker reads those events, so you don't need to set up Traccar notifications for the app.

## Live links

**Share → live link** uses Traccar's own device sharing. The account that makes the link must not be a temporary one, and sharing must be allowed on the server: in Traccar's web page, **Settings → Server**, leave **Disable share** off.

People who open the link can't send commands or see reports, unless the server's config turns `web.shareDevice.commands` or `web.shareDevice.reports` on.

## Road speed limits

Traccar can look up the speed limit of the road the car is on, from OpenStreetMap. Speeding alerts then use each road's limit instead of one number for everywhere, and the app shows the limit next to the speed. Where a road has no limit in OpenStreetMap, the limit you set in [Controls](Controls) still applies.

Add these to Traccar's settings. In Docker, as environment variables:

```yaml
      SPEED_LIMIT_ENABLE: "true"
      SPEED_LIMIT_URL: "https://overpass-api.de/api/interpreter"
      SPEED_LIMIT_ACCURACY: "40"                     # metres around the car to look for the road
      EVENT_OVERSPEED_THRESHOLD_MULTIPLIER: "1.1"    # alert only when 10% over the limit
      EVENT_OVERSPEED_MINIMAL_DURATION: "10"         # ...for at least 10 seconds
```

Or in `traccar.xml`:

```xml
<entry key='speedLimit.enable'>true</entry>
<entry key='speedLimit.url'>https://overpass-api.de/api/interpreter</entry>
<entry key='speedLimit.accuracy'>40</entry>
<entry key='event.overspeed.thresholdMultiplier'>1.1</entry>
<entry key='event.overspeed.minimalDuration'>10</entry>
```

The public Overpass server is free for light use, which one or a few cars are. For a fleet, run your own Overpass server and point `SPEED_LIMIT_URL` at it.
