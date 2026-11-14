# Service and costs

If you keep your car's records in [LubeLogger](https://lubelogger.com), CarTracker can show them: reminders, what the car has cost, its fuel and every record. It reads from your own LubeLogger server and changes nothing there.

## Connecting

Open **More → Service and costs → LubeLogger** and fill in:

- **LubeLogger address**: where you open LubeLogger in a browser, such as `lubelogger.example.com`. Add `http://` in front if yours doesn't use https.
- **API key**: make one in LubeLogger's settings. The **Viewer** role is enough.
- Or, in place of an API key, your LubeLogger **username and password**. Accounts that sign in through single sign-on need an API key.

Tap **Connect**, then pick which LubeLogger vehicle the car is. Each car in CarTracker gets its own. Once connected, the car card has a **Service and costs** button too.

The address and sign-in are kept on your phone only.

## What it shows

- **Reminders**, most urgent first, each with a coloured dot (red past due, orange very urgent, yellow urgent, green not urgent), its due date or odometer reading, and how many days are left.
- **Costs** for the last **12 months**, **this year** or **all time**: the total, a bar for each type (service, repairs, upgrades, fuel, tax) and a month-by-month chart with the same colours. All time charts the last three years.
- **Fuel, last 12 months**: fill-ups, how much fuel, the price per unit and the average economy, plus a chart of the economy fill by fill. Amounts and economy are in the units you use in LubeLogger.
- **Records**: everything, newest first. The chips above the list show one type at a time.

Money is shown with your phone's currency symbol.

## Reminder notifications

With **Tell me about reminders** on, the app looks twice a day and sends one notification when a reminder becomes urgent, very urgent or past due. It tells you again only when the reminder moves up a level.

## Changing things

At the bottom of the screen: **Refresh** reads everything again, **Vehicle** lets you pick a different LubeLogger vehicle for the car, and **Disconnect** forgets the address and sign-in.

## If it doesn't connect

- *LubeLogger didn't accept the sign-in*: check the API key or the username and password.
- *No LubeLogger found at that address* or *answered, but not as LubeLogger*: check the address, including any port, and that the phone can reach it (on mobile data a home-only address won't work without a VPN).
