"""Patch, sign and execute the two Android shape fixtures on a connected emulator."""
import argparse
from pathlib import Path
import subprocess
import sys
import time

p = argparse.ArgumentParser()
p.add_argument("--sdk", type=Path, required=True)
p.add_argument("--bundle", type=Path, required=True)
p.add_argument("--morphe", type=Path, required=True)
p.add_argument("--out", type=Path, required=True)
p.add_argument("--fonts", action="store_true")
args = p.parse_args()
if args.bundle.is_dir():
    bundles = [file for file in args.bundle.glob("*.mpp") if not any(part in file.name for part in ("-sources", "-javadoc"))]
    if len(bundles) != 1: raise ValueError("Expected exactly one MPP in the bundle directory")
    args.bundle = bundles[0]
args.out.mkdir(parents=True, exist_ok=True)
tools = args.sdk / "build-tools/35.0.1"
def run(*command, capture=False, check=True):
    return subprocess.run([str(x) for x in command], text=True, capture_output=capture, check=check)

key = args.out / "fixture-key.jks"
if not key.exists():
    run("keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android", "-alias", "fixture",
        "-keyalg", "RSA", "-validity", "7", "-dname", "CN=Aimal Fixture")
for app, package, patch in [
    ("crunchyroll", "com.crunchyroll.crunchyroid", "Subtitle styling"),
    ("media3", "com.wbd.stream", "Playback speed and aspect ratio"),
    ("crunchyroll-tv", "com.crunchyroll.crunchyroid", "Subtitle styling (Android TV)"),
]:
    out = args.out / app
    run(sys.executable, Path(__file__).with_name("build_fixture.py"), "--sdk", args.sdk, "--app", app, "--out", out)
    apk = out / "patched.apk"
    run("java", "-jar", args.morphe, "patch", "-p", args.bundle, "--exclusive", "-e", patch,
        out / "fixture.apk", "-o", apk, "--unsigned", "-r", out / "patch-result.json")
    run("java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", key, "--ks-pass", "pass:android", apk)
    run("adb", "install", "-r", apk)
    run("adb", "shell", "am", "force-stop", package)
    run("adb", "shell", "run-as", package, "rm", "-f", "files/fixture-result.txt", "files/font-result.txt", "files/controls-result.txt", "files/tv-result.txt")
    activity = "com.crunchyroll.crunchyroid.player.ui.PlayerActivity" if app == "crunchyroll-tv" else "fixture.TestActivity"
    run("adb", "shell", "am", "start", "-W", "-S", "-n", package + "/" + activity, "--ez", "fonts", str(args.fonts).lower())
    files = ["fixture-result.txt"] + (["tv-result.txt"] if app == "crunchyroll-tv" else []) + (["controls-result.txt"] if app == "crunchyroll" else []) + (["font-result.txt"] if args.fonts else [])
    for file in files:
        deadline = time.monotonic() + 55
        while time.monotonic() < deadline:
            result = run("adb", "shell", "run-as", package, "cat", "files/" + file, capture=True, check=False)
            if result.returncode == 0 and result.stdout:
                print(package + ": " + result.stdout, flush=True)
                (out / file).write_text(result.stdout, encoding="utf-8")
                if not result.stdout.startswith("PASS:"): sys.exit(1)
                break
            time.sleep(1)
        else:
            raise RuntimeError(package + ": timed out waiting for " + file)
