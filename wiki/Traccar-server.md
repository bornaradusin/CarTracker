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

Everything else (car and glow colours, theme, which alerts you get) is kept on your phone.

## Alerts and events

Traccar records events (ignition, places, overspeed, alarms) for every car whether or not any notification is set up in Traccar. CarTracker reads those events, so you don't need to set up Traccar notifications for the app.
