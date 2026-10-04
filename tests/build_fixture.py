"""Build an unsigned shape fixture; patch it with Morphe before installing.

Usage: python tests/build_fixture.py --sdk <sdk> --app crunchyroll|media3 --out <directory>
"""
import argparse
from pathlib import Path
import os
import subprocess
import zipfile

p = argparse.ArgumentParser()
p.add_argument("--sdk", type=Path, required=True)
p.add_argument("--app", choices=["crunchyroll", "media3", "crunchyroll-tv"], required=True)
p.add_argument("--out", type=Path, required=True)
args = p.parse_args()
root = Path(__file__).resolve().parents[1]
out = args.out.resolve()
out.mkdir(parents=True, exist_ok=True)
tools = args.sdk / "build-tools" / "35.0.1"
android = args.sdk / "platforms" / "android-35" / "android.jar"
extensions = out / "extension-classes"
classes = out / "classes"
extensions.mkdir(exist_ok=True)
classes.mkdir(exist_ok=True)
def run(*command): subprocess.run([str(value) for value in command], check=True)
production = list((root / "extensions/extension/src/main/java").rglob("*.java"))
run("javac", "-encoding", "UTF-8", "--release", "11", "-cp", android, "-d", extensions, *production)
run("javac", "-encoding", "UTF-8", "--release", "11", "-cp", str(android) + os.pathsep + str(extensions), "-d", classes,
    *list((root / "tests/fixtures/src").rglob("*.java")))
jar = out / "fixture.jar"
with zipfile.ZipFile(jar, "w") as archive:
    for file in classes.rglob("*.class"): archive.write(file, file.relative_to(classes).as_posix())
package = "com.crunchyroll.crunchyroid" if args.app.startswith("crunchyroll") else "com.wbd.stream"
version = "3.74.0" if args.app == "crunchyroll-tv" else ("3.117.0" if args.app == "crunchyroll" else "7.9.0.84")
activity = "com.crunchyroll.crunchyroid.player.ui.PlayerActivity" if args.app == "crunchyroll-tv" else "fixture.TestActivity"
manifest = out / "AndroidManifest.xml"
manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="1" android:versionName="{version}">
<uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/><uses-permission android:name="android.permission.INTERNET"/>
<application android:debuggable="true" android:name="com.wbd.stream.MainApplication" android:label="Aimal subtitle fixture" android:theme="@android:style/Theme.Material.NoActionBar">
<activity android:name="{activity}" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity>
</application></manifest>''', encoding="utf-8")
apk = out / "fixture.apk"
res = out / "res/values"
res.mkdir(parents=True, exist_ok=True)
(res / "ids.xml").write_text('<resources><item name="exo_controller" type="id"/></resources>')
aapt = tools / ("aapt2.exe" if os.name == "nt" else "aapt2")
run(aapt, "compile", "--dir", out / "res", "-o", out / "resources.zip")
run(aapt, "link", "-I", android, "--manifest", manifest, "-o", apk, out / "resources.zip")
dex = out / "dex"
dex.mkdir(exist_ok=True)
run("java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "23", "--lib", android, "--classpath", extensions,
    "--output", dex, jar)
with zipfile.ZipFile(apk, "a") as archive:
    for file in dex.glob("*.dex"): archive.write(file, file.name)
    ndk = args.sdk / "ndk/27.0.12077973/toolchains/llvm/prebuilt"
    host = "windows-x86_64" if os.name == "nt" else "linux-x86_64"
    compiler = ndk / host / "bin" / ("clang.exe" if os.name == "nt" else "clang")
    native = out / "libaimalfixture.so"
    run(compiler, "--target=x86_64-linux-android23", "-shared", "-fPIC",
        root / "tests/fixtures/render_clock.c", "-o", native)
    archive.write(native, "lib/x86_64/libaimalfixture.so")
print(apk)
