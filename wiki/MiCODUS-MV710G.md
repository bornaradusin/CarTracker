# MiCODUS MV710G

MiCODUS trackers (the MV710G and other JT808 models) report to Traccar on its JT808 port, 5015. CarTracker recognises them automatically and sends them MiCODUS's own commands, so the fuel cut, alarm, auto-arm and tracker alerts all work. You can also set it by hand under **More → Your car → Tracker type**.

## 1. Point the tracker at your server

Send this by SMS from the tracker's centre number, with your own server address:

```
SERVER,1,track.example.com,5015#
```

The tracker replies to confirm, then appears in Traccar within a minute or two. Add it in Traccar with the tracker's ID (the one MiCODUS uses) as the identifier.

## 2. Tell Traccar it's an MV710G

In Traccar's web page, edit the device and set **Model** to `MV710G`. Traccar then reads whether the alarm is armed from the tracker itself.

## 3. Use the right time zone

MiCODUS trackers send their time as UTC, while Traccar reads JT808 times as China time unless told otherwise. Without this, every position, trip and alert is 8 hours behind. In your Traccar container's environment, add:

```yaml
DECODER_TIMEZONE: "UTC"
```

## 4. Traccar for the MV710G

Traccar 6.17 sends MiCODUS text commands (like `RELAY,0#`) as bare text, which the tracker only picks up some of the time, and it drops the tracker's answers (for example `SF OK` when the alarm is set). Without the answers the app can't confirm anything.

Until an official Traccar release handles them, use this image instead of `traccar/traccar`. It's Traccar 6.17.0 with only these fixes ([details](https://github.com/bornaradusin/CarTracker/tree/main/traccar)):

- commands are sent the way MiCODUS's own platform sends them (a JT808 pass-through message), so the tracker gets them every time
- the tracker's answers are read and show as command results
- the alarm's armed state is read from every position (set the car's **Model** to `MV710G` in Traccar)

```yaml
image: ghcr.io/bornaradusin/cartracker-traccar:6.17.0-mv710g
```

## 5. Saved commands (optional)

For everyday accounts with **Limit commands** (see [Your Traccar server](Traccar-server#everyday-accounts-with-limited-commands)), the server needs saved commands for every button. They are plain MiCODUS commands sent as **Custom** commands, with the text written as hex. For example, `RELAY,0#` (restore fuel) is `52454c41592c3023`.

| Button | Command |
|---|---|
| Cut fuel now | `RELAY,1#` |
| Cut fuel safely | `RELAY,2#` |
| Restore fuel | `RELAY,0#` |
| Alarm on / off | `ARM#` / `DISARM#` |
| Arm automatically on / off | `ACCARM,ON,60#` / `ACCARM,OFF#` |
| Vibration alert on / off | `SENALM,ON,0#` / `SENALM,OFF#` |
| Low car battery alert on / off | `LVALM,ON,11.5,0#` / `LVALM,OFF#` |
| Tracker unplugged alert on / off | `PWRALM,ON,0#` / `PWRALM,OFF#` |
| Locate now | `WHERE#` |
| Restart tracker | `RESTART#` |

The `0` at the end of the alert commands means "report to the server only", so the tracker never sends SMS that cost you money.

### Commands to avoid

- `FACTORY#` and `TRAFFIC,OFF#`: the tracker drops off Traccar and only an SMS brings it back.
- `DATAGMT`: shifts the time of every position.
- Traccar's built-in **Engine stop** and **Engine resume**: MiCODUS trackers ignore them. Use the `RELAY` commands above.
