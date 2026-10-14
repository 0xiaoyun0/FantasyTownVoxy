"""Compare actual RTF screenshots; keep sampler accuracy separate from visual quality."""
from pathlib import Path
import hashlib, json, re, shutil
import numpy as np
from PIL import Image
from paths import artifact, VERSION

R=Path(__file__).resolve().parents[1]
digest=hashlib.sha256(artifact().read_bytes()).hexdigest()
baseline=R/'build/quality-rtf-default-baseline'
previous=R/'build/quality-rtf-default-release001'
candidate=R/('build/quality-rtf-default-release'+VERSION.replace('.',''))
prediction=R/('build/quality-rtf-custom-quality'+VERSION.replace('.',''))
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
assert 'HXZ_QUALITY_FIRST PASS actual snapshot' in log
assert 'HXZ_QUALITY_RUNTIME PASS coarse terrain generated but not drawn' in log
selection=[{'accepted':int(a),'coarseHidden':int(c)} for a,c in re.findall(r'HXZ_QUALITY_RUNTIME accepted=(\d+) coarseHidden=(\d+) qualityFirst=true',log)]
assert selection and max(s['coarseHidden'] for s in selection)>0
assert 'HXZ_NORMAL_BRIDGE failed=false' in log
features=(R/'build/validation/client-features-rtf=default-client.log').read_text(encoding='utf-8',errors='replace')
assert (R/'build/validation/client-features-rtf=default-jar.sha256').read_text().strip()==digest
assert 'HXZ_QUALITY_FIRST PASS native Sodium Apply off/on with live renderer reconstruction' in features

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
        'scope':'actual RTF forest/mountain genuine LOD captures without shaders; actual RTF coarse startup prediction rejected; native detailed/incomplete/cached snapshot selection and Sodium live opt-out; fine prediction GPU output was not observed in this short startup fixture; screenshot diagnostics are not depth or universal seam measurements',
        'scenes':scenes,'predictionFixture':prediction.name,'predictionSelection':selection}
(R/'build/validation/QUALITY-PASS.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
images=R/('docs/quality-'+VERSION);images.mkdir(parents=True,exist_ok=True)
for scene,name in ((0,'forest'),(1,'mountain')):
    shutil.copy2(candidate/f'screenshots/quality-{scene}-handoff.png',images/(name+'-real-lod.png'))
shutil.copy2(prediction/'screenshots/quality-1-handoff.png',images/'mountain-quality-first.png')
lines=[f'# RTF 渲染质量验证 · {VERSION}','',f'JAR SHA256：`{digest}`。','',
    '本轮使用 NeoTerraForged 默认预设和海平面提高 9 格的自定义预设；启动前核对噪声生成器与 RTF 数据包，不使用超平坦。正式存档和客户端配置未修改。','',
    '## 默认画质优先','',
    '预测必须完成 64×64 网格、采样间距不超过 2 格才允许显示。普通和 Iris 路径共用过滤后的绘制及覆盖列表，粗块不能隐藏本来应该显示的真实 LOD。预测内部仍可生成和缓存粗采样，作为后续精修的输入，但不会拿来占位。','',
    '原生客户端反例：0.0.2 的实际 RenderSnapshot 接纳了粗网格和未精修缓存，新版拒绝它们，同时保留两种合格精细网格、原始缓存集合及开关关闭后的原行为。钠的实际“应用”按钮已测试关闭/开启和在线重建。','',
    f'自定义 RTF 山地启动实景中，最多已有 {max(s["coarseHidden"] for s in selection)} 块粗预测完成，它们均没有进入绘制列表；短时测试内没有精修预测到达 GPU。这是预期的等待行为，不能拿来证明精细预测已经绘制或整个远方已达到 90% 画面相似度。', '',
    f'![RTF 山地：真实 LOD 保留，未精修远方等待](docs/quality-{VERSION}/mountain-quality-first.png)','',
    '## 真实 LOD 交界对照','',
    '同一 RTF 世界、位置、视角和时间，先加载原版半径 8 区块，再降为 2 区块并重建渲染器。与保存的原始 NeoVoxy 和 0.0.1 视点比较。天空色和亮色中性像素仅作为图像辅助诊断，不等于深度测量或白缝识别。','',
    '| 场景 | 参考地形像素 | 相对原始 NeoVoxy 新增天空色 | 新版亮色中性像素 |','|---|---:|---:|---:|']
for s in scenes:
    lines.append(f'| {"森林" if s["scene"]==0 else "山地"} | {s["referenceTerrainPixels"]} | {s["newSkyColourVsNeoVoxy"]} | {s["counts"][VERSION]["brightNeutralPixelsAtReferenceTerrain"]} |')
lines += ['',f'![RTF 森林真实 LOD 交界](docs/quality-{VERSION}/forest-real-lod.png)','',
    f'![RTF 山地真实 LOD 交界](docs/quality-{VERSION}/mountain-real-lod.png)','',
    '用户报告的整圈白缝在这些固定视点中仍未稳定复现，不声称所有场景的白边已经消失。已有遮罩生命周期与深度投影 GPU 回归继续通过；本轮未覆盖所有光影包。','',
    '## 设置与代价','',
    '钠 → Fantasy Town Voxy → 地形预测 → LOD 画质优先，默认开启。较远处只有粗预测时等待精修或真实 LOD，可见预测范围可能小于设定距离。此策略不强制加线程或增加内存预算，性能预设保留开关。','',
    '“粗预测山坡（兼容选项）”默认关闭，仅在关闭画质优先后有效；旧配置保留的开启值不会绕过画质优先。已保存真实 LOD、用户预测总开关和 NeoVoxy/HXZ 渲染选择保留。','',
    '完整记录见发布包 validation-summary.json 的 quality 字段；地表采样精度另见 [准确度报告](PREDICTION_ACCURACY.zh-CN.md)。','']
(R/'RENDER_QUALITY.zh-CN.md').write_text('\n'.join(lines),encoding='utf-8')
print(json.dumps(report,indent=2))
