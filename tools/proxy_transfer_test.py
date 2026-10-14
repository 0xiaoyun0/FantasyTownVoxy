from paths import artifact, VERSION
from paths import java_home,setting
"""Private Velocity + two NeoVelocity backends, using one duplicated fixture world."""
from pathlib import Path
import os,sys,json,uuid,subprocess,threading,socket,shutil,time,hashlib
R=Path(__file__).resolve().parents[1]
S=Path(setting('HXZ_NEOFORGE_SERVER'))
java_home()
java=Path(java_home())/'bin/java.exe'
jar=artifact()
work=R/'build'/('proxy-test-'+uuid.uuid4().hex[:8]);work.mkdir()
secret=uuid.uuid4().hex+uuid.uuid4().hex
processes=[];logs=[]
def port():
    with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
ports={name:port() for name in ('alpha','beta','proxy')}
def launch(name,args,ready_text):
    wd=work/name;log=(wd/'console.log').open('w',encoding='utf-8');logs.append(log)
    p=subprocess.Popen([str(java),*args],cwd=wd,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW)
    processes.append((p,name));ready=threading.Event()
    def read():
        for line in p.stdout:
            log.write(line);log.flush()
            if ready_text in line:ready.set()
    threading.Thread(target=read,daemon=True).start()
    deadline=time.monotonic()+120
    while not ready.wait(.25):
        if p.poll() is not None or time.monotonic()>deadline:raise RuntimeError(name+' failed startup; inspect '+str(wd/'console.log'))
    print('Private '+name+' ready',flush=True)
    return p
try:
    for name in ('alpha','beta'):
        wd=work/name;(wd/'mods').mkdir(parents=True);(wd/'config').mkdir()
        shutil.copy2(S/'eula.txt',wd/'eula.txt');shutil.copy2(jar,wd/'mods'/jar.name)
        nv=next((S/'mods').glob('*neovelocity*'));shutil.copy2(nv,wd/'mods'/nv.name)
        from rtf_fixture import prepare_rtf_world
        prepare_rtf_world(wd/'world','default')
        for mod in (Path(setting('HXZ_CLIENT'))/'mods').glob('*.jar'):
            if any(k in mod.name.lower() for k in ('neoterraforged','terrablender')):shutil.copy2(mod,wd/'mods'/mod.name)
        (wd/'config/neovelocity-common.toml').write_text('[forwarding]\nforwarding-secret = "'+secret+'"\nforwarding-secret-type = "IN_LINE"\nlogin-custom-packet-catchall = true\n',encoding='utf-8')
        c=json.loads((R/'src/main/resources/hxz-defaults/vss-server-config.json').read_text());c.update(serverIdentity='1234ABCD',nodeIdentity=name,enablePredictionSync=False)
        (wd/'config/vss-server-config.json').write_text(json.dumps(c),encoding='utf-8')
        (wd/'config/voxy-hxz-server.json').write_text('{}',encoding='utf-8')
        (wd/'server.properties').write_text(f'server-ip=127.0.0.1\nserver-port={ports[name]}\nonline-mode=false\nlevel-name=world\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\ngenerate-structures=false\n',encoding='ascii')
        args=next((S/'libraries/net/neoforged/neoforge').glob('*/win_args.txt')).read_text().replace('libraries/',(S/'libraries').as_posix()+'/').replace('-DlibraryDirectory=libraries','-DlibraryDirectory='+(S/'libraries').as_posix())
        (wd/'launch-args.txt').write_text(args,encoding='gbk')
        launch(name,['-Xms256M','-Xmx3072M','-Dfile.encoding=UTF-8','@launch-args.txt','nogui'],'Done (')
    wd=work/'proxy';wd.mkdir();shutil.copy2(R/'build/proxy-download/velocity.jar',wd/'velocity.jar');(wd/'forwarding.secret').write_text(secret)
    (wd/'velocity.toml').write_text(f'''config-version = "2.7"
bind = "127.0.0.1:{ports['proxy']}"
motd = "FantasyTownVoxy isolated regression"
show-max-players = 5
online-mode = false
force-key-authentication = false
player-info-forwarding-mode = "modern"
forwarding-secret-file = "forwarding.secret"
announce-forge = true
ping-passthrough = "DISABLED"
[servers]
alpha = "127.0.0.1:{ports['alpha']}"
beta = "127.0.0.1:{ports['beta']}"
try = ["alpha"]
[forced-hosts]
"lobby.example.com" = ["alpha"]
"factions.example.com" = ["alpha"]
"minigames.example.com" = ["alpha"]
[advanced]
login-ratelimit = 0
log-player-connections = true
''',encoding='utf-8')
    launch('proxy',['-Xms128M','-Xmx512M','-Dfile.encoding=UTF-8','-jar','velocity.jar'],'Done (')
    result=subprocess.run([sys.executable,str(R/'tools/client_smoke.py'),'--connect','127.0.0.1:'+str(ports['proxy']),'--proxy-test','--xaero','--expect-sharing'],cwd=R)
    if result.returncode:raise RuntimeError('Real proxy transfer failed; logs: '+str(work))
    log=(R/'build/client-smoke-proxy/console.log').read_text(encoding='utf-8',errors='replace');assert 'HXZ_VELOCITY PASS' in log
    dest=R/'build/validation';dest.mkdir(exist_ok=True)
    shutil.copy2(R/'build/client-smoke-proxy/console.log',dest/'velocity-transfer-client.log')
    (dest/'VELOCITY-PASS.json').write_text(json.dumps({'allChecksPassed':True,'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'scenario':'real Velocity 3.4.0 + NeoVelocity 1.2.6, identical backend world snapshots, same proxy endpoint alpha/beta/alpha; real Xaero 1.40.16 and minimap 25.3.13 roots and saved config'},indent=2))
    print('PASS real Velocity same-seed A/B/A; logs '+str(work),flush=True)
finally:
    for p,name in reversed(processes):
        if p.poll() is None:
            p.stdin.write(('shutdown' if name=='proxy' else 'stop')+'\n');p.stdin.flush()
    for p,name in processes:
        try:p.wait(timeout=60)
        except subprocess.TimeoutExpired:p.terminate();p.wait(timeout=15)
    for log in logs:log.close()
