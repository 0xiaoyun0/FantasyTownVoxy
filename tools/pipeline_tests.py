"""Run real VSS disk pipeline regressions without starting Minecraft or OpenGL.

--baseline loads the immutable 0.0.3 release, otherwise compile only the changed
runtime overlays into an isolated directory before running against that release.
"""
from pathlib import Path
import os, subprocess, sys
from paths import ROOT, java_home, artifact

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
baseline = artifact() if '--artifact' in sys.argv else ROOT / 'dist/fantasy-town-voxy-0.0.3-neoforge-mc1.21.1.jar'
out = ROOT / 'build/pipeline-tests'
out.mkdir(exist_ok=True)
java = Path(java_home()) / 'bin'
baseline_cp = str(baseline) + os.pathsep + (ROOT / 'build/compile-classpath.txt').read_text(encoding='utf-8')
overlays = [ROOT / 'src/main/java/dev/xantha/vss/networking/server/runtime' / name for name in
            ('TrackedTaskExecutor.java', 'DiskTaskRuntime.java')]
binary = '--baseline' in sys.argv or '--artifact' in sys.argv
overlays = [path for path in overlays if path.exists()] if not binary else []
tests = [ROOT / 'tests/server' / name for name in ('ReadContinuationTest.java', 'GateRejectionTest.java', 'ExecutorOverloadTest.java', 'SubmissionErrorTest.java')]
logconfig = out / 'log4j.xml'
logconfig.write_text('<Configuration><Appenders><Console name="Console" target="SYSTEM_ERR">'
                    '<PatternLayout pattern="%p %m%n"/></Console></Appenders>'
                    '<Loggers><Root level="warn"><AppenderRef ref="Console"/></Root></Loggers></Configuration>', encoding='utf-8')

def run(tool, args, label=None):
    argfile = out / (tool + '.args')
    argfile.write_text('\n'.join('"' + str(s).replace('\\', '/') + '"' for s in args),
                       encoding='utf-8' if tool == 'javac' else 'gbk')
    proc = subprocess.run([str(java / (tool + '.exe'))]
                          + (['-J-Dfile.encoding=UTF-8', '-J-Duser.language=en'] if tool == 'javac' else [])
                          + ['@' + str(argfile)], capture_output=True)
    result = (proc.stdout + proc.stderr).decode('utf-8', errors='replace')
    print(result)
    prefix = 'baseline-' if '--baseline' in sys.argv else 'artifact-' if '--artifact' in sys.argv else ''
    (out / (prefix + (label or tool) + '.log')).write_text(result, encoding='utf-8')
    if proc.returncode: sys.exit(proc.returncode)

run('javac', ['-encoding', 'UTF-8', '-proc:none', '-cp', baseline_cp, '-d', out] + overlays + tests)
test_cp = str(out) + os.pathsep + baseline_cp
# The baseline must come before separately compiled production overlays, while test classes are unique.
if binary: test_cp = baseline_cp + os.pathsep + str(out)
for name in ('SubmissionErrorTest', 'ReadContinuationTest', 'GateRejectionTest', 'ExecutorOverloadTest'):
    run('java', ['-ea', '-Xss512k', '-Dlog4j.configurationFile=' + logconfig.as_uri(), '-cp', test_cp,
                'dev.xantha.vss.networking.server.runtime.' + name], name)
