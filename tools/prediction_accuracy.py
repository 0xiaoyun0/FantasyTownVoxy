"""Generate independent actual terrain and compare unobserved predictions in native NeoForge."""
from pathlib import Path
import hashlib,json,shutil,subprocess,sys
import nbtlib as nbt

ROOT=Path(__file__).resolve().parents[1]
mode=sys.argv[1] if len(sys.argv)>1 else 'vanilla'
legacy='--legacy' in sys.argv
seed=int(next((x.split('=',1)[1] for x in sys.argv if x.startswith('--seed=')),'8675309'))
assert mode in ('export','vanilla','rtf-default','rtf-custom')
fixture=mode+('' if seed==8675309 else '-seed'+str(seed))
work=ROOT/('build/client-accuracy-'+fixture)
world=work/'saves/hxz-accuracy-fixture';world.mkdir(parents=True,exist_ok=True)
if not (world/'level.dat').exists():
    data=nbt.load(ROOT/'build/server-smoke-plain/hxz-smoke-world/level.dat')
    root=data['Data'];root.pop('Player',None)
    root.update(GameType=nbt.Int(3),allowCommands=nbt.Byte(1),SpawnX=nbt.Int(0),SpawnY=nbt.Int(160),SpawnZ=nbt.Int(0),LevelName=nbt.String('Private prediction accuracy fixture'))
    root['GameRules']['doMobSpawning']=nbt.String('false')
    root['GameRules']['spawnChunkRadius']=nbt.String('0')
    root['WorldGenSettings']['seed']=nbt.Long(seed)
    root['WorldGenSettings']['generate_features']=nbt.Byte(0)
    if mode!='export':
        root['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']=nbt.Compound({
            'type':nbt.String('minecraft:noise'),'settings':nbt.String('minecraft:overworld'),
            'biome_source':nbt.Compound({'type':nbt.String('minecraft:multi_noise'),'preset':nbt.String('minecraft:overworld')})})
    if mode.startswith('rtf-'):
        pack=ROOT/'build/client-accuracy-export/exported'/mode/'pack'
        assert (pack/'pack.mcmeta').exists(),'Run prediction_accuracy.py export first'
        shutil.copytree(pack,world/'datapacks/hxz-accuracy',dirs_exist_ok=True)
        root['DataPacks']['Enabled'].append(nbt.String('file/hxz-accuracy'))
    data.save(world/'level.dat')
jar=ROOT/'dist/fantasy-town-voxy-0.0.1-neoforge-mc1.21.1.jar'
digest=hashlib.sha256(jar.read_bytes()).hexdigest()
if (work/'accuracy.json').exists():(work/'accuracy.json').unlink()
result=subprocess.run([sys.executable,'tools/client_smoke.py','--accuracy='+fixture]+(['--legacy-prediction'] if legacy else []),cwd=ROOT)
validation=ROOT/'build/validation';validation.mkdir(exist_ok=True)
label=fixture+('-legacy' if legacy else '')
shutil.copy2(work/'console.log',validation/('accuracy-'+label+'-console.log'))
if (work/'accuracy.json').exists():
    report=json.loads((work/'accuracy.json').read_text());report['sha256']=digest;report['passed']=result.returncode==0;report['legacy']=legacy
    (validation/('accuracy-'+label+'.json')).write_text(json.dumps(report,indent=2))
assert digest==hashlib.sha256(jar.read_bytes()).hexdigest(),'Artifact changed during accuracy measurement'
raise SystemExit(result.returncode)
