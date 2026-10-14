from paths import artifact, VERSION
from paths import java_home,setting
"""Validate the exact artifact in an isolated installed-modpack copy and actual RTF world."""
from pathlib import Path
import os,sys,subprocess,hashlib,json,shutil
R=Path(__file__).resolve().parents[1]
java_home()
jar=artifact()
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
result=subprocess.run([sys.executable,'tools/client_smoke.py','--full','--singleplayer'],cwd=R)
if result.returncode:raise SystemExit(result.returncode)
work=R/'build/client-smoke-full';validation=R/'build/validation'
text=(work/'console.log').read_text(encoding='utf-8',errors='replace')
for marker in ['HXZ_COMPAT PASS Sable','HXZ_COMPAT PASS Xaero','HXZ_OPTIMIZATION PASS actual RTF','HXZ_SMOKE PASS actual RTF custom preset']:
    assert marker in text,marker
assert hashlib.sha256((work/'mods'/jar.name).read_bytes()).hexdigest()==digest
assert hashlib.sha256(jar.read_bytes()).hexdigest()==digest
shutil.copy2(work/'console.log',validation/'fullpack-world-client.log')
shutil.copy2(work/'result.txt',validation/'fullpack-world-result.txt')
shutil.copy2(work/'screenshots/fantasy-town-voxy-performance.png',validation/'fantasy-town-voxy-performance.png')
(validation/'FULLPACK-PASS.json').write_text(json.dumps({'sha256':digest,'allChecksPassed':True,'modFiles':len(list((work/'mods').glob('*.jar'))),'scope':'isolated full modpack in actual RTF integrated world; actual Sable 2.0.3 client/server plot rejection, Xaero 1.40.16 late-producer race, RTF custom preset cache separation, native Sodium; no production launch; no long-duration gameplay certification'},indent=2))
print('PASS full modpack world checks '+digest,flush=True)
