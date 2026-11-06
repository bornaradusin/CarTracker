"""
Fills a fresh, throwaway Traccar with a made-up car, places and a day of trips around Dublin,
so the wiki's screenshots never show anyone's real car.

    python3 seed.py http://localhost:8082 http://localhost:5055
"""
import http.cookiejar
import json
import math
import sys
import time
import urllib.parse
import urllib.request

API = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8082"
OSMAND = sys.argv[2] if len(sys.argv) > 2 else "http://localhost:5055"
EMAIL, PASSWORD = "demo@cartracker.app", "cartracker-demo"
UNIQUE_ID = "352093081234567"

HOME = (53.32262, -6.26542)
WORK = (53.33930, -6.23810)
SHOP = (53.32620, -6.25560)

# Roughly along the roads: Rathmines, the Grand Canal, Grand Canal Dock, Ranelagh
HOME_TO_WORK = [HOME, (53.32420, -6.26560), (53.32640, -6.26600), (53.33070, -6.26520), (53.33190, -6.26180),
                (53.33300, -6.25600), (53.33420, -6.25060), (53.33550, -6.24520), (53.33650, -6.24100),
                (53.33780, -6.23950), WORK]
WORK_TO_SHOP = [WORK, (53.33650, -6.24100), (53.33550, -6.24520), (53.33420, -6.25060), (53.33000, -6.25250),
                (53.32800, -6.25400), SHOP]
SHOP_TO_HOME = [SHOP, (53.32560, -6.25800), (53.32500, -6.26000), (53.32400, -6.26400), HOME]

jar = http.cookiejar.CookieJar()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def call(method, path, body=None, form=False):
    data = None
    headers = {"Accept": "application/json"}
    if body is not None:
        if form:
            data = urllib.parse.urlencode(body).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        else:
            data = json.dumps(body).encode()
            headers["Content-Type"] = "application/json"
    request = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    with opener.open(request, timeout=30) as response:
        text = response.read().decode()
        return json.loads(text) if text.strip().startswith(("{", "[")) else text


def wait_for_server():
    for _ in range(120):
        try:
            call("GET", "/api/server")
            return
        except Exception:
            time.sleep(2)
    raise SystemExit("Traccar didn't start")


def send(point, at, speed_kmh=0.0, bearing=0.0, ignition=False, **extra):
    params = {
        "id": UNIQUE_ID,
        "lat": f"{point[0]:.6f}",
        "lon": f"{point[1]:.6f}",
        "timestamp": int(at),
        "speed": f"{speed_kmh / 1.852:.2f}",  # knots
        "bearing": f"{bearing:.0f}",
        "altitude": "18",
        "accuracy": "6",
        "ignition": "true" if ignition else "false",
        "power": "13.9" if ignition else "12.6",
        "batt": "100",
        "blocked": "false",
    }
    params.update({k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in extra.items()})
    urllib.request.urlopen(OSMAND + "/?" + urllib.parse.urlencode(params), timeout=10).read()
    # Traccar answers before it has finished with a position; give it time so they're handled in order
    time.sleep(0.15)


def metres(a, b):
    dy = (b[0] - a[0]) * 111_320
    dx = (b[1] - a[1]) * 111_320 * math.cos(math.radians(a[0]))
    return math.hypot(dx, dy)


def bearing(a, b):
    y = math.sin(math.radians(b[1] - a[1])) * math.cos(math.radians(b[0]))
    x = math.cos(math.radians(a[0])) * math.sin(math.radians(b[0])) - \
        math.sin(math.radians(a[0])) * math.cos(math.radians(b[0])) * math.cos(math.radians(b[1] - a[1]))
    return (math.degrees(math.atan2(y, x)) + 360) % 360


def drive(route, start, speed_kmh=32.0, fast_leg=None):
    """Sends a position every 15 s along [route]; returns the time it arrives."""
    t = start
    send(route[0], t, 0, 0, ignition=True)
    t += 60
    for i in range(len(route) - 1):
        a, b = route[i], route[i + 1]
        speed = 62.0 if fast_leg == i else speed_kmh
        steps = max(1, int(metres(a, b) / (speed / 3.6 * 15)))
        for s in range(1, steps + 1):
            f = s / steps
            p = (a[0] + (b[0] - a[0]) * f, a[1] + (b[1] - a[1]) * f)
            send(p, t, speed, bearing(a, b), ignition=True)
            t += 15
    send(route[-1], t, 0, 0, ignition=True)
    t += 45
    send(route[-1], t, 0, 0, ignition=False)
    return t


def park(point, start, until, **extra):
    t = start + 600
    while t < until:
        send(point, t, **extra)
        t += 600


def main():
    wait_for_server()
    call("POST", "/api/users", {"name": "Demo", "email": EMAIL, "password": PASSWORD})
    call("POST", "/api/session", {"email": EMAIL, "password": PASSWORD}, form=True)
    device = call("POST", "/api/devices", {
        "name": "Golf", "uniqueId": UNIQUE_ID, "category": "car",
        "attributes": {"speedLimit": round(50 / 1.852, 2)},
    })
    for name, point, radius, icon in (("Home", HOME, 150, "home"), ("Work", WORK, 200, "work"), ("Supermarket", SHOP, 120, "shop")):
        fence = call("POST", "/api/geofences", {
            "name": name, "area": f"CIRCLE ({point[0]} {point[1]}, {radius})", "attributes": {"icon": icon},
        })
        call("POST", "/api/permissions", {"deviceId": device["id"], "geofenceId": fence["id"]})

    now = time.time()
    # Yesterday: to work and back
    t = now - 86400 - 9.5 * 3600
    send(HOME, t - 1200)
    t = drive(HOME_TO_WORK, t)
    park(WORK, t, now - 86400 - 1.5 * 3600)
    t = drive(list(reversed(HOME_TO_WORK)), now - 86400 - 1.5 * 3600)
    park(HOME, t, now - 9 * 3600)
    # Today: to work, a knock while parked, the supermarket, then home (a bit too fast along the canal)
    t = drive(HOME_TO_WORK, now - 9 * 3600)
    park(WORK, t, now - 6 * 3600)
    send(WORK, now - 6 * 3600, alarm="vibration")
    park(WORK, now - 6 * 3600, now - 2.2 * 3600)
    t = drive(WORK_TO_SHOP, now - 2.2 * 3600, fast_leg=2)
    park(SHOP, t, now - 1.1 * 3600)
    t = drive(SHOP_TO_HOME, now - 1.1 * 3600)
    park(HOME, t, now - 60, armed=True)
    send(HOME, now - 30, armed=True)
    print(f"Seeded device {device['id']} with trips around Dublin")


def keep_alive():
    """A parked report every 45 s, so the car stays online while the screenshots are taken."""
    while True:
        send(HOME, time.time(), armed=True)
        time.sleep(45)


if __name__ == "__main__":
    if len(sys.argv) > 3 and sys.argv[3] == "keepalive":
        keep_alive()
    else:
        main()
