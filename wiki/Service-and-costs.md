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

## Adding and changing

- **Add a record** at the top: pick the type (service, repairs, upgrades, fuel or tax), the date, and fill in the rest. A fuel record asks how much went in, whether you filled to full and whether you missed a fill-up before it. Tax has no odometer.
- Tap any **record** in the list to change it, or to **delete** it (tap Delete twice). Picking another type moves the record to that type. Tags, extra fields and attached files a record has in LubeLogger are kept.
- **Add a reminder** at the bottom of Reminders, or tap one to change it. Give a due date, an odometer reading, or both; with both, whichever comes first counts. Reminders can't be deleted from here; do that in LubeLogger.

Numbers can be typed with a dot or a comma. If LubeLogger refuses a change, its own message is shown; with an API key that usually means the key's role is too low.

## Reminder notifications

With **Tell me about reminders** on, the app looks twice a day and sends one notification when a reminder becomes urgent, very urgent or past due. It tells you again only when the reminder moves up a level.

## Changing things

At the bottom of the screen: **Refresh** reads everything again, **Vehicle** lets you pick a different LubeLogger vehicle for the car, and **Disconnect** forgets the address and sign-in.

## If it doesn't connect

- *LubeLogger didn't accept the sign-in*: check the API key or the username and password.
- *No LubeLogger found at that address* or *answered, but not as LubeLogger*: check the address, including any port, and that the phone can reach it (on mobile data a home-only address won't work without a VPN).
