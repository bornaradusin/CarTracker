# Service and costs

If you keep your car's records in [LubeLogger](https://lubelogger.com), CarTracker can show them: reminders, what the car has cost, its fuel and every record. It works on your own LubeLogger server: what you add or change here goes straight into LubeLogger.

## Connecting

Open the **Service** tab at the bottom and fill in:

- **LubeLogger address**: where you open LubeLogger in a browser, such as `lubelogger.example.com`. Add `http://` in front if yours doesn't use https.
- **API key**: make one in LubeLogger's settings. **Viewer** is enough to look, **Editor** to add and change records, **Manager** to delete them.
- Or, in place of an API key, your LubeLogger **username and password**. Accounts that sign in through single sign-on need an API key.

Tap **Connect**, then pick which LubeLogger vehicle the car is. Each car in CarTracker gets its own.

The address and sign-in are kept on your phone only.

## What it shows

- **Reminders**, most urgent first, each with a coloured dot (red past due, orange very urgent, yellow urgent, green not urgent), its due date or odometer reading, and how many days are left.
- **Costs** for the last **12 months**, **this year** or **all time**: the total, a bar for each type (service, repairs, upgrades, fuel, tax) and a month-by-month chart with the same colours. All time charts the last three years.
- **Fuel, last 12 months**: fill-ups, how much fuel, the price per unit and the average economy, plus a chart of the economy fill by fill. Amounts and economy are in the units you use in LubeLogger.
- **Records**: everything, newest first. The chips above the list show one type at a time.

Money is shown with your phone's currency symbol.

## Service items

Under **Add a record** is a list of things to service, each counting down by kilometres, months, or both, whichever comes first. A new car starts with engine oil, air filter, cabin filter, spark plugs and brake fluid at their usual intervals; these are starting points, so set them to what the car's service book says.

Each item shows:

- **How far off it is**: *8,284 km left*, *5 months left* or *36 days overdue*.
- **A health bar** of 20 squares, like in a game: all lit just after it's done, going out one by one down to none when it's due. Green, amber once under half is left, red when it's due within 1,000 km or 30 days; overdue, the empty squares turn red too.
- **How often and when next**: *Every 15,000 km or every year · next at 192,500 km or by Aug 2027*.

The most urgent items are at the top. Items not set up yet say *Not set yet* and sit at the bottom.

**The odometer** the items count from is the newest reading in LubeLogger, from any record. When the app's own count for the car has carried on a little past that reading (up to 10,000 km), it uses that; the line at the top of the list says which.

**When an item was last done** comes from LubeLogger by itself: a service, repair or upgrade record with the item's name in its description counts as the item being done on that record's date and odometer. Name items the way you write them in LubeLogger ("Engine oil" matches "Engine oil and filter"). For work done before you kept records, tap the item and type in when it was last done; the later of the two is used.

Tap an item to change its name, how often, or when it was last done, or to **remove** it. **Done today…** opens a new service record with the item's name already filled in: save it and the bar is full again.

**Add an item** at the bottom offers common parts with their usual intervals to start from (oil filter, fuel filter, timing belt, auxiliary belt, water pump, gearbox oil, Haldex oil and filter, brake pads and discs, coolant, tyres, wiper blades, battery, air con service, NCT and more), or type your own.

**Where they're kept:** in LubeLogger, as one reminder each, named after the item. Its notes say how often and when it was last done (the line in square brackets is what the apps read, so leave it as it is), and it's due when the item next is, so LubeLogger shows and reminds about it too. They don't appear again under Reminders here. When work is logged with an item's name, the app moves its reminder on to the next time by itself. Items this phone had before were moved there the first time this version opened. [CarDash](https://github.com/bornaradusin/CarDash/wiki/Service) shows the same items, read only.

## Adding and changing

- **Add a record** at the top: pick the type (service, repairs, upgrades, fuel or tax), the date, and fill in the rest. A fuel record asks how much went in, whether you filled to full and whether you missed a fill-up before it. Tax has no odometer.
- Tap any **record** in the list to change it, or to **delete** it (tap Delete twice). Picking another type moves the record to that type. Tags, extra fields and attached files a record has in LubeLogger are kept.
- **Add a reminder** at the bottom of Reminders, or tap one to change it or to **delete** it (tap Delete twice). Give a due date, an odometer reading, or both; with both, whichever comes first counts.

Numbers can be typed with a dot or a comma. If LubeLogger refuses a change, its own message is shown; with an API key that usually means the key's role is too low.

## With CarDash (optional)

[CarDash](https://github.com/bornaradusin/CarDash), on the car's radio, reads the exact kilometres from the dashboard. Connected to the same LubeLogger, it puts them there once a day, and CarTracker can use them:

- **Service items** count from LubeLogger's newest reading, so with CarDash they follow the dashboard with nothing to turn on.
- **Match the kilometres to CarDash**, under the LubeLogger settings at the bottom of the Service tab, also sets the tracker's own odometer (the one on the car screen and in trips) from those readings. The app looks at most every half hour. It finds the tracker's count at the moment the dashboard was read and moves today's count by the difference, so driving since then is kept. Changes under 200 m are left alone. It needs a Traccar account that's allowed to change the odometer. The setting's hint says what happened last time: no reading from CarDash yet, a reading found but not the tracker's count from then yet, a problem reading LubeLogger, or what the odometer was set to. It checks again each time you open the Service tab.
- **CarDash shows the service items** from LubeLogger, counting down from the dashboard's kilometres. They're set up and logged here.

## Reminder notifications

With **Tell me about reminders** on, the app looks twice a day and sends one notification when a reminder becomes urgent, very urgent or past due. It tells you again only when the reminder moves up a level.

## Changing things

At the bottom of the screen: **Refresh** reads everything again, **Vehicle** lets you pick a different LubeLogger vehicle for the car, and **Disconnect** forgets the address and sign-in.

## If it doesn't connect

- *LubeLogger didn't accept the sign-in*: check the API key or the username and password.
- *No LubeLogger found at that address* or *answered, but not as LubeLogger*: check the address, including any port, and that the phone can reach it (on mobile data a home-only address won't work without a VPN).
