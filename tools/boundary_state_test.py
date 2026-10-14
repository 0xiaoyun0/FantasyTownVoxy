from paths import artifact, VERSION
"""Run the actual boundary queue lifecycle, including the unmerged NeoVoxy input."""
from pathlib import Path
import os, subprocess, sys
from paths import java_home
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
R=Path(__file__).resolve().parents[1]
jar=R/'reference/neovoxy-original.jar' if '--baseline' in sys.argv else artifact()
out=R/'build/boundary-state-test';out.mkdir(exist_ok=True)
cp=str(jar)+os.pathsep+(R/'build/compile-classpath.txt').read_text(encoding='utf-8')
java=Path(java_home())/'bin'
for tool,args in [('javac',['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out),str(R/'tests/ChunkBoundaryStateTest.java')]),('java',['-ea','-cp',str(out)+os.pathsep+cp,'ChunkBoundaryStateTest'])]:
    af=out/(tool+'.args');af.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in args),encoding='utf-8' if tool=='javac' else 'gbk')
    p=subprocess.run([str(java/(tool+'.exe'))]+(['-J-Dfile.encoding=UTF-8','-J-Duser.language=en'] if tool=='javac' else ['-Dfile.encoding=UTF-8'])+['@'+str(af)],capture_output=True)
    print((p.stdout+p.stderr).decode('utf-8',errors='replace'))
    if p.returncode:raise SystemExit(p.returncode)
