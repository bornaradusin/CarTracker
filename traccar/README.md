# Traccar with MiCODUS MV710G fixes

Traccar 6.17.0 with two changes to its JT808 decoder, for MiCODUS MV710G trackers:

1. **Command replies.** The MV710G answers every command with a JT808 pass-through message
   (0x0900, type 0x00) holding text such as `SMS,SF OK`. Traccar 6.17.0 has no code for that
   message type and drops it, so replies never show as a command result. With the patch, the
   text after `SMS,` is saved as the command result (`SF OK`), which Traccar turns into a
   "Command result" event.
2. **Alarm state.** The tracker reports whether its alarm is armed in bit 6 of the status word in
   every position. With the car's Model set to `MV710G`, Traccar saves it as the `armed`
   attribute. This is the same change as Traccar's own pull request 6110, merged after 6.17.0.

Everything else is the official `traccar/traccar:6.17.0` image: only its main program,
`tracker-server.jar`, is replaced with one built from Traccar 6.17.0's source with
[mv710g.patch](mv710g.patch). The build runs Traccar's own JT808 decoder tests first.

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
