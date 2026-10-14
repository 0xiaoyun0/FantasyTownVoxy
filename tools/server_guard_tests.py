from paths import java_home,setting
"""Fast real-executor regression; --baseline exercises the supplied VSS bytecode."""
from pathlib import Path
import os, subprocess, sys
R=Path(__file__).resolve().parents[1]
java=Path(java_home())/'bin'
out=R/'build/server-guard-tests';out.mkdir(exist_ok=True)
base=Path(setting('HXZ_VSS',R/'reference/vss-original.jar'))
cp=str(base) if '--baseline' in sys.argv else os.pathsep.join([str(R/'build/classes'),str(base)])
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out),str(R/'tests/server/ExecutorOverloadTest.java')],check=True)
result=subprocess.run([str(java/'java.exe'),'-ea','-cp',str(out)+os.pathsep+cp,'dev.xantha.vss.networking.server.runtime.ExecutorOverloadTest'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
output=result.stdout.decode('utf-8',errors='replace')
logs=R/'build/validation';logs.mkdir(exist_ok=True)
(logs/('server-guard-baseline.log' if '--baseline' in sys.argv else 'server-guard.log')).write_text(output,encoding='utf-8')
print(output)
if '--baseline' not in sys.argv and result.returncode==0:
    subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(out),str(R/'tests/server/FairQueueTest.java')],check=True)
    result=subprocess.run([str(java/'java.exe'),'-ea','-cp',str(out)+os.pathsep+cp,'FairQueueTest'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    text=result.stdout.decode('utf-8',errors='replace');print(text)
    with (logs/'server-guard.log').open('a',encoding='utf-8') as f:f.write(text)
sys.exit(result.returncode)
