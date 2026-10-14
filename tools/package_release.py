"""Package only an artifact with matching native and full-modpack verification records."""
from pathlib import Path
import hashlib,json,shutil
R=Path(__file__).resolve().parents[1]
jar=R/'dist/fantasy-town-voxy-0.0.1-neoforge-mc1.21.1.jar'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
manifest=json.loads((R/'dist/build-manifest.json').read_text())
native=json.loads((R/'build/validation/FINAL-PASS.json').read_text())
full=json.loads((R/'build/validation/FULLPACK-PASS.json').read_text())
assert native['allChecksPassed'] and full['allChecksPassed']
assert native['sha256']==full['sha256']==manifest['jarSha256']==digest
proxy=json.loads((R/'build/validation/VELOCITY-PASS.json').read_text(encoding='utf-8'))
assert proxy['allChecksPassed'] and proxy['sha256']==digest
prediction=json.loads((R/'build/validation/PREDICTION-PASS.json').read_text(encoding='utf-8'))
assert prediction['allChecksPassed'] and prediction['sha256']==digest
assert manifest['version']=='0.0.1' and manifest['build']=='20261014-release1'
release=R.parent/'FantasyTownVoxy-0.0.1';release.mkdir(exist_ok=True)
for path in [jar,R/'dist/build-manifest.json',R/'dist/SHA256SUMS.txt']:
    shutil.copy2(path,release/path.name)
for name in ['README.md','BUILDING.md','QUICKSTART.zh-CN.md','README_HXZ.zh-CN.md','SETTINGS.zh-CN.md','SERVER_SAFETY.zh-CN.md','THIRD_PARTY_NOTICES.md','VALIDATION.md','PREDICTION_ACCURACY.zh-CN.md','CHANGELOG.md','RELEASE_NOTES.zh-CN.md']:
    shutil.copy2(R/name,release/name)
for path in (R/'build/client-smoke-minimal/screenshots').glob('fantasy-town-voxy-*.png'):
    shutil.copy2(path,release/path.name)
shutil.copy2(R/'build/validation/fantasy-town-voxy-performance.png',release/'fantasy-town-voxy-performance.png')
(release/'validation-summary.json').write_text(json.dumps({'build':manifest['build'],'native':native,'modpackWorld':full,'velocity':proxy,'prediction':prediction},indent=2),encoding='utf-8')
assert len(list(release.glob('*.jar')))==1
assert hashlib.sha256((release/jar.name).read_bytes()).hexdigest()==digest
print('PACKAGED '+str(release))
print('SHA256 '+digest)
