from paths import artifact, VERSION
from paths import java_home,setting
"""Launch only the integrated mod in a private loopback-only test server."""
from pathlib import Path
import os, shutil, subprocess, time, threading, sys, socket, json, uuid, zipfile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
ROOT=Path(__file__).resolve().parents[1]
SERVER=Path(setting('HXZ_NEOFORGE_SERVER'))
CLIENT='--client' in sys.argv
PLAIN='--plain' in sys.argv
RTF=next((x.split('=',1)[1] for x in sys.argv if x.startswith('--rtf=')),'default')
WORK=ROOT/('build/server-smoke-rtf-'+RTF+('-plain' if PLAIN else ''))
WORK.mkdir(parents=True,exist_ok=True)
assert (ROOT/'build').resolve() in WORK.resolve().parents
assert 'eula=true' in (SERVER/'eula.txt').read_text(), 'Existing installation must have accepted the Minecraft EULA'
shutil.copy2(SERVER/'eula.txt',WORK/'eula.txt')
mods=WORK/'mods'; mods.mkdir(exist_ok=True)
jar=artifact()
for old in [p for p in mods.glob('*.jar') if p.name.startswith(('voxy-hxz-','fantasy-town-voxy-'))]:
    assert (ROOT/'build').resolve() in old.resolve().parents
    if old.name!=jar.name:old.unlink()
