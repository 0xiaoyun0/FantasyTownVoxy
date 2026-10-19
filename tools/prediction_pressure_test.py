"""Run the real prediction manager's adaptive-memory recovery contract, without Minecraft."""
from pathlib import Path
import os, subprocess, sys
from paths import ROOT, artifact, java_home

sys.stdout.reconfigure(encoding='utf-8',errors='replace')
out=ROOT/'build/prediction-pressure-test';out.mkdir(exist_ok=True)
cp=str(artifact())+os.pathsep+(ROOT/'build/compile-classpath.txt').read_text(encoding='utf-8')
sources=[*sorted((ROOT/'tests/prediction/stubs').rglob('*.java')),ROOT/'tests/prediction/PredictionPressureReclaimTest.java']
sources += [ROOT/'tests/prediction/PressureRedirectFixture.java',ROOT/'src/main/java/dev/xantha/vss/client/prediction/PredictionPressureRetirement.java']
java=Path(java_home())/'bin'
transformed=out/'dev/xantha/vss/client/prediction/PredictionTileManager.class'
if transformed.exists():transformed.unlink()
commands=[('javac',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out),*[str(p) for p in sources]])]
if '--baseline' not in sys.argv:commands.append(('java',['-cp',str(out)+os.pathsep+cp,'PressureRedirectFixture',str(artifact()),str(out)]))
logging=out/'log4j2.xml'
logging.write_text('<Configuration status="ERROR"><Appenders><Console name="stderr" target="SYSTEM_ERR"><PatternLayout pattern="%level %msg%n"/></Console></Appenders><Loggers><Root level="warn"><AppenderRef ref="stderr"/></Root></Loggers></Configuration>',encoding='utf-8')
commands.append(('java',['-Dlog4j2.configurationFile='+logging.as_uri(),'-ea','-cp',str(out)+os.pathsep+cp,'dev.xantha.vss.client.prediction.PredictionPressureReclaimTest']))
for tool,args in commands:
    af=out/(tool+'.args');af.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf-8' if tool=='javac' else 'gbk')
    result=subprocess.run([str(java/(tool+'.exe'))]+(['-J-Dfile.encoding=UTF-8','-J-Duser.language=en'] if tool=='javac' else ['-Dfile.encoding=UTF-8'])+['@'+str(af)],capture_output=True)
    output=(result.stdout+result.stderr).decode('utf-8',errors='replace');print(output)
    if tool=='java':(ROOT/('build/validation/prediction-pressure-baseline.log' if '--baseline' in sys.argv else 'build/validation/prediction-pressure.log')).write_text(output,encoding='utf-8')
    if result.returncode:raise SystemExit(result.returncode)
