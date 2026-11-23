#!/usr/bin/env python3
"""CI : test de bout en bout. Vrai client Fabric 1.21.1 (portablemc, sous xvfb) avec les memes mods que le serveur de
test (run-server, prepare par smoke.py), connecte en operateur ; le client joue le scenario de LiveTest.java.
Captures et journaux dans out/live."""
import glob, hashlib, json, os, queue, shutil, subprocess, sys, threading, time, uuid

OUT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else "out")
DST = os.path.join(OUT, "live")
SRV = os.path.abspath("run-server")
MC = os.path.abspath("run-prod-client")
NAME = "ToolsTest"
os.makedirs(DST, exist_ok=True)
os.makedirs(os.path.join(MC, "mods"), exist_ok=True)
props = dict(l.strip().split("=", 1) for l in open("gradle.properties") if "=" in l and not l.startswith("#"))
steps = open(os.path.join(DST, "steps.txt"), "w")


def log(msg):
    print(msg, flush=True)
    steps.write(msg + "\n")
    steps.flush()


if not os.path.exists(os.path.join(SRV, "server.jar")):
    log("serveur absent (smoke.py)")
    sys.exit(0)

subprocess.run("sudo apt-get install -y -qq xvfb mesa-utils libgl1-mesa-dri x11-apps imagemagick > /dev/null 2>&1 || true", shell=True)
subprocess.run([sys.executable, "-m", "pip", "install", "-q", "portablemc==4.4.1"])
for f in glob.glob(os.path.join(SRV, "mods", "*.jar")):
    if "luckperms" in os.path.basename(f).lower():
        continue
    shutil.copy(f, os.path.join(MC, "mods"))
log("mods client : %s" % sorted(os.listdir(os.path.join(MC, "mods"))))
version = "fabric:1.21.1:" + props.get("loader_version", "0.19.5")
base = [sys.executable, "-m", "portablemc", "--main-dir", MC, "--work-dir", MC]
java = shutil.which("java") or "java"
r = subprocess.run(base + ["start", "--dry", "--jvm", java, version], capture_output=True, text=True)
log("preparation du client : code %s" % r.returncode)
with open(os.path.join(MC, "options.txt"), "w") as fh:
    fh.write("onboardAccessibility:false\nnarrator:0\ntutorialStep:none\nskipMultiplayerWarning:true\njoinedFirstServer:true\n"
             "renderDistance:4\nmaxFps:30\nsoundCategory_master:0.0\nguiScale:2\nlang:fr_fr\n")

with open(os.path.join(SRV, "server.properties"), "a") as fh:
    fh.write("enforce-secure-profile=false\n")
# operateur hors ligne : UUID calcule comme le serveur
h = bytearray(hashlib.md5(("OfflinePlayer:" + NAME).encode()).digest())
h[6] = (h[6] & 0x0f) | 0x30
h[8] = (h[8] & 0x3f) | 0x80
with open(os.path.join(SRV, "ops.json"), "w") as fh:
    json.dump([{"uuid": str(uuid.UUID(bytes=bytes(h))), "name": NAME, "level": 4, "bypassesPlayerLimit": False}], fh)
srv = subprocess.Popen(["java", "-Xmx3G", "-jar", "server.jar", "nogui"], cwd=SRV,
                       stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
lines = queue.Queue()
slog = open(os.path.join(DST, "server.log"), "w")


def reader():
    for line in srv.stdout:
        slog.write(line)
        slog.flush()
        lines.put(line)
    lines.put(None)


threading.Thread(target=reader, daemon=True).start()


def wait_for(token, timeout):
    end = time.time() + timeout
    while time.time() < end:
        try:
            line = lines.get(timeout=1)
        except queue.Empty:
            continue
        if line is None:
            return False
        if token in line:
            return True
    return False


ok = wait_for("Done (", 900)
log("serveur pret : %s" % ok)
if ok:
    for c in ["gamerule doDaylightCycle false", "gamerule doWeatherCycle false", "gamerule doMobSpawning false", "time set 6000", "weather clear"]:
        srv.stdin.write(c + "\n")
        srv.stdin.flush()
        time.sleep(1)
    env = dict(os.environ, LIBGL_ALWAYS_SOFTWARE="1", MESA_GL_VERSION_OVERRIDE="4.5")
    clog = open(os.path.join(DST, "client.log"), "w")
    cl = ["xvfb-run", "-n", "98", "-s", "-screen 0 1280x720x24"] + base + ["start", "-u", NAME, "--resolution", "1280x720", "--jvm", java,
          "--jvm-args=-Xmx3G -Dcobblepulse_tools.livetest=true", "-s", "127.0.0.1", "-p", "25565", version]
    log("$ " + " ".join(cl))
    client = subprocess.Popen(cl, stdout=clog, stderr=subprocess.STDOUT, env=env)
    t0 = time.time()
    while client.poll() is None and time.time() - t0 < 1200:
        time.sleep(10)
    if client.poll() is None:
        log("!! client : delai depasse")
        subprocess.run(f"xwd -root -display :98 -silent | convert xwd:- {DST}/xvfb_timeout.png", shell=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        client.kill()
        subprocess.run("pkill -f net.fabricmc.loader.impl.launch.knot.KnotClient", shell=True)
    log("client termine (%.0f s)" % (time.time() - t0))
    clog.close()
    time.sleep(3)
try:
    srv.stdin.write("stop\n")
    srv.stdin.flush()
    srv.wait(timeout=180)
except Exception:
    srv.kill()
slog.close()
for f in glob.glob(os.path.join(MC, "screenshots", "*.png")):
    shutil.copy(f, DST)
for f in glob.glob(os.path.join(MC, "logs", "latest.log")):
    shutil.copy(f, os.path.join(DST, "client-latest.log"))
for f in glob.glob(os.path.join(MC, "crash-reports", "*")) + glob.glob(os.path.join(SRV, "crash-reports", "*")):
    shutil.copy(f, DST)
try:
    for l in open(os.path.join(DST, "client-latest.log"), errors="replace"):
        if "[livetest]" in l and ("PASS" in l or "FAIL" in l or "SUMMARY" in l):
            log(l.strip()[:300])
except Exception:
    pass
log("captures : %s" % sorted(os.listdir(DST)))
