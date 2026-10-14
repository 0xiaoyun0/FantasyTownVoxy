"""Report measured terrain accuracy; never substitute a desired percentage for results."""
from pathlib import Path
import hashlib,json
R=Path(__file__).resolve().parents[1];V=R/'build/validation'
digest=hashlib.sha256((R/'dist/fantasy-town-voxy-0.0.1-neoforge-mc1.21.1.jar').read_bytes()).hexdigest()
results=[]
for seed in (8675309,42):
    for mode in ('vanilla','rtf-default','rtf-custom'):
        name=mode+('' if seed==8675309 else '-seed'+str(seed))
        r=json.loads((V/('accuracy-'+name+'.json')).read_text())
        assert r['passed'] and not r.get('legacy',False) and r['sha256']==digest
        assert r['samples']==2560 and r['wetSamples']>=128 and r['highGroundSamples']>=128
        for key in ('previewMatchPercent','refinedMatchPercent','wetMatchPercent','landMatchPercent','highGroundMatchPercent'):
            assert r[key]>=90,(name,key,r[key])
        assert r['previewRefinedIdenticalPercent']==100
        results.append(r)
legacy=[]
for name in ('vanilla','rtf-custom'):
    r=json.loads((V/('accuracy-'+name+'-legacy.json')).read_text())
    assert r['sha256']==digest and r['legacy'] and not r['passed'] and r['previewMatchPercent']<90
    legacy.append(r)
report={'sha256':digest,'allChecksPassed':True,'scope':'six native surface/water fixtures, two seeds, vanilla and actual RTF default/custom; sampled geometry only; not universal world/mesh/pixel/vegetation accuracy','results':results,'legacyRegressions':legacy}
(V/'PREDICTION-PASS.json').write_text(json.dumps(report,indent=2))
names={'vanilla':'原版地形','rtf-default':'RTF 默认预设','rtf-custom':'RTF 自定义：海平面 +9'}
lines=['# 预测地形准确度实测','',f'版本 0.0.1，JAR SHA256：`{digest}`。','',
'原生 NeoForge + NeoTerraForged 0.0.6 测试，共 6 组、15,360 个地表采样位置。两个种子均测试原版地形、RTF 默认预设、RTF 海平面增加 9 格的自定义预设。测试世界与客户端均位于独立目录，未读取正式地图。','',
'## 判定方法','',
'同一横向位置同时满足：地表高度误差不超过 **2 格**、是否有地表水/熔岩一致、有液体时种类一致且液面误差不超过 **1 格**，才计为命中。预览与精修各自必须达到 90%；预览中的水域、陆地、高地三类也分别必须达到 90%。不统计大量空气体素来提高分数。','',
'每组选择 6 个固定分散区域，再沿固定网格寻找 4 个预测高度高于海平面至少 40 格的区域，选择时不读取真实误差。每个区域取 2×2 区块，每区块取 64 个固定位置。先使用独立世界生成快照预测，再在集成服务器完整生成 FULL 区块并扫描实际方块，实际地形不会写回该预测采样器。原版覆盖渲染器使用的原生批量预览路径，RTF 覆盖其 Java 预览路径。','',
'地表参考排除树叶、树干、红树根、竹子、仙人掌和巨大蘑菇等植被，冰柱等未排除特征仍计入误差。测试是**地形采样准确率**，不是屏幕像素一致率或所有方块一致率；远处网格节点间仍有 LOD 简化。','',
'## 开启稳定预测与逐点采样','',
'| 地形 | 种子 | 预览 / 精修命中 | 水域 | 陆地 | 高地 | 预览与精修完全一致 |','|---|---:|---:|---:|---:|---:|---:|']
for r in results:
    kind=r['fixture'].split('-seed')[0]
    lines.append(f"| {names[kind]} | {r['seed']} | {r['previewMatchPercent']:.2f}% / {r['refinedMatchPercent']:.2f}% | {r['wetMatchPercent']:.2f}% | {r['landMatchPercent']:.2f}% | {r['highGroundMatchPercent']:.2f}% | {r['previewRefinedIdenticalPercent']:.0f}% |")
lines+=['','这里的完全一致只比较同一采样位置的地表高度、液体种类及液面，不包含植被、建筑和网格分辨率变化。','',
'## 关闭开关的对照','',
'同一 JAR、同一种子与相同选点规则，关闭三个开关，恢复 VSS 快速预览和插值。它们应触发准确度回归失败，证明测试能检测此问题，且开关没有写死。','',
'| 地形 | 原预览命中 | 精修命中 | 预览与精修完全一致 |','|---|---:|---:|---:|']
for r in legacy:lines.append(f"| {names[r['fixture']]} | {r['previewMatchPercent']:.2f}% | {r['refinedMatchPercent']:.2f}% | {r['previewRefinedIdenticalPercent']:.2f}% |")
lines+=['','## 范围与设置','',
f"六组中的最大地表偏差仍为 {max(r['maxPreviewHeightError'] for r in results)} 格；自然生成特征、冰柱、洞口等少数位置仍可能不同。结果不能保证任意种子、数据包、其他生成器或已被玩家修改的世界都达到 90%。真实区块和服务器共享 LOD 才能还原已有建筑；预测不代替真实数据。植被沿用 VSS 的群系/特征预测，数量可单独调节，不承诺逐树一致。",'',
'钠 → Fantasy Town Voxy → 地形预测：**稳定地形预测**、**预测网格逐点采样**与**补全冰山与恶地地形**默认开启，均可独立关闭。准确采样增加首次生成成本；渐进加载、动态视距和预测计算预算继续生效。应用后重建预测，新模式使用独立预测缓存，旧预测和真实 LOD 保留。RTF 初始化修复还补齐了使用同一设置对象的噪声设置注册表，避免仅预设编解码成功但预测无法启动。','',
'## 复现','',
'见 [BUILDING.md](BUILDING.md)。先运行 `python tools/prediction_accuracy.py export`，然后分别运行 `vanilla`、`rtf-default`、`rtf-custom`，并各自增加 `--seed=42` 再测一次。`vanilla --legacy` 与 `rtf-custom --legacy` 是预期失败的对照。最后运行 `python tools/prediction_report.py`，仅在六组和两个对照均符合预期且 JAR 哈希相同时生成通过记录。','']
(R/'PREDICTION_ACCURACY.zh-CN.md').write_text('\n'.join(lines),encoding='utf-8')
print('PASS six prediction accuracy fixtures and two opt-out regressions '+digest)
