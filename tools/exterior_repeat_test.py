"""Headless exact-iterator diagnostic; no Minecraft client, server, world, or OpenGL launch."""
from pathlib import Path
import os, subprocess, sys
from paths import ROOT, java_home, artifact

sys.stdout.reconfigure(encoding='utf-8', errors='replace')
out = ROOT / 'build/exterior-repeat'
out.mkdir(exist_ok=True)
parts = (ROOT / 'build/compile-classpath.txt').read_text(encoding='utf-8').split(os.pathsep)
# Minecraft's patched jar and NeoForge API must be from the same installed version.
patched = next(Path(part) for part in parts if Path(part).name.startswith('neoforge-')
               and part.endswith('-client.jar'))
universal = patched.with_name(patched.name.replace('-client.jar', '-universal.jar'))
assert universal.is_file(), 'Matching installed NeoForge universal jar is required'
parts = [str(universal) if Path(part).name.startswith('neoforge-') and part.endswith('-universal.jar')
         else part for part in parts]
cp = str(artifact()) + os.pathsep + os.pathsep.join(parts)
for tool, args in [
    ('javac', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', str(out), str(ROOT / 'tests/ExteriorRepeatTest.java')]),
    ('java', ['-Xmx1024M', '-cp', str(out) + os.pathsep + cp, 'dev.xantha.vss.client.prediction.ExteriorRepeatTest'])
]:
    argfile = out / (tool + '.args')
    argfile.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in args),
                       encoding='utf-8' if tool == 'javac' else 'gbk')
    proc = subprocess.run([str(Path(java_home()) / 'bin' / (tool + '.exe'))]
                          + (['-J-Duser.language=en', '-J-Dfile.encoding=UTF-8'] if tool == 'javac' else [])
                          + ['@' + str(argfile)], capture_output=True)
    output = (proc.stdout + proc.stderr).decode('utf-8', errors='replace')
    (out / (tool + '.log')).write_text(output, encoding='utf-8')
    print(output)
    if proc.returncode:
        raise SystemExit(proc.returncode)
