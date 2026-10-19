from paths import artifact, VERSION
from paths import java_home,setting
"""Run serial, hidden native fixtures against the final artifact, preserving logs."""
from pathlib import Path
import subprocess,sys,os,hashlib,json,zipfile,re
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
R=Path(__file__).resolve().parents[1]
java_home()
logs=R/'build/validation';logs.mkdir(exist_ok=True)
jar=artifact()
os.environ['HXZ_TEST_JAR']=str(jar)
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
with zipfile.ZipFile(jar) as z:
 assert len(z.namelist())==len(set(z.namelist()))
 assert z.testzip() is None
 meta=z.read('META-INF/neoforge.mods.toml').decode()
 assert re.findall(r'\[\[mods\]\]\s*modId="([^"]+)"',meta)==['voxy']
 assert 'dev/xantha/vss/VSSMod.class' not in z.namelist()
 assert not any(n.startswith(('hxz/smoke/','hxz/safetyprobe/')) for n in z.namelist())
 for name in re.findall(r'config="([^"]+\.mixins\.json)"',meta):
  config=json.loads(z.read(name))
  for group in ['mixins','client','server']:
   for cls in config.get(group,[]):assert (config['package']+'.'+cls).replace('.','/')+'.class' in z.namelist(),cls
 with zipfile.ZipFile(Path(setting('HXZ_VSS',R/'reference/vss-original.jar'))) as vss:
  for name in vss.namelist():
   if name.endswith('.class') and ('FreeTerraForged' in name or name.endswith('/WorldgenCodecSnapshot.class')):assert z.read(name)==vss.read(name),name
print('PASS artifact integrity, single mod, mixin entries, original RTF adapter bytecode',flush=True)
for label,args in [('core',['tools/core_tests.py']),('disk-pipeline',['tools/pipeline_tests.py','--artifact']),('ingest-backpressure',['tools/ingest_backpressure_test.py']),('ingest-timeout',['tools/ingest_timeout_test.py','--artifact']),('ingest-session',['tools/ingest_session_test.py']),('boundary',['tools/boundary_state_test.py']),('prediction-depth',['tools/prediction_depth_test.py']),('persistence',['tools/persistence_test.py']),('server-guards',['tools/server_guard_tests.py']),('tall-and-import',['tools/tall_column_test.py']),('gpu',['tools/gpu_regression.py','--jar',str(jar)]),('gpu-original',['tools/gpu_regression.py','--jar',str(jar),'--original']),('gpu-scheduled-original',['tools/gpu_regression.py','--jar',str(jar),'--scheduled-original'])]:
 result=subprocess.run([sys.executable]+args,cwd=R,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 text=result.stdout.decode('utf-8',errors='replace');(logs/(label+'.log')).write_text(text,encoding='utf-8');print(text,flush=True)
 if result.returncode:raise SystemExit('FAIL '+label)
subprocess.run([sys.executable,'tools/native_prediction_contract.py']+(['--reuse-passed'] if '--reuse-passed' in sys.argv else []),cwd=R,check=True)
scenarios=[['--client','--features'],['--client','--flood','--unsafe-config'],['--client','--admin-toggle'],['--client','--plain','--original'],['--client','--without-hxz'],['--client','--sharing-disabled','--without-iris'],['--client','--plain','--persistence','--xaero'],['--client','--persistence','--xaero'],['--client','--downloads','--xaero']]
for args in scenarios:args.append('--rtf=default')
for args in scenarios:
 label='-'.join(x[2:] for x in args)
 record=logs/(label+'-jar.sha256');result_file=logs/(label+'-result.txt')
 if '--reuse-passed' in sys.argv and record.exists() and result_file.exists() and record.read_text().strip()==digest and result_file.read_text(encoding='utf-8').startswith('PASS '):
  print('REUSE verified exact artifact '+label,flush=True);continue
 print('RUN '+label,flush=True)
 result=subprocess.run([sys.executable,'tools/server_smoke.py']+args,cwd=R,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
 text=result.stdout.decode('utf-8',errors='replace');(logs/(label+'-runner.log')).write_text(text,encoding='utf-8')
 print(text[-2200:] if result.returncode else text,flush=True)
 if result.returncode:raise SystemExit('FAIL '+label)
 assert (logs/(label+'-jar.sha256')).read_text().strip()==digest
assert hashlib.sha256(jar.read_bytes()).hexdigest()==digest
(logs/'FINAL-PASS.json').write_text(json.dumps({'sha256':digest,'allChecksPassed':True,'scenarios':len(scenarios),'scope':'isolated NeoForge client/server including actual two-JVM plain/shared LOD persistence and Xaero root reuse; live download window/byte caps and immediate Sodium Apply; tall-column crash regression, legacy import, hot reload, admin permissions, request flood and failure injection; no production launch'},indent=2))
print('PASS final native test matrix '+digest,flush=True)
