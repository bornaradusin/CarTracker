# Trips

The **Trips** tab shows the car's driving, one day at a time.

<p>
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/trips.png" width="260" alt="Trips">
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/trips-dark.png" width="260" alt="Trips, dark">
</p>


- **The last 7 days** across the top. Tap a day to see it.
- **The day's summary**: distance, driving time and top speed.
- **One card per trip**: when it started and ended, how long it took, where it went from and to, the distance and top speed, and a sketch of the route.

Trips are worked out by your Traccar server from the car's positions. A stop shorter than a few minutes counts as part of the same trip.

Only real drives are listed: when the engine runs while the car stands still (idling, waiting in a car park), Traccar counts that as a trip too, so anything under 200 m is left out of the list, the day's totals and the stats. **Parked for** counts from the end of the last real drive.


## Stats

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/stats.png" width="270" align="right" alt="Stats">

Tap **Stats** under the day's summary.

- **Week, Month or Year**, with the arrows to go back to earlier ones.
- The totals: **distance**, **driving time**, number of **trips** and **top speed**.
- A bar for each day (a week or month) or each month (a year). Switch the bars between **Distance** and **Driving time**, and tap a bar for its numbers.
- The **average per day** so far, and the **longest trip**.

Finished days are kept on your phone, so a month or a year opens quickly after the first time. Each day counts trips the same way as the Trips tab, from the ignition.

<br clear="right">

## Heatmap

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/heatmap.png" width="270" align="right" alt="Heatmap">

Tap **Heatmap** to see every trip over a **week, 4 weeks, 3 months or a year** on one map. Each spot is a soft dot coloured by how often the car passed it: blue for once, through green and yellow, to red for the roads driven most. Zoom in to see single streets.

The first time a long stretch loads it can take a while: it shows how many weeks are done. Finished weeks are kept on your phone after that.

<br clear="right">


## Replay

Tap a trip to replay it on the map.

<p>
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/replay.png" width="260" alt="Replaying a trip">
<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/replay-dark.png" width="260" alt="Replaying a trip, dark">
</p>


- **Play / pause** the car along its route.
- **1×, 4× or 16×** speed.
- **Drag the slider** to jump to any point. The time and the car's speed at that point show above it, with the road's limit when your server [looks them up](Traccar-server#road-speed-limits).
- **The route is coloured by speed**: green when slow, then yellow, orange and red, to purple above 130 km/h. The bar under the numbers shows which colour is which speed.

