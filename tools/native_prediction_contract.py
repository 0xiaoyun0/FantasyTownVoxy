"""Exercise transformed prediction contracts in the exact native NeoForge artifact."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys
from paths import artifact, NEOFORGE_VERSION

R = Path(__file__).resolve().parents[1]
V = R / 'build/validation'
V.mkdir(exist_ok=True)
digest = hashlib.sha256(artifact().read_bytes()).hexdigest()
record = V / 'PREDICTION-CONTRACT-PASS.json'
if '--reuse-passed' in sys.argv and record.exists():
    prior = json.loads(record.read_text(encoding='utf-8'))
    if prior.get('allChecksPassed') and prior.get('sha256') == digest:
        print('REUSE exact artifact native prediction contracts ' + digest)
        raise SystemExit(0)
subprocess.run([sys.executable, 'tools/client_smoke.py', '--singleplayer',
                '--rtf=custom', '--quality-first-contract'], cwd=R, check=True)
work = R / 'build/client-smoke-minimal'
launch = json.loads((work / 'launch-record.json').read_text(encoding='utf-8'))
assert launch['jarSha256'] == digest and launch['neoforge'] == NEOFORGE_VERSION
log = (work / 'console.log').read_text(encoding='utf-8', errors='replace')
markers = ['HXZ_QUALITY_FIRST PASS actual snapshot', 'HXZ_PRIORITY PASS actual manager',
           'HXZ_PARENTLESS PASS actual GPU-residency snapshot']
markers += ['HXZ_PRESSURE PASS axis=' + str(axis) for axis in (64, 32, 16)]
for marker in markers:
    assert marker in log, marker
assert hashlib.sha256(artifact().read_bytes()).hexdigest() == digest
shutil.copy2(work / 'console.log', V / 'native-prediction-contract.log')
record.write_text(json.dumps({'sha256': digest, 'neoforge': NEOFORGE_VERSION,
    'allChecksPassed': True, 'markers': markers,
    'scope': 'actual transformed prediction manager, quality snapshot, GPU ownership and adaptive-budget pressure release; fine/coarse retirement and independent opt-out'}, indent=2), encoding='utf-8')
print('PASS native prediction contracts ' + digest)
