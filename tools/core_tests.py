from paths import java_home,setting
from pathlib import Path
import subprocess,os
R=Path(__file__).resolve().parents[1]
java=Path(java_home())/'bin'
out=R/'build/tests';out.mkdir(exist_ok=True)
tests=['CoreTest','DownloadPolicyTest','DownloadFairnessTest']
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-cp',str(R/'build/classes'),'-d',str(out),*[str(R/'tests'/(t+'.java')) for t in tests]],check=True)
for test in tests:subprocess.run([str(java/'java.exe'),'-ea','-cp',str(out)+os.pathsep+str(R/'build/classes'),test],check=True)
