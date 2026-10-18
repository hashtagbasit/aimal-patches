"""Keep Gradle logs under the compact-output wrapper, including in Actions."""
import json
from pathlib import Path
import subprocess
import sys

wrapper = str(Path(__file__).with_name("gradle_run.py"))
base = [sys.executable, wrapper]
for bundle in (Path(__file__).resolve().parents[2] / "patches/build/libs").glob("patches-*.mpp"):
    bundle.unlink()
created = subprocess.run(base + ["create"], text=True, capture_output=True, check=True)
workflow = json.loads(created.stdout)["workflow"]
print(created.stdout, flush=True)
try:
    completed = subprocess.run(base + [
        "run", "--workflow", workflow, "--scope", "targeted",
        "--question", "Does the subtitle bundle compile and produce Morphe patch metadata?",
        "--", "./gradlew", ":patches:buildAndroid", ":patches:generatePatchesList",
    ])
finally:
    subprocess.run(base + ["finish", "--workflow", workflow], check=True)
sys.exit(completed.returncode)
