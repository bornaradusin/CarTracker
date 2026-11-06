# Troubleshooting

### Signed in, but "No cars yet"

The account has no cars linked. An admin links them in Traccar under **Settings → Users** → the account → link icon → **Devices**. Then sign out and back in. See [Your Traccar server](Traccar-server#accounts).

### "Your account isn't allowed to send that"

The account has **Limit commands** set and the saved command for that button isn't linked to it, or isn't linked to the car. See [Everyday accounts with limited commands](Traccar-server#everyday-accounts-with-limited-commands).

### Settings stay on "Waiting for … to confirm…" and then nothing changes

The tracker's answer isn't reaching Traccar. On MiCODUS trackers, use the Traccar image from [MiCODUS MV710G, step 4](MiCODUS-MV710G#4-traccar-with-mv710g-replies). To check, send a command and look in Traccar under **Reports → Events** for a "Command result".

### Times are hours out

The server is reading the tracker's time in the wrong time zone. For MiCODUS trackers, set `DECODER_TIMEZONE: "UTC"` ([step 3](MiCODUS-MV710G#3-use-the-right-time-zone)).

### Alerts come late

- Turn on **More → Notifications → Live alerts**. Without it, the app checks every 15 minutes.
- Tap **Keep running in the background**, so the phone doesn't pause the app to save battery.
- Check the alert type is on under **Which alerts to get**.

### The car shows Offline

The tracker hasn't reported for a while. It may be out of mobile coverage, in an underground car park, or switched off. It reports again once it's back in coverage.

### "Finding the address…" doesn't go away

Street names come from your phone's own address lookup, which needs an internet connection. If the server itself works out addresses (Traccar's geocoder), the app uses those instead.

### An update won't install

Versions up to 1.0.4 were unsigned test builds. Uninstall CarTracker once and install the new version. Every signed update after that installs over the last. See [Install](Install#updating).

### The map is blank

The maps load from the internet. Check your connection, or try another style from the layers button.
