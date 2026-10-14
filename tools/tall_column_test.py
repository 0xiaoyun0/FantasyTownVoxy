from paths import java_home,setting
from pathlib import Path
import os,sys,subprocess
sys.stdout.reconfigure(encoding='utf8',errors='replace')
R=Path(__file__).resolve().parents[1]
java=Path(java_home())/'bin'
out=R/'build/tall-column-test';out.mkdir(exist_ok=True)
cp=(R/'build/compile-classpath.txt').read_text()
if '--baseline' not in sys.argv:cp=str(R/'build/adapted')+os.pathsep+str(R/'build/classes')+os.pathsep+cp
def run(tool,args):
 f=out/(tool+'.args');f.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf8' if tool=='javac' else 'gbk')
 p=subprocess.run([str(java/(tool+'.exe'))]+(['-J-Dfile.encoding=UTF-8','-J-Duser.language=en'] if tool=='javac' else [])+['@'+str(f)],capture_output=True)
 output=(p.stdout+p.stderr).decode('utf8',errors='replace');print(output)
 (out/(tool+('-baseline' if '--baseline' in sys.argv else '')+'.log')).write_text(output,encoding='utf8')
 if p.returncode:sys.exit(p.returncode)
sources=[str(R/'tests/server/TallColumnTest.java')]
if '--baseline' not in sys.argv:sources.append(str(R/'tests/server/LodFileTest.java'))
run('javac',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out)]+sources)
run('java',['-cp',str(out)+os.pathsep+cp,'TallColumnTest'])
if '--baseline' not in sys.argv:run('java',['-cp',str(out)+os.pathsep+cp,'LodFileTest',str(out)])
