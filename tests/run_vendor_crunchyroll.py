"""Exercise actual 3.117.0 JNI/View hooks on an isolated emulator (no login).

Uses the user's APK; never downloads or publishes the commercial application.
Installs it under com.crunchyroll.crunchyroid using a temporary test signature.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

p = argparse.ArgumentParser()
p.add_argument("--sdk", type=Path, required=True)
p.add_argument("--apk", type=Path, required=True, help="Patched unsigned, merged Crunchyroll APK")
p.add_argument("--out", type=Path, required=True)
p.add_argument("--reuse-target", action="store_true", help="Rebuild only instrumentation against the already installed APK signed by this output directory's test key")
p.add_argument("--tv", action="store_true", help="Verify actual TV stream routing and separate ASS playback")
p.add_argument("--device", help="Explicit isolated emulator serial when other emulators are connected")
args = p.parse_args()
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
tools = args.sdk / "build-tools/35.0.1"
android = args.sdk / "platforms/android-35/android.jar"

def run(*cmd, capture=False):
    if cmd[0] == "adb": cmd = (os.environ.get("AIMAL_ADB", "adb"), *cmd[1:])
    return subprocess.run([str(x) for x in cmd], check=True, text=True, capture_output=capture)

devices = run("adb", "devices", capture=True).stdout.splitlines()[1:]
connected = [line.split()[0] for line in devices if line.strip() and line.split()[-1] == "device"]
selected = args.device if args.device else (connected[0] if len(connected) == 1 else None)
if selected not in connected or not selected.startswith("emulator-"):
    raise RuntimeError("Connect exactly one isolated emulator; this runner does not install on physical devices")
os.environ["ANDROID_SERIAL"] = selected
key = out / "vendor-test.jks"
if not key.exists():
    run("keytool", "-genkeypair", "-keystore", key, "-storepass", "android", "-keypass", "android",
        "-alias", "test", "-keyalg", "RSA", "-validity", "7", "-dname", "CN=Aimal Vendor Test")
classes = out / "classes"
classes.mkdir(exist_ok=True)
runner = "CrunchyrollTvRunner" if args.tv else "CrunchyrollRunner"
run("javac", "-encoding", "UTF-8", "--release", "11", "-cp", android, "-d", classes,
    Path(__file__).parent / ("vendor/" + runner + ".java"))
jar = out / "runner.jar"
with zipfile.ZipFile(jar, "w") as archive:
    for file in classes.rglob("*.class"): archive.write(file, file.relative_to(classes).as_posix())
manifest = out / "AndroidManifest.xml"
manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="app.aimal.verify">
<uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/>
<application android:label="Aimal vendor test"/>
<instrumentation android:name="app.aimal.verify.{runner}" android:targetPackage="com.crunchyroll.crunchyroid"/>
</manifest>''', encoding="utf-8")
test = out / "test.apk"
run(tools / ("aapt2.exe" if os.name == "nt" else "aapt2"), "link", "-I", android, "--manifest", manifest, "-o", test)
dex = out / "dex"
dex.mkdir(exist_ok=True)
run("java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "23", "--lib", android,
    "--output", dex, jar)
with zipfile.ZipFile(test, "a") as archive:
    for file in dex.glob("*.dex"): archive.write(file, file.name)
    if args.tv: archive.write(Path(__file__).parent / "vendor/sample.mp4", "assets/sample.mp4")
target = out / "target.apk"
shutil.copyfile(args.apk, target)
for apk in ((test,) if args.reuse_target else (test, target)):
    run("java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", key, "--ks-pass", "pass:android", apk)
    run("adb", "install", "--no-incremental", "-r", apk)
result = run("adb", "shell", "am", "instrument", "-w", "app.aimal.verify/app.aimal.verify." + runner, capture=True)
(out / "vendor-result.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
print(result.stdout)
if ("PASS: TV original" if args.tv else "PASS: vendor JNI") not in result.stdout:
    raise RuntimeError("Vendor hook verification failed; see vendor-result.txt")
