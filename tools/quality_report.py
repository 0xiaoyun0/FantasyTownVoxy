"""Compare actual RTF screenshots; keep sampler accuracy separate from visual quality."""
from pathlib import Path
import hashlib, json
import numpy as np
from PIL import Image
from paths import artifact, VERSION

R=Path(__file__).resolve().parents[1]
digest=hashlib.sha256(artifact().read_bytes()).hexdigest()
baseline=R/'build/quality-rtf-default-baseline'
previous=R/'build/quality-rtf-default-release001'
candidate=R/'build/quality-rtf-default-release002'
prediction=R/'build/quality-rtf-custom-bridge002'
original_hash='1ba68d6b19c8020d16f93f2ddfe184f23edb7fc138d692d732ee13ecd1e0071c'
previous_hash='85281019547d84f85aebcbe482377ba83381109842990ca77619e125b091657a'

def verify(work, expected, glob):
    jars=list((work/'mods').glob(glob));assert len(jars)==1
    assert hashlib.sha256(jars[0].read_bytes()).hexdigest()==expected
    assert (work/'result.txt').read_text(encoding='utf-8').startswith('PASS ')
    log=(work/'console.log').read_text(encoding='utf-8',errors='replace')
    assert 'QUALITY_VISUAL captured' in log
    for error in ('VSS prediction tile build failed','OpenGL Error','Failed to compile shader'):
        assert error not in log, (work.name,error)
    return log

verify(baseline,original_hash,'neovoxy*.jar')
verify(previous,previous_hash,'fantasy-town-voxy-*.jar')
verify(candidate,digest,'fantasy-town-voxy-*.jar')
log=verify(prediction,digest,'fantasy-town-voxy-*.jar')
assert 'HXZ_SMOOTH PASS actual mesher' in log
assert 'HXZ_NORMAL_BRIDGE failed=false reported=true completedViewport=me.cortex.voxy.' in log

def pixels(work,scene,kind):
    return np.asarray(Image.open(work/f'screenshots/quality-{scene}-{kind}.png').convert('RGB')).astype(np.int16)

def sky(rgb):
    # Fixed, cloudless daylight land scenes. This is a colour diagnostic, not depth.
    return (rgb[:,:,2]>rgb[:,:,1]+8)&(rgb[:,:,1]>rgb[:,:,0]+8)

scenes=[]
for scene in (0,1):
    reference=pixels(baseline,scene,'vanilla')
    ground=~sky(reference)
    assert ground.sum()>100000, 'Empty terrain comparison is not evidence'
    stock=pixels(baseline,scene,'handoff')
    current=pixels(candidate,scene,'handoff')
    old=pixels(previous,scene,'handoff')
    lost=int(np.count_nonzero(ground&~sky(stock)&sky(current)))
    # Allows raster-edge and asynchronously generated leaf differences, not broad gaps.
    assert lost<=int(ground.sum()*.001), (scene,'new sky-colour gaps',lost)
    counts={}
    for name,work,frame in [('NeoVoxy',baseline,stock),('0.0.1',previous,old),(VERSION,candidate,current)]:
        rgb=frame
        counts[name]={'skyColourAtReferenceTerrain':int(np.count_nonzero(ground&sky(frame))),
                      'brightNeutralPixelsAtReferenceTerrain':int(np.count_nonzero(ground&(rgb.min(axis=2)>=220)&(rgb.max(axis=2)-rgb.min(axis=2)<=12)))}
    scenes.append({'scene':scene,'referenceTerrainPixels':int(ground.sum()),'newSkyColourVsNeoVoxy':lost,'counts':counts})

report={'sha256':digest,'allChecksPassed':True,'originalSha256':original_hash,'previousSha256':previous_hash,
        'scope':'actual RTF forest and mountain captures; normal renderer without shaders; native coarse mesher and prediction/water callback; sky-colour diagnostics are not depth measurements or a guarantee that every seam is fixed',
        'scenes':scenes,'predictionFixture':prediction.name}
(R/'build/validation/QUALITY-PASS.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2))
