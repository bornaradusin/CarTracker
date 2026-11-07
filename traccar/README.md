# Traccar with MiCODUS MV710G fixes

Traccar 6.17.0 with four changes to its JT808 code, for MiCODUS MV710G trackers:

1. **Commands sent the MiCODUS way.** A custom command that is an SMS style command such as
   `RELAY,0#` (as text, or as its hex) is sent wrapped in a JT808 pass-through message (0x8900,
   type 0xFF), the same way MiCODUS's own platform sends it. Traccar 6.17.0 sends the bare text,
   which the tracker only picks up some of the time. Applies to cars with no Model or a Model
   starting with `MV`; complete JT808 messages in hex still go as they are.
2. **The tracker's ID in commands.** MiCODUS IDs have 11 digits; Traccar 6.17.0 turns odd-length
   IDs into the wrong 6 bytes in messages it sends. With the patch, IDs under 12 digits are sent
   with a leading zero, as the tracker sends them.
3. **Command replies.** The MV710G answers commands with a JT808 pass-through message (0x0900)
   holding text: type 0xFF with `SF OK` for pass-through commands, type 0x00 with `SMS,SF OK`
   for bare text. Traccar 6.17.0 drops both. With the patch, the text is saved as the command
   result (`SF OK`), which Traccar turns into a "Command result" event.
4. **Alarm state.** The tracker reports whether its alarm is armed in bit 6 of the status word in
   every position. With the car's Model set to `MV710G`, Traccar saves it as the `armed`
   attribute. This is the same change as Traccar's own pull request 6110, merged after 6.17.0.

Everything else is the official `traccar/traccar:6.17.0` image: only its main program,
`tracker-server.jar`, is replaced with one built from Traccar 6.17.0's source with
[mv710g.patch](mv710g.patch). The build runs Traccar's own JT808 decoder and encoder tests first.

## Use it

In the stack, change the Traccar image:

```yaml
    image: ghcr.io/bornaradusin/cartracker-traccar:6.17.0-mv710g
```

Then in Traccar, edit the car and set **Model** to `MV710G`.

MV710G trackers send their time as UTC, while Traccar reads JT808 times as China time unless told
otherwise, so also set this in the Traccar service's environment:

```yaml
      DECODER_TIMEZONE: "UTC"
```

## Going back

Once an official Traccar release includes both changes, switch the image back to
`traccar/traccar:<that version>`. Nothing in the database depends on this image.