probe_jar=mods/'hxz-safety-probe.jar'
if probe_jar.exists():probe_jar.unlink()
if not PLAIN: shutil.copy2(jar,mods/jar.name)
terrain_mods=[p for p in (Path(setting('HXZ_CLIENT'))/'mods').glob('*.jar') if any(k in p.name.lower() for k in ('neoterraforged','terrablender'))]
assert len(terrain_mods)==2, 'Native RTF and TerraBlender are required for every server fixture'
for mod in terrain_mods:shutil.copy2(mod,mods/mod.name)
assert len(list(mods.glob('*.jar')))==(2 if PLAIN else 3), 'Unexpected mods in smoke environment'
from rtf_fixture import prepare_rtf_world
prepare_rtf_world(WORK/'hxz-smoke-world',RTF)
if '--flood' in sys.argv or '--features' in sys.argv:
    probe=ROOT/'build/server-probe';probe.mkdir(exist_ok=True)
    for p in probe.rglob('*.class'):p.unlink()
    cp=str(jar)+os.pathsep+(ROOT/'build/compile-classpath.txt').read_text(encoding='utf-8')
    subprocess.run([str(Path(java_home())/'bin/javac.exe'),'-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(probe),*map(str,(ROOT/'tests/server/probe').glob('*.java'))],check=True)
    with zipfile.ZipFile(probe_jar,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="hxz_safety_probe"\nversion="1"\n[[mods]]\nmodId="hxz_feature_probe"\nversion="1"\n[[mixins]]\nconfig="hxz-safety-probe.mixins.json"\n')
        z.writestr('hxz-safety-probe.mixins.json',json.dumps({'required':True,'package':'hxz.safetyprobe.mixin','compatibilityLevel':'JAVA_21','mixins':['ColumnProbeMixin']}))
        for p in probe.rglob('*.class'):z.write(p,p.relative_to(probe).as_posix())
if not PLAIN:
    config=WORK/'config'; config.mkdir(exist_ok=True)
    fixture_config=json.loads((ROOT/'src/main/resources/hxz-defaults/vss-server-config.json').read_text())
    fixture_config.update(nodeIdentity='smoke-'+uuid.uuid4().hex[:8],debugLogging='--flood' not in sys.argv,enabled='--sharing-disabled' not in sys.argv)
    if '--unsafe-config' in sys.argv:fixture_config.update(diskReadQueueLimit=4096,diskReaderThreads=16,nbtReadQueueLimit=1024,sendPacketsPerTick=1024,enableChunkGeneration=True,generationConcurrencyLimitGlobal=64,generationPackingThreads=12)
    (config/'vss-server-config.json').write_text(json.dumps(fixture_config),encoding='utf-8')
    # Keep each fixture repeatable; only this isolated directory is changed.
    (config/'voxy-hxz-server.json').write_text('{}',encoding='utf-8')
    if '--downloads' in sys.argv:
        (config/'voxy-hxz-server.json').write_text(json.dumps({'downloadScheduleEnabled':True,'downloadBlockStartHour':0,'downloadBlockEndHour':0}),encoding='utf-8')
args=next((SERVER/'libraries/net/neoforged/neoforge').glob('*/win_args.txt')).read_text()
args=args.replace('libraries/',(SERVER/'libraries').as_posix()+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+(SERVER/'libraries').as_posix())
# java.exe reads @argfiles using the Windows native code page, before JVM properties.
(WORK/'launch-args.txt').write_text(args,encoding='gbk' if os.name=='nt' else 'utf-8')
with socket.socket() as reservation:
    reservation.bind(('127.0.0.1',0)); port=reservation.getsockname()[1]
(WORK/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={port}\nonline-mode=false\nlevel-name=hxz-smoke-world\nlevel-type=minecraft:normal\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nsync-chunk-writes=false\nenable-query=false\nenable-rcon=false\n',encoding='ascii')
java=Path(java_home())/'bin/java.exe'
done=False
client_ok=not CLIENT
lines=[]
client_worker=None
with (WORK/'console.log').open('w',encoding='utf-8') as log:
    process=subprocess.Popen([str(java),'-Xms256M','-Xmx3072M','-Dfile.encoding=UTF-8','@launch-args.txt','nogui'],cwd=WORK,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace')
    def consume():
        global done, client_ok, client_worker
        for line in process.stdout:
            log.write(line); log.flush(); lines.append(line)
            if ('--admin-toggle' in sys.argv or '--downloads' in sys.argv) and 'HxzSmoke joined the game' in line:
                process.stdin.write('op HxzSmoke\n');process.stdin.flush()
            if 'Done (' in line:
                done=True
                if CLIENT:
                    print('Server ready; testing real client connection (server HXZ '+('absent' if PLAIN else 'installed')+')',flush=True)
                    command=[sys.executable,str(ROOT/'tools/client_smoke.py'),'--connect','127.0.0.1:'+str(port)]
                    if '--without-hxz' in sys.argv: command+=['--without-hxz']
                    if '--original' in sys.argv:command+=['--original']
                    if '--admin-toggle' in sys.argv:command+=['--admin-toggle']
                    if '--without-iris' in sys.argv:command+=['--without-iris']
                    if '--flood' in sys.argv:command+=['--flood']
                    if '--features' in sys.argv:command+=['--features']
                    if '--downloads' in sys.argv:command+=['--downloads']
                    if '--xaero' in sys.argv:command+=['--xaero']
                    if not PLAIN and '--sharing-disabled' not in sys.argv: command+=['--expect-sharing']
                    def run_client():
                        global client_ok
                        try:
                            # Reused fixtures must not leave the test player dead or awaiting respawn.
                            process.stdin.write('difficulty peaceful\ngamerule doMobSpawning false\ngamerule doImmediateRespawn true\ntime set noon\n'); process.stdin.flush()
                            if not PLAIN:
                                process.stdin.write('voxyhxzserver prediction off\nvoxyhxzserver status\nvoxyhxzserver prediction on\n');process.stdin.flush()
                                # Seed a small, saved area outside the client's vanilla view.
                                process.stdin.write('forceload add 0 128 63 191\n'); process.stdin.flush()
                                time.sleep(2)
                                process.stdin.write('save-all flush\nforceload remove all\n'); process.stdin.flush()
                                time.sleep(2)
                            if '--persistence' in sys.argv:
                                tag='plain' if PLAIN else 'shared'
                                validation=ROOT/'build/validation';validation.mkdir(exist_ok=True)
                                client_dir=ROOT/('build/client-smoke-persistence-'+tag)
                                for mode in ('write','read'):
                                    result=subprocess.run(command+['--persistence='+mode],cwd=ROOT)
                                    for name in ('console.log','result.txt'):
                                        if (client_dir/name).exists():shutil.copy2(client_dir/name,validation/('persistence-'+tag+'-'+mode+'-'+name))
                                    client_ok=result.returncode==0
                                    if not client_ok:break
                            else:
                                result=subprocess.run(command,cwd=ROOT)
                                client_ok=result.returncode==0
                        finally:
                            if process.poll() is None:
                                process.stdin.write('stop\n'); process.stdin.flush()
                    client_worker=threading.Thread(target=run_client,daemon=True)
                    client_worker.start()
                else:
                    process.stdin.write('stop\n'); process.stdin.flush()
    reader=threading.Thread(target=consume,daemon=True); reader.start()
    try: process.wait(timeout=700 if '--persistence' in sys.argv else 420 if CLIENT else 180)
    except subprocess.TimeoutExpired:
        process.terminate(); process.wait(timeout=15)
    reader.join(timeout=5)
    if client_worker is not None: client_worker.join(timeout=5)
text=''.join(lines)
if '--flood' in sys.argv and 'HXZ_FLOOD PASS' not in text:
    print('\n'.join(l for l in lines if 'HXZ_FLOOD' in l));client_ok=False
if '--flood' in sys.argv and 'HXZ_RECOVERY PASS' not in text:client_ok=False
if '--flood' in sys.argv and 'HXZ_PRESSURE PASS' not in text:client_ok=False
if '--features' in sys.argv and 'HXZ_TALL PASS' not in text:client_ok=False
if '--features' in sys.argv and 'HXZ_RELOAD PASS' not in text:client_ok=False
if '--features' in sys.argv and 'HXZ_PROFILE PASS' not in text:client_ok=False
if not done or not client_ok or process.returncode!=0:
    print(''.join(lines[-95:]))
    raise SystemExit('FAIL isolated native server startup: '+str(process.returncode))
if not PLAIN: assert (WORK/'config/vss-server-config.json').exists(), 'Unified entrypoint did not install defaults'
print('PASS '+('client joined server with HXZ '+('absent' if PLAIN else 'installed') if CLIENT else 'one-JAR NeoForge server startup')+' and clean shutdown')
print('Log: '+str(WORK/'console.log'))
validation=ROOT/'build/validation';validation.mkdir(exist_ok=True)
label='-'.join(x[2:] for x in sys.argv[1:]) or 'server-only'
shutil.copy2(WORK/'console.log',validation/(label+'-server.log'))
client_dir=ROOT/('build/client-smoke-unmodded' if '--without-hxz' in sys.argv else 'build/client-smoke-minimal')
if '--without-iris' in sys.argv:client_dir=ROOT/'build/client-smoke-no-iris'
if '--persistence' in sys.argv:client_dir=ROOT/('build/client-smoke-persistence-'+('plain' if PLAIN else 'shared'))
if CLIENT:
    shutil.copy2(client_dir/'console.log',validation/(label+'-client.log'))
    shutil.copy2(client_dir/'result.txt',validation/(label+'-result.txt'))
import hashlib
(validation/(label+'-jar.sha256')).write_text(hashlib.sha256(jar.read_bytes()).hexdigest())
