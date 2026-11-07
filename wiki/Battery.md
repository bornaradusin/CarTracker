# Battery

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/battery.png" width="270" align="right" alt="Battery history">

Tap **Battery** on the car card to see the car battery's voltage, as the tracker measures it.

- **Now**: the latest reading, and what it means: **Good**, **OK, a little low**, **Low**, or **Charging** while the engine runs.
- **24 hours, 3 days or 7 days** of readings as a line. The shaded parts are when the engine was running. The dashed line is your warning level.
- **Lowest parked**, **Highest** and how long the **engine** ran in that time.

A healthy 12 V battery reads about 12.4 to 12.7 V parked, and 13.8 to 14.4 V with the engine running. A battery that drops lower every night, or reads under 12 V parked, is worth having checked.

<br clear="right">

## Low battery warning

The app warns you itself when the battery runs low, with no setting needed on the tracker:

- It checks every reading **while the car is parked** (ignition off). Readings with the engine running don't count: the alternator lifts them and starting the engine dips them.
- Below your level (**11.8 V** unless you change it) you get one alert, **Golf: car battery low**.
- It won't warn again until the battery has recovered, usually after a drive.

Turn it off, or change the level from 11.0 to 12.4 V, under **Low battery warning** on this screen. Each car has its own level. The **Battery** tile on the car card turns orange below the same level.

This is separate from the tracker's own **Low car battery** alarm under [Controls](Controls), which the tracker raises below the voltage you set there (11.5 V at first).

Past days are kept on your phone once loaded, so the 7-day view opens quickly the second time.

## Reading correction

Some trackers read the battery a little low or high. Measure it with a multimeter across the battery terminals, compare with the app, and set the difference under **Reading correction**, from −1.0 to +1.0 V in steps of 0.1 V. For example, if the app says 12.2 V and the multimeter says 12.4 V, set **+0.2 V**.

The correction changes every reading in the app: the Car screen, this screen and its history, the widget and the low battery warning. On a MiCODUS tracker it also resends the tracker's own low battery alert, so it goes off at the voltage you set.
