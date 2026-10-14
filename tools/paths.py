"""Machine paths are environment variables or an ignored build-local.json file."""
from pathlib import Path
import json, os

ROOT = Path(__file__).resolve().parents[1]
VERSION = '0.0.2'
_file = ROOT / 'build-local.json'
_local = json.loads(_file.read_text(encoding='utf-8')) if _file.exists() else {}

def setting(key, default=None):
    value = os.environ.get(key) or _local.get(key) or default
    if value is None:
        raise SystemExit('Set '+key+' in the environment or build-local.json; see BUILDING.md')
    return str(value)

def java_home():
    value = setting('JAVA_HOME')
    os.environ.setdefault('JAVA_HOME', value)
    return value

def artifact():
    return Path(os.environ.get('HXZ_TEST_JAR',str(ROOT / 'dist' / ('fantasy-town-voxy-'+VERSION+'-neoforge-mc1.21.1.jar'))))
