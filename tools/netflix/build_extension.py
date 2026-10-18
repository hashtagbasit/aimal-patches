"""Build the Netflix 8.3.11 integration into an already prepared independent APK.

The decoded project must retain the native 8.3.11 ARM libraries. Signing and
installation are deliberately separate; updates must use the existing signer.
"""
import argparse
from pathlib import Path
import subprocess
import zipfile
import hashlib

p = argparse.ArgumentParser()
p.add_argument('--project', type=Path, required=True)
p.add_argument('--sdk', type=Path, required=True)
p.add_argument('--apktool', type=Path, required=True)
p.add_argument('--out', type=Path, required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[2]
native = a.project / 'lib/armeabi-v7a/libnetflix.so'
if hashlib.sha256(native.read_bytes()).hexdigest() != '062832e1160a1014a435f4102790d306cc86e8c60f96bac5bec65ecb28677e17':
    raise SystemExit('Unsupported native binary: this integration requires Netflix TV 8.3.11 build 12041 ARM32')
a.out.mkdir(parents=True, exist_ok=True)
def run(*args): subprocess.run([str(x) for x in args], check=True)

activity = a.project / 'smali_classes2/com/netflix/ninja/MainActivity.smali'
text = activity.read_text(encoding='utf-8')
bridge = 'Lapp/aimal/extension/netflix/NetflixSubtitles;'
if bridge not in text:
    text = text.replace('invoke-super {p0}, Landroid/app/Activity;->onResume()V',
        f'invoke-super {{p0}}, Landroid/app/Activity;->onResume()V\n\n    invoke-static {{p0}}, {bridge}->attach(Landroid/app/Activity;)V')
    text = text.replace('invoke-super {p0}, Landroid/app/Activity;->onPause()V',
        f'invoke-super {{p0}}, Landroid/app/Activity;->onPause()V\n\n    invoke-static {{p0}}, {bridge}->detach(Landroid/app/Activity;)V')
    assert '.method public dispatchKeyEvent(' not in text
    text += f'''
.method public dispatchKeyEvent(Landroid/view/KeyEvent;)Z
    .locals 1
    invoke-static {{p0, p1}}, {bridge}->onKey(Landroid/app/Activity;Landroid/view/KeyEvent;)Z
    move-result v0
    if-eqz v0, :aimal_native_key
    return v0
    :aimal_native_key
    invoke-super {{p0, p1}}, Landroid/app/Activity;->dispatchKeyEvent(Landroid/view/KeyEvent;)Z
    move-result v0
    return v0
.end method
'''
    activity.write_text(text, encoding='utf-8')

ndk = a.sdk / 'ndk/27.0.12077973/toolchains/llvm/prebuilt/windows-x86_64/bin/clang++.exe'
run(ndk, '--target=armv7a-linux-androideabi23', '-marm', '-shared', '-fPIC', '-O2', '-std=c++17',
    '-static-libstdc++', Path(__file__).with_name('native_captions.cpp'),
    '-llog', '-ldl', '-o', a.project / 'lib/armeabi-v7a/libaimal_netflix_probe.so')
android = a.sdk / 'platforms/android-35/android.jar'
classes = a.out / 'classes'
classes.mkdir(exist_ok=True)
run('javac', '-encoding', 'UTF-8', '--release', '11', '-cp', android, '-d', classes,
    *list((root / 'extensions/extension/src/main/java').rglob('*.java')))
jar = a.out / 'extension.jar'
with zipfile.ZipFile(jar, 'w') as z:
    for file in classes.rglob('*.class'): z.write(file, file.relative_to(classes).as_posix())
dex = a.out / 'dex'
dex.mkdir(exist_ok=True)
run('java', '-cp', a.sdk / 'build-tools/35.0.1/lib/d8.jar', 'com.android.tools.r8.D8',
    '--min-api', '24', '--lib', android, '--output', dex, jar)
apk = a.out / 'netflix-unsigned.apk'
run('java', '-jar', a.apktool, 'b', a.project, '-o', apk)
with zipfile.ZipFile(apk, 'a', compression=zipfile.ZIP_DEFLATED) as z:
    assert 'classes4.dex' not in z.namelist()
    z.write(dex / 'classes.dex', 'classes4.dex')
print(apk)
