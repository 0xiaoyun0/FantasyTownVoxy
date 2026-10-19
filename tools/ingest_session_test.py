"""Deterministic real processor epoch/poll race, with no Minecraft process or production mutation."""
from pathlib import Path
import os, subprocess, sys
from paths import ROOT, artifact, java_home

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
sources_mode = '--sources' in sys.argv
out = ROOT / ('build/ingest-session-sources' if sources_mode else 'build/ingest-session-artifact')
out.mkdir(exist_ok=True)
jar = ROOT / 'dist/fantasy-town-voxy-0.0.3-neoforge-mc1.21.1.jar' if sources_mode else artifact()
cp = str(jar) + os.pathsep + (ROOT / 'build/compile-classpath.txt').read_text(encoding='utf-8')
sources = [*sorted((ROOT / 'tests/ingest/stubs').rglob('*.java')),
           *sorted((ROOT / 'tests/ingest/session-stubs').rglob('*.java')),
           ROOT / 'tests/ingest/ClientSessionQueueRaceTest.java']
if sources_mode:
    sources += [ROOT / 'src/main/java' / name for name in (
        'me/cortex/voxy/hxz/core/LodIngestBudget.java',
        'dev/xantha/vss/networking/client/ClientLodBackpressure.java',
        'dev/xantha/vss/networking/client/ClientColumnProcessor.java')]
logging = out / 'log4j.xml'
logging.write_text('<Configuration><Appenders><Console name="Console" target="SYSTEM_ERR"><PatternLayout pattern="%p %m%n"/></Console></Appenders><Loggers><Root level="warn"><AppenderRef ref="Console"/></Root></Loggers></Configuration>', encoding='utf-8')
java = Path(java_home()) / 'bin'
for tool, args in (
    ('javac', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(out), *map(str, sources)]),
    ('java', ['-ea', '-Dlog4j.configurationFile=' + logging.as_uri(), '-cp', str(out) + os.pathsep + cp,
              'dev.xantha.vss.networking.client.ClientSessionQueueRaceTest'])):
    argfile = out / (tool + '.args')
    argfile.write_text('\n'.join('"' + s.replace('\\', '/') + '"' for s in args), encoding='utf-8' if tool == 'javac' else 'gbk')
    result = subprocess.run([str(java / (tool + '.exe'))] + (['-J-Dfile.encoding=UTF-8', '-J-Duser.language=en'] if tool == 'javac' else []) + ['@' + str(argfile)], capture_output=True)
    output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
    print(output)
    (out / (tool + '.log')).write_text(output, encoding='utf-8')
    if result.returncode: raise SystemExit(result.returncode)
