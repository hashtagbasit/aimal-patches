"""Prepare an independent Netflix 8.3.11 project for build_extension.py."""
from pathlib import Path
import argparse
import shutil
import xml.etree.ElementTree as ET
import hashlib
import re

parser = argparse.ArgumentParser()
parser.add_argument('--source', type=Path, required=True)
parser.add_argument('--target', type=Path, required=True)
parser.add_argument('--signer-sha256', required=True, help='SHA-256 of your signing certificate, not the private key')
args = parser.parse_args()
source = args.source.resolve()
target = args.target.resolve()
signer = args.signer_sha256.replace(':', '').upper()
if not re.fullmatch('[0-9A-F]{64}', signer): raise SystemExit('Invalid signing certificate SHA-256')
native = source / 'lib/armeabi-v7a/libnetflix.so'
if hashlib.sha256(native.read_bytes()).hexdigest() != '062832e1160a1014a435f4102790d306cc86e8c60f96bac5bec65ecb28677e17':
    raise SystemExit('Unsupported native binary; use Netflix TV 8.3.11 build 12041 ARM32')
if target.exists():
    raise SystemExit("Compatibility directory already exists; refusing to overwrite")
shutil.copytree(source, target)
android = "{http://schemas.android.com/apk/res/android}"
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
ET.register_namespace("amazon", "http://schemas.amazon.com/apk/res/android")
manifest = target / "AndroidManifest.xml"
tree = ET.parse(manifest)
document = tree.getroot()
original = document.attrib["package"]
package = "app.aimal.netflix.compat"
document.set("package", package)
permissions = {
    node.attrib[android + "name"]: (
        package + node.attrib[android + "name"][len(original):]
        if node.attrib[android + "name"].startswith(original + ".")
        else package + ".permission." + str(index)
    )
    for index, node in enumerate(document.findall("permission"))
}
components = {"application", "activity", "activity-alias", "service", "receiver", "provider", "instrumentation"}
for node in document.iter():
    for key, value in list(node.attrib.items()):
        if value in permissions:
            node.set(key, permissions[value])
    if node.tag in components:
        name = node.get(android + "name", "")
        if name.startswith("."):
            node.set(android + "name", original + name)
        target_name = node.get(android + "targetActivity", "")
        if target_name.startswith("."):
            node.set(android + "targetActivity", original + target_name)
    if node.tag == "provider" and android + "authorities" in node.attrib:
        node.set(android + "authorities", ";".join(
            package + ".provider." + value.replace(".", "_")
            for value in node.attrib[android + "authorities"].split(";")
        ))
    if android + "label" in node.attrib:
        node.set(android + "label", "Netflix Aimal compatibility test")
tree.write(manifest, encoding="utf-8", xml_declaration=True)

# The app's own integrity check must recognize the new APK's public certificate.
loader = target / 'smali_classes2/com/netflix/ninja/Loader$1.smali'
body = loader.read_text(encoding='utf-8')
official = '36:38:63:59:6E:A9:92:41:EB:71:B1:A9:85:55:3A:A6:04:DE:3E:A3:C5:F0:C5:46:74:23:90:E6:82:16:4E:6B'
assert official in body, 'Unexpected integrity check'
loader.write_text(body.replace(official, ':'.join(signer[i:i+2] for i in range(0,64,2))), encoding='utf-8')

helper = target / 'smali_classes2/app/aimal/netflix/NativeProbe.smali'
helper.parent.mkdir(parents=True, exist_ok=True)
helper.write_text('''.class public final Lapp/aimal/netflix/NativeProbe;
.super Ljava/lang/Object;
.method public static load()V
    .locals 1
    :start
    const-string v0, "aimal_netflix_probe"
    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V
    :end
    .catch Ljava/lang/UnsatisfiedLinkError; {:start .. :end} :failed
    return-void
    :failed
    move-exception v0
    return-void
.end method
''', encoding='utf-8')
hooks = {
    'DefaultNrdlib.smali': 'invoke-static {v3, v4}, Lcom/getkeepsafe/relinker/ReLinker;->loadLibrary(Landroid/content/Context;Ljava/lang/String;)V',
    'BaseNrdLib.smali': 'invoke-static {v6}, Ljava/lang/System;->load(Ljava/lang/String;)V',
}
for name, marker in hooks.items():
    file = target / 'smali_classes2/com/netflix/mediaclient/service/nrdlib' / name
    body = file.read_text(encoding='utf-8')
    assert body.count(marker) == 1, 'Unexpected native loading code'
    file.write_text(body.replace(marker, marker + '\n\n    invoke-static {}, Lapp/aimal/netflix/NativeProbe;->load()V'), encoding='utf-8')
print(target)
