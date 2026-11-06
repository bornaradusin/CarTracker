# Controls

Tap **All controls** on the car card.

<p>
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/controls.png" width="260" alt="All controls">
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/controls-dark.png" width="260" alt="All controls, dark">
</p>


## Fuel cut (immobiliser)

The tile shows **Fuel on**, or **Fuel cut** in orange while the car can't start.

- **Cut fuel now**: the engine stops being able to run straight away. Only use it when the car is parked.
- **Cut fuel safely**: waits until the car is below 20 km/h. Only shown when the tracker supports it.
- **Restore fuel**: the car can start again.

**Ask for fingerprint** (on at first): cutting or restoring fuel asks for your fingerprint, face or the phone's PIN first, so it can't happen by accident or by someone else picking up your phone. Turn it off in the same card. Phones with no screen lock skip it.

## Alarm

- **Alarm**: armed, the tracker alerts you if the car is moved or shaken.
- **Arm automatically** (MiCODUS): arms the alarm a minute after the ignition goes off.

## Tracker alerts

These are set on the tracker itself:

- **Vibration or movement**: bumps, knocks or towing while parked
- **Low car battery**: below 11.5 V on MiCODUS trackers
- **Tracker unplugged**: power to the tracker is cut

**Speeding** is worked out by your Traccar server, not the tracker. Turn it on, then set the limit with **−** and **+** under **Alert above**, in steps of 10 km/h from 30 to 200.

<br clear="right">

## Waiting for the tracker

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/controls-more.png" width="270" align="right" alt="Tracker and advanced commands">

Settings stored on the tracker (alarm, arm automatically, and the three tracker alerts) only change in the app once the tracker confirms them. Until then, the switch says **Waiting for … to confirm…**. If the tracker doesn't answer within 90 seconds, nothing changes and the app tells you.

If the car is offline, the app won't send these, because the setting couldn't be confirmed.

## Tracker

- **Locate now**: asks the tracker for a fresh position.
- **Update rate**: how often the tracker reports. Choose from every 5 s driving / 1 min parked, every 10 s / 3 min, every 30 s / 5 min, or every 1 min / 10 min. Faster rates use more mobile data.
- **Restart tracker**: restarts it. It's back online within a minute or two.

## Advanced commands

Turn on **More → Commands → Advanced commands** to add this section. It has:

- every command Traccar offers for your tracker
- the commands saved on your server
- a **Custom command** box, for any command from your tracker's manual

Accounts with **Limit commands** set in Traccar can only send the server's saved commands. The app picks the matching saved command for every button, so link the saved commands to both the account and the car. See [Your Traccar server](Traccar-server).

<br clear="right">
