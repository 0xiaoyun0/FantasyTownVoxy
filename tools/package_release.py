from paths import artifact, VERSION
"""Package only an artifact with matching native and full-modpack verification records."""
from pathlib import Path
import hashlib,json,shutil
R=Path(__file__).resolve().parents[1]
jar=artifact()
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
quality=json.loads((R/'build/validation/QUALITY-PASS.json').read_text(encoding='utf-8'))
assert quality['allChecksPassed'] and quality['sha256']==digest
progress=json.loads((R/'build/validation/PREDICTION-PROGRESS-PASS.json').read_text(encoding='utf-8'))
assert progress['allChecksPassed'] and progress['sha256']==digest
contract=json.loads((R/'build/validation/PREDICTION-CONTRACT-PASS.json').read_text(encoding='utf-8'))
assert contract['allChecksPassed'] and contract['sha256']==digest
assert manifest['version']==VERSION
release=R.parent/('FantasyTownVoxy-'+VERSION);release.mkdir(exist_ok=True)
for path in [jar,R/'dist/build-manifest.json',R/'dist/SHA256SUMS.txt']:
    shutil.copy2(path,release/path.name)
for name in ['README.md','BUILDING.md','QUICKSTART.zh-CN.md','README_HXZ.zh-CN.md','SETTINGS.zh-CN.md','SERVER_SAFETY.zh-CN.md','THIRD_PARTY_NOTICES.md','VALIDATION.md','PREDICTION_ACCURACY.zh-CN.md','RENDER_QUALITY.zh-CN.md','CHANGELOG.md','RELEASE_NOTES.zh-CN.md']:
    shutil.copy2(R/name,release/name)
shutil.copytree(R/('docs/quality-'+VERSION),release/('docs/quality-'+VERSION),dirs_exist_ok=True)
shutil.copytree(R/'docs/optimization-0.3.0',release/'docs/optimization-0.3.0',dirs_exist_ok=True)
for path in (R/'build/client-smoke-minimal/screenshots').glob('fantasy-town-voxy-*.png'):
    shutil.copy2(path,release/path.name)
shutil.copy2(R/'build/validation/fantasy-town-voxy-performance.png',release/'fantasy-town-voxy-performance.png')
(release/'validation-summary.json').write_text(json.dumps({'build':manifest['build'],'native':native,'modpackWorld':full,'velocity':proxy,'prediction':prediction,'quality':quality,'predictionProgress':progress,'predictionContracts':contract},indent=2),encoding='utf-8')
assert len(list(release.glob('*.jar')))==1
assert hashlib.sha256((release/jar.name).read_bytes()).hexdigest()==digest
print('PACKAGED '+str(release))
print('SHA256 '+digest)
