# Alerts

<p>
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/alerts.png" width="260" alt="Alerts">
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/alerts-dark.png" width="260" alt="Alerts, dark">
</p>

The **Alerts** tab lists the last 7 days of alerts, newest first, grouped by day, with where each happened. It shows the same alert types you get notifications for: switch one off under [Which alerts to get](#which-alerts-to-get) and it leaves the list and the badge too.

- **Filters**: All, Ignition, Places, Speed or Alarm.
- **Refresh**: checks the server now.
- **Clear**: empties the list and the badge. Traccar keeps its own record; the app just stops showing what you've cleared.

Tapping an alert notification opens this tab.


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
| Tracker replies | The tracker confirming a command, in plain words: **Alarm turned on**, **Fuel cut**, **Fuel restored** |
| Tracker offline and back online | The tracker stops reporting, and when it's back (off at first) |
| Started and stopped moving | Every trip's start and end (off at first) |

The app's own **low battery warning** is set separately, per car, under [Battery](Battery#low-battery-warning).

Tap any alert for its details: the full text (for example the tracker's whole answer to **Locate now** or **Check status**), when and where it happened, **Copy**, and **Open in Maps**.

## Commands you sent

Every command sent from this phone (fuel, alarm, Locate now, settings, custom commands, and the quick settings tile) shows in **Alerts** with what came of it:

- **Sent · waiting for the car to answer**, with how many times it was sent
- **Answered**, with the tracker's answer in plain words
- **Done · the car's report shows it**, when the tracker didn't answer but its next report shows the change
- **No answer**, **Didn't work** or **Not sent**, with **Send again** in its details

The tracker's answer shows with the command, not as a separate alert. Tap **Commands** at the top to see only these. They're kept on the phone for a week.
