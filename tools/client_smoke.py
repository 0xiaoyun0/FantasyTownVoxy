from paths import artifact, VERSION
from paths import java_home,setting
"""Launch an isolated copy using the user's installed client libraries; no account data."""
from pathlib import Path
import os, sys, json, re, shutil, subprocess, zipfile
sys.stdout.reconfigure(encoding='utf-8',errors='replace')
ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path(setting('HXZ_CLIENT'))
MC=SOURCE.parent.parent
FULL='--full' in sys.argv
NO_HXZ='--without-hxz' in sys.argv
QUALITY_VISUAL='--quality-visual' in sys.argv
QUALITY_BASELINE='--quality-baseline' in sys.argv
QUALITY_PREDICTION='--quality-prediction' in sys.argv
RTF=next((x.split('=',1)[1] for x in sys.argv if x.startswith('--rtf=')),'default')
QUALITY_LABEL=next((x.split('=',1)[1] for x in sys.argv if x.startswith('--quality-label=')),'candidate')
WORK=ROOT/('build/client-smoke-full' if FULL else 'build/client-smoke-unmodded' if NO_HXZ else 'build/client-smoke-minimal')
if '--without-iris' in sys.argv:WORK=ROOT/'build/client-smoke-no-iris'
if '--proxy-test' in sys.argv:WORK=ROOT/'build/client-smoke-proxy'
if QUALITY_VISUAL:WORK=ROOT/('build/quality-rtf-'+RTF+'-'+('baseline' if QUALITY_BASELINE else QUALITY_LABEL))
ACCURACY=next((x.split('=',1)[1] for x in sys.argv if x.startswith('--accuracy=')),None)
if ACCURACY:WORK=ROOT/('build/client-accuracy-'+ACCURACY)
PERSISTENCE=next((x.split('=',1)[1] for x in sys.argv if x.startswith('--persistence=')),None)
if PERSISTENCE:WORK=ROOT/('build/client-smoke-persistence-'+('shared' if '--expect-sharing' in sys.argv else 'plain'))
WORK.mkdir(parents=True,exist_ok=True)
assert (ROOT/'build').resolve() in WORK.resolve().parents
mods=WORK/'mods'; mods.mkdir(exist_ok=True)
for p in (SOURCE/'mods').glob('*.jar'):
    name=p.name.lower()
    if '--without-iris' in sys.argv and 'iris-neoforge-' in name:continue
    if 'voxy' in name or name.startswith('vss-'): continue
    if FULL or '--xaero' in sys.argv and 'xaero' in name or any(k in name for k in ('sodium-neoforge-','neoterraforged','terrablender','iris-neoforge-')):
        target=mods/p.name
        if not target.exists() or target.stat().st_size!=p.stat().st_size: shutil.copy2(p,target)
jar=artifact()
for old in [p for p in mods.glob('*.jar') if p.name.startswith(('voxy-hxz-','fantasy-town-voxy-'))]:
    assert (ROOT/'build').resolve() in old.resolve().parents
    if old.name!=jar.name:old.unlink()
if QUALITY_BASELINE:
    for old in mods.glob('fantasy-town-voxy-*.jar'):old.unlink()
    baseline=Path(setting('HXZ_NEOVOXY'));shutil.copy2(baseline,mods/baseline.name)
elif not NO_HXZ: shutil.copy2(jar,mods/jar.name)
config=WORK/'config'; config.mkdir(exist_ok=True)
if not FULL and not NO_HXZ:
    fixture_config=json.loads((ROOT/'src/main/resources/hxz-defaults/vss-client-config.json').read_text())
    fixture_config['debugLogging']=True
    if ACCURACY:fixture_config.update(enablePrediction=False,receiveServerLods=False,rememberTerrain=False)
    if '--proxy-test' in sys.argv:fixture_config['enablePrediction']=False
    if PERSISTENCE:
        fixture_config.update(enablePrediction=False,receiveServerLods=PERSISTENCE=='write')
    (config/'vss-client-config.json').write_text(json.dumps(fixture_config),encoding='utf-8')
    (config/'voxy-hxz-neovoxy.json').write_text(json.dumps({'originalRendering':'--original' in sys.argv,'performanceScheduling':'--original' not in sys.argv,'stablePrediction':'--legacy-prediction' not in sys.argv,'predictionStrictSampling':'--legacy-prediction' not in sys.argv,'predictionSurfaceFeatures':'--legacy-prediction' not in sys.argv}),encoding='utf-8')
    if PERSISTENCE:
        (config/'voxy-config.json').write_text(json.dumps({'enabled':True,'ingest_enabled':PERSISTENCE=='write','section_render_distance':2,'service_threads':2}),encoding='utf-8')
if FULL:
    for name in ('config','defaultconfigs','kubejs'):
        if (SOURCE/name).exists(): shutil.copytree(SOURCE/name,WORK/name,dirs_exist_ok=True)
