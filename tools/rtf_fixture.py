"""Private RTF worlds for integration tests; reject flat worlds before launch."""
from pathlib import Path
import hashlib, shutil
import nbtlib as nbt

ROOT = Path(__file__).resolve().parents[1]

def preset_digest(world):
    pack = Path(world) / 'datapacks/hxz-accuracy'
    digest = hashlib.sha256()
    for entry in sorted(p for p in pack.rglob('*') if p.is_file()):
        digest.update(entry.relative_to(pack).as_posix().encode('utf-8'))
        digest.update(b'\0')
        digest.update(entry.read_bytes())
    return digest.hexdigest()

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
    source_data = nbt.load(source / 'level.dat')['Data']
    assert data['WorldGenSettings'] == source_data['WorldGenSettings'], 'Fixture seed/generator differs from requested RTF source; use a new private fixture directory'
    assert preset_digest(destination) == preset_digest(source), 'Fixture RTF datapack differs from requested preset; use a new private fixture directory'
    generator = data['WorldGenSettings']['dimensions']['minecraft:overworld']['generator']
    assert str(generator['type']) == 'minecraft:noise', 'RTF tests must never use a flat generator'
    assert 'file/hxz-accuracy' in data['DataPacks']['Enabled'], 'RTF pack is not enabled'
    pack = destination / 'datapacks/hxz-accuracy'
    assert (pack / 'data/reterraforged/worldgen').is_dir(), 'Not an RTF generated preset pack'
    print('Verified actual RTF ' + preset + ' world: ' + str(destination), flush=True)
    return destination
