# Map

<p>
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/car.png" width="260" alt="The map">
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/car-dark.png" width="260" alt="The map, dark">
</p>

The **Car** tab shows your car on the map, with its card at the bottom.

- **The car follows itself.** Every time the car sends a new position, the map moves to keep it in the middle, at whatever zoom you've chosen.
- **You're on the map too.** Once you allow location, a blue dot shows where you are.
- **The pin's point** is exactly where the car is. Tap another car's pin to switch to it.
- **Top left:** the car's name. With more than one car, tap it to switch.
- **Top right:** the bell, with the number of new alerts.

### Buttons on the right

| Button | What it does |
|---|---|
| Layers | Map style: **Standard**, **Clean**, **Black & white** or **Terrain**. Each follows the app's light or dark theme |
| Crosshair | Shows you and the car together. The first time, it asks to use your location. The map stops following the car until you tap the car button |
| Car | Puts the car back in the middle and follows it again |

<br clear="right">

## The car card

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/car-card-down.png" width="270" align="right" alt="The card slid down">

- The car's **name**, the **street** it's on, and whether it's **Moving**, **Parked** or **Offline**, with when it last reported.
- **Ignition**, **Speed** while driving, distance driven **Today**, and the car's **Battery** voltage. The voltage turns orange below your [warning level](Battery#low-battery-warning) (11.8 V unless you change it). Tap it for the battery's history: see [Battery](Battery).
- With [road speed limits](Traccar-server#road-speed-limits) on your server, **Speed** shows the road's limit too, and turns orange over it.
- **Parked for**: how long since the last drive ended. Right after you park it says **< 5 min**, until Traccar has seen the car stand still long enough to count the drive as over.
- **Immobilise**: cut or restore the fuel, after your fingerprint (or the phone's PIN). The button turns orange while the fuel is cut and the car can't start. See [Controls](Controls).
- **Alarm**: arm or disarm. It's coloured while the alarm is armed, and shows **Confirming…** until the tracker confirms.
- **Directions**: turn-by-turn to the car in Google Maps.
- **Share**: see [Sharing the car](#sharing-the-car) below.
- **All controls**: everything the tracker can do. See [Controls](Controls).

**Slide the card down** to see more of the map: only its top stays showing. Slide it back up to bring it back. The map re-centres on the car each time.

<br clear="right">

## Sharing the car

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/share.png" width="270" align="right" alt="Sharing the car">

**Share** on the car card offers:

- **Where it is now**: a Google Maps link to the spot it's at, to send in any app.
- **A live link** for **1 hour, 4 hours, 1 day or 1 week**: whoever opens it follows the car on a map in their browser until it ends. They can't control the car or see its trips, and they don't need an account or the app.

Traccar allows one live link per car at a time. While one is running, **Share** offers to send it again or **stop it now**. Stopping one early needs an admin account on the server; otherwise it stops by itself when it ends. Live links use Traccar's own sharing: see [Your Traccar server](Traccar-server#live-links).

<br clear="right">

## Map styles

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/map-styles.png" width="270" alt="Map styles">

The maps come from [OpenFreeMap](https://openfreemap.org) and [OpenTopoMap](https://opentopomap.org): free, with no Google key.