if QUALITY_VISUAL:
    (config/'voxy-config.json').write_text(json.dumps({'enabled':True,'ingest_enabled':True,'section_render_distance':4,'service_threads':2,'render_vanilla_fog':False}),encoding='utf-8')
    # An upgrade can retain 0.0.2's smooth-preview preference; the new default must still hide coarse meshes.
    (config/'voxy-hxz-neovoxy.json').write_text(json.dumps({'originalRendering':True,'performanceScheduling':False,'smoothPredictionPreview':True}),encoding='utf-8')
    (config/'vss-client-config.json').write_text(json.dumps({'enablePrediction':QUALITY_PREDICTION,'receiveServerLods':QUALITY_PREDICTION,'predictionDistanceBlocks':2048,'predictionFineDistanceBlocks':1024,'predictionBackgroundWorkers':1,'predictionRefinementWorkers':2,'debugLogging':True}),encoding='utf-8')
    (config/'iris.properties').write_text('enableShaders=false\n',encoding='utf-8')
(config/'fml.toml').write_text('earlyWindowControl = false\n',encoding='utf-8')
(WORK/'options.txt').write_text('lang:zh_cn\nguiScale:2\ntutorialStep:none\nrenderDistance:2\nsimulationDistance:5\nmaxFps:60\nfullscreen:false\npauseOnLostFocus:false\nonboardAccessibility:false\n',encoding='utf-8')
java=Path(java_home())/'bin'
cp=str(ROOT/'build/classes')+os.pathsep+str(jar)+os.pathsep+(ROOT/'build/compile-classpath.txt').read_text(encoding='utf-8')
probe=ROOT/'build/client-probe'; probe.mkdir(parents=True,exist_ok=True)
for old_class in probe.rglob('*.class'):old_class.unlink()
subprocess.run([str(java/'javac.exe'),'-J-Duser.language=en','-J-Dfile.encoding=UTF-8','-encoding','UTF-8','-proc:none','--release','21','-cp',cp,'-d',str(probe),*map(str,(ROOT/'tests/client').glob('*.java'))],check=True)
with zipfile.ZipFile(mods/'hxz-smoke-probe.jar','w',zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="hxz_smoke_probe"\nversion="1"\ndisplayName="HXZ local test probe"\n[[mixins]]\nconfig="hxz-smoke.mixins.json"\n')
    z.writestr('hxz-smoke.mixins.json',json.dumps({'required':True,'package':'hxz.smoke.mixin','compatibilityLevel':'JAVA_21','client':['HiddenWindowMixin']+([] if NO_HXZ or QUALITY_BASELINE else ['ReceivedTerrainMixin'])}))
    for p in probe.rglob('*.class'): z.write(p,p.relative_to(probe).as_posix())
meta=json.loads((SOURCE/(SOURCE.name+'.json')).read_text(encoding='utf-8'))
def allowed(rules):
    result=not rules
    for rule in rules:
        match=True
        osrule=rule.get('os',{})
        if osrule.get('name','windows')!='windows': match=False
        if osrule.get('arch','x86_64') not in ('x86_64','amd64'): match=False
        if osrule.get('version') and not re.search(osrule['version'],'10.0'): match=False
        if rule.get('features'): match=False
        if match: result=rule['action']=='allow'
    return result
libraries=[]
for lib in meta['libraries']:
    if not allowed(lib.get('rules',[])): continue
    artifact=lib.get('downloads',{}).get('artifact')
    if not artifact: continue
    path=MC/'libraries'/artifact['path']
    if not path.exists(): raise SystemExit('Missing installed client library: '+str(path))
    # Keep the final version of each library identity, as a launcher does when merging patches.
    key=':'.join(lib['name'].split(':')[:2]+lib['name'].split(':')[3:])
    libraries=[(k,p) for k,p in libraries if k!=key]+[(key,path)]
classpath=os.pathsep.join([str(p) for _,p in libraries]+[str(SOURCE/(SOURCE.name+'.jar'))])
native=WORK/'natives'; native.mkdir(exist_ok=True)
values={'auth_player_name':'HxzSmoke','version_name':SOURCE.name,'game_directory':str(WORK),'assets_root':str(MC/'assets'),'assets_index_name':meta['assetIndex']['id'],'auth_uuid':'00000000000000000000000000000001','auth_access_token':'0','clientid':'0','auth_xuid':'0','user_type':'legacy','version_type':'HXZ local test','natives_directory':str(native),'launcher_name':'HXZSmoke','launcher_version':'1','classpath':classpath,'library_directory':str(MC/'libraries'),'classpath_separator':os.pathsep}
def expand(text):
    for k,v in values.items(): text=text.replace('${'+k+'}',v)
    assert '${' not in text,text
    return text
def flatten(items):
    result=[]
    for item in items:
        if isinstance(item,str): result.append(expand(item))
        elif allowed(item.get('rules',[])):
            value=item['value']; result.extend(expand(s) for s in (value if isinstance(value,list) else [value]))
    return result
args=['-Xms512M','-Xmx6144M' if FULL else '-Xmx3072M','-Dfile.encoding=UTF-8','-Dhxz.smoke.dir='+str(WORK),'-Dhxz.smoke.version='+VERSION]+flatten(meta['arguments']['jvm'])+[meta['mainClass']]+flatten(meta['arguments']['game'])+['--width','1280','--height','720']
if '--connect' in sys.argv:
    target=sys.argv[sys.argv.index('--connect')+1]
    args.insert(0,'-Dhxz.smoke.world=true'); args+=['--quickPlayMultiplayer',target]
    if not NO_HXZ: args.insert(0,'-Dhxz.smoke.expectedSharing='+('true' if '--expect-sharing' in sys.argv else 'false'))
if '--admin-toggle' in sys.argv:args.insert(0,'-Dhxz.smoke.adminToggle=true')
if '--flood' in sys.argv:args.insert(0,'-Dhxz.smoke.flood=true')
if '--features' in sys.argv:args.insert(0,'-Dhxz.smoke.features=true')
if '--downloads' in sys.argv:args.insert(0,'-Dhxz.smoke.downloads=true')
if '--xaero' in sys.argv:args.insert(0,'-Dhxz.smoke.xaero=true')
if '--proxy-test' in sys.argv:args.insert(0,'-Dhxz.smoke.proxy=true')
if PERSISTENCE:args.insert(0,'-Dhxz.smoke.persistence='+PERSISTENCE)
if ACCURACY:
    args.insert(0,'-Dhxz.smoke.accuracy='+ACCURACY);args.insert(0,'-Dhxz.smoke.world=true')
    args+=['--quickPlaySingleplayer','hxz-accuracy-fixture']
if '--singleplayer' in sys.argv:
    from rtf_fixture import prepare_rtf_world
    fixture=prepare_rtf_world(WORK/'saves'/'hxz-optimization-rtf',RTF)
    args.insert(0,'-Dhxz.smoke.world=true');args.insert(0,'-Dhxz.smoke.compatibility=true')
    args+=['--quickPlaySingleplayer',fixture.name]
if '--quality-first-contract' in sys.argv:args.insert(0,'-Dhxz.smoke.qualityFirstContract=true')
if QUALITY_VISUAL:
    from rtf_fixture import prepare_rtf_world
    fixture=prepare_rtf_world(WORK/'saves'/'hxz-quality-rtf',RTF)
    args.insert(0,'-Dhxz.smoke.world=true');args.insert(0,'-Dhxz.smoke.qualityVisual=true')
    if QUALITY_PREDICTION:args.insert(0,'-Dhxz.smoke.qualityPrediction=true')
    if '--smooth-contract' in sys.argv:args.insert(0,'-Dhxz.smoke.smoothContract=true')
    scene=next((x.split('=',1)[1] for x in sys.argv if x.startswith('--quality-scene=')),None)
    if scene is not None:args.insert(0,'-Dhxz.smoke.qualityScene='+str(int(scene)))
    args+=['--quickPlaySingleplayer',fixture.name]
# Use a UTF-8 JVM argfile only for ASCII paths; the launcher consumes native Windows encoding.
def quote(s): return '"'+s.replace('\\','/').replace('"','\\"')+'"'
(WORK/'launch-args.txt').write_text('\n'.join(quote(s) for s in args),encoding='gbk')
result=WORK/'result.txt'
if result.exists(): result.unlink()
print('Starting isolated '+('full' if FULL else 'minimal')+' client with '+str(len(list(mods.glob('*.jar'))))+' mod files',flush=True)
with (WORK/'console.log').open('w',encoding='utf-8') as log:
    process=subprocess.Popen([str(java/'java.exe'),'@launch-args.txt'],cwd=WORK,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    try: process.wait(timeout=1800 if ACCURACY else 720 if QUALITY_VISUAL else 300)
    except subprocess.TimeoutExpired: process.terminate(); process.wait(timeout=15)
log_text=(WORK/'console.log').read_text(encoding='utf-8',errors='replace')
quality_error=QUALITY_VISUAL and any(message in log_text for message in ('VSS prediction tile build failed','OpenGL Error','Failed to compile shader'))
if not result.exists() or not result.read_text(encoding='utf-8').startswith('PASS ') or process.returncode!=0 or quality_error:
    lines=(WORK/'console.log').read_text(encoding='utf-8',errors='replace').splitlines()
    errors=[(i,l) for i,l in enumerate(lines) if any(k in l for k in ('/ERROR]','/FATAL]','Caused by:','Crash report saved','Exception in thread'))]
    for i,l in errors[:10]: print('\n'.join(lines[i:i+3])[:1800])
    print('Log: '+str(WORK/'console.log'))
    raise SystemExit('FAIL client smoke, exit '+str(process.returncode))
print(result.read_text(encoding='utf-8')); print('Log: '+str(WORK/'console.log'))
