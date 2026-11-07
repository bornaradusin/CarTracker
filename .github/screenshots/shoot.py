"""
Installs CarTracker on the emulator, signs in to the throwaway Traccar filled by seed.py and
takes the wiki's screenshots.

    python3 shoot.py <app.apk> <output folder>
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

APK, OUT = sys.argv[1], sys.argv[2]
PACKAGE = "io.github.bornaradusin.cartracker"
HERE = os.path.dirname(os.path.abspath(__file__))
os.makedirs(OUT, exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(["adb", *args], capture_output=True, check=check).stdout


def shell(*args):
    return adb("shell", *args).decode(errors="replace")


def nodes():
    """Everything on screen, as (text, description, class, centre) from a UI dump."""
    for _ in range(5):
        raw = adb("exec-out", "uiautomator", "dump", "/dev/tty", check=False).decode(errors="replace")
        start, end = raw.find("<?xml"), raw.rfind("</hierarchy>")
        if start >= 0 and end > start:
            break
        time.sleep(1)
    else:
        return []
    root = ET.fromstring(raw[start:end + len("</hierarchy>")])
    found = []
    for n in root.iter("node"):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds", "[0,0][0,0]")))
        found.append((n.get("text", ""), n.get("content-desc", ""), n.get("class", ""), ((x1 + x2) // 2, (y1 + y2) // 2)))
    return found


def find(label, exact=True, timeout=20):
    deadline = time.time() + timeout
    while time.time() < deadline:
        for text, desc, _, centre in nodes():
            for value in (text, desc):
                if value and (value == label if exact else label in value):
                    return centre
        time.sleep(1)
    raise SystemExit(f"Not on screen: {label}")


def tap(label, exact=True, timeout=20):
    x, y = find(label, exact, timeout)
    shell("input", "tap", str(x), str(y))
    time.sleep(1.5)


def tap_at(x, y):
    shell("input", "tap", str(x), str(y))
    time.sleep(1.5)


def type_text(value):
    shell("input", "text", value.replace(" ", "%s"))
    time.sleep(0.5)


def hide_keyboard():
    """Back closes the keyboard, but only press it while the keyboard is up, or it leaves the app."""
    for _ in range(3):
        if "mInputShown=true" not in shell("dumpsys", "input_method"):
            return
        shell("input", "keyevent", "4")
        time.sleep(1.5)


def edit_boxes():
    return sorted(((text, centre) for text, _, cls, centre in nodes() if cls.endswith("EditText")), key=lambda b: b[1][1])


def fill(index, value, secret=False):
    """Types into the index-th text box, a few characters at a time, and retypes it if keys got lost."""
    for _ in range(3):
        text, centre = edit_boxes()[index]
        tap_at(*centre)
        shell("input", "keyevent", "123")  # to the end
        for _ in range(len(text) + 2):
            shell("input", "keyevent", "67")  # delete
        for i in range(0, len(value), 4):
            shell("input", "text", value[i:i + 4])
            time.sleep(0.3)
        time.sleep(1.5)
        typed = edit_boxes()[index][0]
        if (len(typed) == len(value)) if secret else (typed == value):
            return
        print(f"retyping box {index}: got {typed!r}")
    raise SystemExit(f"Couldn't type into box {index}")


def swipe(x1, y1, x2, y2, ms=400):
    shell("input", "swipe", str(x1), str(y1), str(x2), str(y2), str(ms))
    time.sleep(1.5)


def scroll_to(label, exact=True):
    for _ in range(8):
        try:
            return find(label, exact, timeout=2)
        except SystemExit:
            swipe(540, 1700, 540, 900)
    raise SystemExit(f"Couldn't scroll to: {label}")


def shot(name, settle=1.5):
    time.sleep(settle)
    with open(os.path.join(OUT, f"{name}.png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p"))
    print("screenshot", name)


def demo_status_bar():
    shell("settings", "put", "global", "sysui_demo_allowed", "1")
    for extras in (
        ["-e", "command", "enter"],
        ["-e", "command", "clock", "-e", "hhmm", "0941"],
        ["-e", "command", "battery", "-e", "level", "100", "-e", "plugged", "false"],
        ["-e", "command", "network", "-e", "wifi", "show", "-e", "level", "4"],
        ["-e", "command", "network", "-e", "mobile", "show", "-e", "datatype", "none", "-e", "level", "4"],
        ["-e", "command", "notifications", "-e", "visible", "false"],
    ):
        shell("am", "broadcast", "-a", "com.android.systemui.demo", *extras)


def main():

    adb("install", "-r", "-g", APK)
    shell("pm", "grant", PACKAGE, "android.permission.POST_NOTIFICATIONS")
    demo_status_bar()
    shell("am", "start", "-n", f"{PACKAGE}/.MainActivity")

    # Sign in: the emulator reaches the runner's Traccar at 10.0.2.2. The first start is slow.
    fields = []
    deadline = time.time() + 120
    while time.time() < deadline and len(fields) < 3:
        time.sleep(3)
        fields = [centre for _, _, cls, centre in nodes() if cls.endswith("EditText")]
    if len(fields) < 3:
        raise SystemExit("Sign-in fields not found")
    # Look the boxes up again each time: hints appear under them as you type and move the rest down
    for index, value in enumerate(("http://10.0.2.2:8082", "demo@cartracker.app", "cartracker-demo")):
        fill(index, value, secret=index == 2)
    hide_keyboard()
    shot("login")
    tap("Sign in")

    # Car
    find("All controls", timeout=60)
    shot("car", settle=10)
    tap("Map style")
    shot("map-styles")
    shell("input", "keyevent", "111")
    time.sleep(1)
    # Slide the car card down for more map, then back up
    _, card_y = find("Parked")
    swipe(540, card_y, 540, card_y + 1400, ms=500)
    shot("car-card-down", settle=4)
    _, card_y = find("Parked")
    swipe(540, card_y, 540, card_y - 1400, ms=500)

    # Battery history, from the battery tile
    tap("Battery")
    shot("battery", settle=5)
    shell("input", "keyevent", "4")
    time.sleep(2)
    # Sharing: where it is now, or a live link
    tap("Share")
    shot("share")
    tap("Close")

    # Controls
    tap("All controls")
    shot("controls")
    swipe(540, 1900, 540, 700)
    shot("controls-more")
    shell("input", "keyevent", "4")
    time.sleep(2)

    # Trips and a replay
    tap("Trips")
    shot("trips", settle=4)
    tap("Stats")
    shot("stats", settle=5)
    shell("input", "keyevent", "4")
    time.sleep(2)
    tap("Heatmap")
    shot("heatmap", settle=12)
    shell("input", "keyevent", "4")
    time.sleep(2)
    tap(" · top ", exact=False)
    shot("replay", settle=8)
    shell("input", "keyevent", "4")
    time.sleep(2)

    # Alerts
    tap("Alerts")
    shot("alerts", settle=4)

    # More
    tap("More")
    shot("more")
    x, y = scroll_to("Glow colour", exact=False)
    tap_at(x, y)
    shot("colour-picker")
    tap("Cancel")
    x, y = scroll_to("Supermarket", exact=False)
    tap_at(x, y)
    shot("place-edit")
    tap("Cancel")
    swipe(540, 1700, 540, 700)
    shot("more-settings")
    # The same screens in dark mode
    x, y = scroll_to("Dark")
    tap_at(x, y)
    tap("Car")
    shot("car-dark", settle=8)
    tap("All controls")
    shot("controls-dark")
    shell("input", "keyevent", "4")
    time.sleep(2)
    tap("Trips")
    shot("trips-dark", settle=3)
    tap(" · top ", exact=False)
    shot("replay-dark", settle=8)
    shell("input", "keyevent", "4")
    time.sleep(2)
    tap("Alerts")
    shot("alerts-dark", settle=3)
    tap("More")
    shot("more-dark", settle=2)


def explain_failure():
    """Leaves a picture of the screen and what was on it, to see where it stopped."""
    try:
        shot("failed-here", settle=0)
        for text, desc, cls, centre in nodes():
            if text or desc:
                print("on screen:", cls.split(".")[-1], repr(text or desc), centre)
    except Exception as e:
        print("couldn't capture the screen:", e)


if __name__ == "__main__":
    # Keeps the demo car online while the screenshots are taken; stopped at the end so the step can finish
    keepalive = subprocess.Popen(
        [sys.executable, os.path.join(HERE, "seed.py"), "http://localhost:8082", "http://localhost:5055", "keepalive"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        main()
    except BaseException:
        explain_failure()
        raise
    finally:
        keepalive.kill()
        # Any crash of the app during the run, with its stack trace
        try:
            crashes = adb("logcat", "-d", "-b", "crash", check=False).decode(errors="replace").strip()
            print("app crashes during the run:" if crashes else "no app crashes during the run")
            if crashes:
                print(crashes)
        except Exception as e:
            print("couldn't read the crash log:", e)
