"""Private RTF worlds for integration tests; reject flat worlds before launch."""
from pathlib import Path
import shutil
import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[1]

def prepare_rtf_world(destination, preset='default'):
    assert preset in ('default', 'custom')
    destination = Path(destination).resolve()
    assert (ROOT / 'build').resolve() in destination.parents
    source = ROOT / ('build/client-accuracy-rtf-' + preset) / 'saves/hxz-accuracy-fixture'
    assert (source / 'level.dat').exists(), 'Generate the native RTF accuracy fixture first'
    if not destination.exists():
        shutil.copytree(source, destination)
        data = nbt.load(destination / 'level.dat')
        data['Data'].pop('Player', None)
        data['Data']['GameType'] = nbt.Int(3)
        data['Data']['GameRules']['doMobSpawning'] = nbt.String('false')
        data.save(destination / 'level.dat')
    data = nbt.load(destination / 'level.dat')['Data']
    generator = data['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']
    assert str(generator['type']) == 'minecraft:noise', 'RTF tests must never use a flat generator'
    assert 'file/hxz-accuracy' in data['DataPacks']['Enabled'], 'RTF pack is not enabled'
    pack = destination / 'datapacks/hxz-accuracy'
    assert (pack / 'data/reterraforged/worldgen').is_dir(), 'Not an RTF generated preset pack'
    print('Verified actual RTF ' + preset + ' world: ' + str(destination), flush=True)
    return destination
