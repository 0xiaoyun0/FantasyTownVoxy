"""Exercise actual VSS poll and Voxy pending-job signal without starting Minecraft."""
from pathlib import Path
import os, subprocess, sys
from paths import ROOT, artifact, java_home

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
baseline='--baseline' in sys.argv
jar=ROOT/'dist/fantasy-town-voxy-0.0.3-neoforge-mc1.21.1.jar' if baseline else artifact()
source_mode='--sources' in sys.argv
out=ROOT/('build/ingest-backpressure-baseline' if baseline else 'build/ingest-backpressure-sources' if source_mode else 'build/ingest-backpressure-tests');out.mkdir(exist_ok=True)
cp=str(jar)+os.pathsep+(ROOT/'build/compile-classpath.txt').read_text(encoding='utf-8')
if '--classes' in sys.argv:cp=str(ROOT/'build/classes')+os.pathsep+cp
sources=[*sorted((ROOT/'tests/ingest/stubs').rglob('*.java')),ROOT/'tests/ingest/ClientIngestBackpressureTest.java']
if source_mode:
    sources += [ROOT/'src/main/java/me/cortex/voxy/hxz/core/LodIngestBudget.java',
                ROOT/'src/main/java/dev/xantha/vss/networking/client/ClientLodBackpressure.java',
                ROOT/'src/main/java/dev/xantha/vss/networking/client/ClientColumnProcessor.java']
if not baseline:sources.append(ROOT/'tests/ingest/LodIngestBudgetTest.java')
java=Path(java_home())/'bin'
logs=ROOT/'build/validation';logs.mkdir(exist_ok=True)
logging=out/'log4j2.xml'
logging.write_text('<Configuration status="ERROR"><Appenders><Console name="stderr" target="SYSTEM_ERR"><PatternLayout pattern="%level %msg%n"/></Console></Appenders><Loggers><Root level="warn"><AppenderRef ref="stderr"/></Root></Loggers></Configuration>',encoding='utf-8')
logging_flag='-Dlog4j2.configurationFile='+logging.as_uri()
for tool,args in [('javac',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out),*[str(p) for p in sources]]),
                  ('java',[logging_flag,'-ea','-cp',str(out)+os.pathsep+cp,'dev.xantha.vss.networking.client.ClientIngestBackpressureTest'])]:
    af=out/(tool+'.args');af.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf-8' if tool=='javac' else 'gbk')
    result=subprocess.run([str(java/(tool+'.exe')),'-J-Dfile.encoding=UTF-8' if tool=='javac' else '-Dfile.encoding=UTF-8','@'+str(af)],capture_output=True)
    output=(result.stdout+result.stderr).decode('utf-8',errors='replace');print(output)
    if tool=='java':(logs/('ingest-backpressure-baseline.log' if baseline else 'ingest-backpressure.log')).write_text(output,encoding='utf-8')
    if result.returncode:raise SystemExit(result.returncode)
if not baseline:
    args=['-ea','-cp',str(out)+os.pathsep+cp,'LodIngestBudgetTest']
    af=out/'policy.args';af.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='gbk')
    result=subprocess.run([str(java/'java.exe'),'-Dfile.encoding=UTF-8','@'+str(af)],capture_output=True)
    output=(result.stdout+result.stderr).decode('utf-8',errors='replace');print(output)
    with (logs/'ingest-backpressure.log').open('a',encoding='utf-8') as log:log.write(output)
    if result.returncode:raise SystemExit(result.returncode)
