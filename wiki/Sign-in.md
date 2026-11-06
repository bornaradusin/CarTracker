# Sign in

<img src="https://raw.githubusercontent.com/wiki/bornaradusin/CarTracker/images/login.png" width="270" align="right" alt="Signing in">

1. **Server address**: your Traccar server, for example `track.example.com`. HTTPS is added for you. The app checks the server as you type and shows its Traccar version when it finds it.
2. **Email** and **Password**: your Traccar account.
3. Tap **Sign in**.

CarTracker keeps only a sign-in token, never your password. To sign in as someone else or to another server, go to **More → Server → Sign out**.

### Plain http

Addresses starting with `http://` are accepted only on your home network: `192.168.x.x`, `10.x.x.x`, `172.16–31.x.x`, Tailscale's `100.64–127.x.x`, and names ending in `.local`, `.lan` or `.home.arpa`. Anywhere else your password would cross the internet unencrypted, so the app asks for HTTPS.

### Signed out by the server

If the server stops accepting the app's token, for example after a password change, the app says so and asks you to sign in again.

<br clear="right">
