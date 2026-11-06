# Alerts

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/alerts.png" width="270" align="right" alt="Alerts">

The **Alerts** tab lists the last 7 days of alerts, newest first, grouped by day, with where each happened.

- **Filters**: All, Ignition, Places, Speed or Alarm.
- **Refresh**: checks the server now.
- **Clear**: empties the list and the badge. Traccar keeps its own record; the app just stops showing what you've cleared.

Tapping an alert notification opens this tab.

<br clear="right">

## Alerts on your phone

### Live alerts

With **More → Notifications → Live alerts** on, the app keeps a connection to your server open, so alerts arrive within seconds even when the app is closed. No Firebase or Google service is involved: alerts come straight from your own server.

Android requires a notification while that connection is open, called **Live connection**. To hide it, tap **Hide the connection notification** and turn that one notification type off. Alerts keep coming.

If your phone pauses apps to save battery, tap **Keep running in the background** so live alerts aren't held back.

With live alerts off, the app checks for new alerts every 15 minutes instead.

### Which alerts to get

**More → Notifications → Which alerts to get** turns each kind on or off:

| Alert | When |
|---|---|
| Ignition on / off | Every time the engine starts or stops |
| Arriving and leaving places | Home and any other place you add |
| Speeding | Over the limit you set under [Controls](Controls) |
| Tracker unplugged | Power to the tracker is cut |
| Vibration or movement | Bumps, knocks or towing while parked |
| Low car battery | The car's battery is running low |
| SOS and tampering | Panic button, tracker removed or tampered with |
| Other tracker alarms | Anything else the tracker raises |
| Tracker replies | The tracker confirming a command, like fuel cut or alarm on |
| Tracker offline | The tracker stops reporting (off at first) |
| Started and stopped moving | Every trip's start and end (off at first) |
