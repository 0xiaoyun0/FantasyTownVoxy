from paths import java_home,setting
"""Fast reconnect regression using actual namespace/world IDs and a closed RocksDB."""
from pathlib import Path
import os, subprocess, sys
R=Path(__file__).resolve().parents[1]
java=Path(java_home())/'bin'
out=R/'build/persistence-test';out.mkdir(exist_ok=True)
jar=Path(os.environ.get('HXZ_TEST_JAR',str(R/'dist/fantasy-town-voxy-0.0.1-neoforge-mc1.21.1.jar')))
cp=str(jar)+os.pathsep+(R/'build/compile-classpath.txt').read_text()
for tool,args in [('javac',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out),str(R/'tests/server/PersistenceTest.java')]),('java',['-ea','-cp',str(out)+os.pathsep+cp,'PersistenceTest',str(out)])]:
    argfile=out/(tool+'.args');argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf8' if tool=='javac' else 'gbk')
    result=subprocess.run([str(java/(tool+'.exe'))]+(['-J-Dfile.encoding=UTF-8'] if tool=='javac' else [])+['@'+str(argfile)],capture_output=True)
    output=(result.stdout+result.stderr).decode('utf8',errors='replace');print(output)
    (out/(tool+'.log')).write_text(output,encoding='utf8')
    if result.returncode:sys.exit(result.returncode)
