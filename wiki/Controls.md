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

One tap is enough: the button shows **Confirming…** until the tracker answers, then switches to the new state straight away. A tracker that's asleep or between connections can miss a command, so if it hasn't answered, the app sends it again after 20 and 50 seconds. If the car is offline, Traccar keeps the command and the tracker gets it when it reconnects.

**Ask for fingerprint** (on at first): cutting or restoring fuel asks for your fingerprint, face or the phone's PIN first, so it can't happen by accident or by someone else picking up your phone. Turn it off in the same card. Phones with no screen lock skip it.

## Alarm

- **Alarm**: armed, the tracker alerts you if the car is moved or shaken.
- **Arm automatically** (MiCODUS): arms the alarm a minute after the ignition goes off.

## Tracker alerts

These are set on the tracker itself:

- **Vibration or movement**: bumps, knocks or towing while parked
- **Low car battery**: on MiCODUS trackers, set the voltage with **−** and **+** under **Alert below**, in steps of 0.1 V (11.5 V at first)
- **Tracker unplugged**: power to the tracker is cut

**Speeding**: turn it on, then set the limit with **−** and **+** under **Alert above**, in steps of 10 km/h from 30 to 200. Your Traccar server watches the limit for any tracker. A MiCODUS tracker is sent the limit too (`SPEED,ON,<limit>,0#`, a moment after the last tap), so it raises its own alarm as well. One speeding burst shows as one alert, even when both report it.

**Ignition on / off**: ignition alerts come from the tracker's ACC wire, which every report carries, so they work on any tracker. On a MiCODUS tracker the switch also sends `ACCALM,ON,2,0#` (or `ACCALM,OFF#`), so the tracker reports ignition changes at once and only to the server. Out of the box it also texts them to the SIM's owner.

These commands, and their answers, show in the [Alerts](Alerts) list like any other.

<br clear="right">

## Waiting for the tracker

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/controls-more.png" width="270" align="right" alt="Tracker and advanced commands">

Settings stored on the tracker (alarm, arm automatically, and the three tracker alerts) only change in the app once the tracker confirms them. Until then, the switch says **Waiting for … to confirm…**. If the tracker doesn't answer within 90 seconds, nothing changes and the app tells you.

If the car is offline, the app won't send these, because the setting couldn't be confirmed.

## Tracker

At first, this card has:

- **Locate now**: asks the tracker for a fresh position.
- **Update rate**: how often the tracker reports. Choose from every 5 s driving / 1 min parked, every 10 s / 3 min, every 30 s / 5 min, or every 1 min / 10 min. Faster rates use more mobile data.
- **Restart tracker**: restarts it. It's back online within a minute or two.

You choose what's here: see below.

## Advanced commands

Turn on **More → Commands → Advanced commands** to add this section. It has:

- every command Traccar offers for your tracker
- the commands saved on your server
- a **Custom command** box, for any command from your tracker's manual

### Choosing what's on Controls

With Advanced commands on, every command has a star. Tap it under **Advanced commands** to move the command up to the **Tracker** card, and tap the coloured star in the **Tracker** card to move it back down. This works for Locate now, Update rate and Restart tracker too.

To keep a custom command as a button (say `STATUS#`), type it in the **Custom command** box and tap the star next to **Send**.

Each car has its own choice. Turning Advanced commands off hides the stars and the section, and the Tracker card stays as you set it.

Accounts with **Limit commands** set in Traccar can only send the server's saved commands. The app picks the matching saved command for every button, so link the saved commands to both the account and the car. See [Your Traccar server](Traccar-server).

<br clear="right">
