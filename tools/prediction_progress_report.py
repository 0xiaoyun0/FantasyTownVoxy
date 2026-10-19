"""Compare native cold-cache RTF runs; an empty high-quality draw list is not a pass."""
from pathlib import Path
import argparse
import hashlib
import json
import re
from paths import artifact, VERSION, NEOFORGE_VERSION, ROOT


def read_run(path):
    text = Path(path).read_text(encoding="utf-8", errors="replace")
    summaries = list(re.finditer(
        r"HXZ_FINE_PROGRESS acceptedMax=(\d+) coarseHiddenMax=(\d+) "
        r"firstAcceptedMs=(-?\d+) firstDrawMs=(-?\d+)", text))
    if not summaries:
        # The published 0.0.3 runtime diagnostic is also an honest zero-output baseline.
        accepted = [int(n) for n in re.findall(r"HXZ_QUALITY_RUNTIME accepted=(\d+)", text)]
        coarse = [int(n) for n in re.findall(r"coarseHidden=(\d+)", text)]
        if not accepted:
            raise AssertionError(f"No actual prediction snapshot evidence in {path}")
        return {"log": str(Path(path).resolve()), "acceptedMax": max(accepted), "coarseHiddenMax": max(coarse, default=0),
                "firstAcceptedMs": None, "firstDrawMs": None, "legacyDiagnostic": True,
                "evidenceVersion": "legacy-snapshot-only"}
    values = tuple(map(int, summaries[-1].groups()))
    result = dict(zip(("acceptedMax", "coarseHiddenMax", "firstAcceptedMs", "firstDrawMs"), values))
    result["runtimePassed"] = "HXZ_FINE_PROGRESS PASS" in text
    result["legacyDiagnostic"] = False
    result['log'] = str(Path(path).resolve())
    work=Path(path).resolve().parent
    jars=list((work/'mods').glob('fantasy-town-voxy-*.jar'))
    assert len(jars)==1, 'Progress run must identify one actual mod artifact'
    result['sha256']=hashlib.sha256(jars[0].read_bytes()).hexdigest()
    assert (work/'result.txt').read_text(encoding='utf-8').startswith('PASS '), 'Client did not finish the measurement cleanly'
    cost=re.search(r'HXZ_FINE_COST sampledPeakHeapMiB=(\d+) frameSamples=(\d+) frameDropped=(\d+) frameP50Ms=([\d.]+) frameP95Ms=([\d.]+) frameP99Ms=([\d.]+)',text)
    assert cost, 'Missing resource and frame measurements'
    result['cost']=dict(zip(('sampledPeakHeapMiB','frameSamples','frameDropped','frameP50Ms','frameP95Ms','frameP99Ms'),map(float,cost.groups())))
    assert result['cost']['frameSamples'] > 0 and result['cost']['frameDropped'] == 0, 'No complete frame sample set'
    for error in ('VSS prediction tile build failed','OpenGL Error','Failed to compile shader'):
        assert error not in text, error
    start = re.search(r'HXZ_FINE_WINDOW_START proofVersion=2 windowMs=(\d+) meshUploads=(\d+) rendered=(\d+)', text)
    end = re.search(r'HXZ_FINE_WINDOW_END proofVersion=2 elapsedMs=(\d+) meshUploads=(\d+) rendered=(\d+)', text)
    result['evidenceVersion'] = 'strict-window-v2' if start and end else 'legacy-cumulative-v1'
    if start and end:
        window, uploads_before, rendered_before = map(int, start.groups())
        elapsed, uploads_after, rendered_after = map(int, end.groups())
        assert elapsed >= window, 'Run stopped before its declared measurement window'
        assert uploads_after >= uploads_before and rendered_after >= rendered_before, 'Renderer counters reset during measurement'
        result['window'] = {'declaredMs': window, 'elapsedMs': elapsed,
                            'uploadsBefore': uploads_before, 'renderedBefore': rendered_before,
                            'uploadsAfter': uploads_after, 'renderedAfter': rendered_after,
                            'uploadDelta': uploads_after - uploads_before, 'renderedDelta': rendered_after - rendered_before}
        scene = re.search(r'HXZ_FINE_START actual RTF mountain=\(([-\d]+),([-\d]+)\) vanillaRadius=(\d+) qualityFirst=(true|false)', text)
        assert scene, 'Missing actual scene settings'
        launch = json.loads((work / 'launch-record.json').read_text(encoding='utf-8'))
        assert launch['jarSha256'] == result['sha256'], 'Launch record does not identify the measured JAR'
        args = (work / 'launch-args.txt').read_text(encoding='gbk', errors='replace')
        result['audit'] = bool(launch.get('exteriorAudit')) or '-Dhxz.smoke.exteriorAudit=true' in args
        assert not result['audit'], 'Exterior audit is diagnostic and cannot certify normal progress'
        heap = re.search(r'-Xmx(\d+)([MG])', args)
        assert heap, 'Missing actual heap limit'
        voxy = json.loads((work / 'config/voxy-config.json').read_text(encoding='utf-8'))
        vss = json.loads((work / 'config/vss-client-config.json').read_text(encoding='utf-8'))
        result['launch'] = launch
        result['conditions'] = {'neoforge': launch['neoforge'], 'rtfPreset': launch['rtfPreset'],
            'heapMiB': int(heap[1]) * (1024 if heap[2] == 'G' else 1),
            'serviceThreads': voxy['service_threads'], 'backgroundWorkers': vss['predictionBackgroundWorkers'],
            'refinementWorkers': vss['predictionRefinementWorkers'],
            'predictionDistanceBlocks': vss['predictionDistanceBlocks'],
            'predictionFineDistanceBlocks': vss['predictionFineDistanceBlocks'],
            'sceneX': int(scene[1]), 'sceneZ': int(scene[2]), 'vanillaRadius': int(scene[3]),
            'qualityFirst': scene[4] == 'true', 'windowMs': window}
    return result


def require_strict(run):
    assert run['evidenceVersion'] == 'strict-window-v2', 'Strict measurement requires renderer counter baselines'
    assert run['window']['declaredMs'] == 180_000, 'Official progress measurement requires a full 180-second window'
    launch = run['launch']
    assert launch['predictionProgress'] and launch['coldProgressFixture'] and not launch.get('exteriorAudit')
    assert run['conditions']['qualityFirst'] and run['conditions']['rtfPreset'] == 'custom'
    assert run['conditions']['neoforge'] == NEOFORGE_VERSION


def certify(candidate, baseline=None, expected_digest=None):
    require_strict(candidate)
    assert not candidate['launch'].get('baseline'), 'A baseline fixture cannot certify a candidate'
    assert candidate['acceptedMax'] > 0, 'No quality-eligible RTF prediction was published'
    assert 0 <= candidate['firstAcceptedMs'] <= candidate['firstDrawMs'] <= 180_000, \
        'Detailed RTF geometry did not reach a measured draw within the bounded test window'
    assert candidate['runtimePassed'], 'Native progress assertions did not pass'
    assert candidate['window']['uploadDelta'] > 0 and candidate['window']['renderedDelta'] > 0, \
        'Cumulative renderer counters do not prove a new upload and draw during this measurement'
    if expected_digest is not None:
        assert candidate['sha256'] == expected_digest, 'Progress measurement is from a different artifact'
    comparison = 'candidate-only'
    if baseline is not None:
        if baseline['evidenceVersion'] == 'strict-window-v2':
            require_strict(baseline)
            assert baseline['launch'].get('baseline'), 'Expected an explicit baseline fixture'
            assert baseline['conditions'] == candidate['conditions'], 'Baseline and candidate measurement conditions differ'
            comparison = 'same-settings-strict-window-v2'
        else:
            assert baseline['acceptedMax'] == 0, 'Legacy evidence is accepted only as a zero-output observation'
            comparison = 'legacy-zero-output-only; no strict resource or first-draw comparison'
    return comparison


def write_benchmark(result, path):
    """Write measured results only after strict candidate certification."""
    def time_value(run, key):
        number = run.get(key)
        return '窗口内未观察到' if number is None or number < 0 else f'{number / 1000:.3f} s'
    rows = []
    for label, run in (('基线', result.get('baseline')), ('候选 ' + VERSION, result['candidate'])):
        if run is None:
            continue
        strict = run['evidenceVersion'] == 'strict-window-v2'
        cost = run.get('cost', {}) if strict else {}
        metrics = [str(int(cost['sampledPeakHeapMiB'])) if cost else '旧证据不作比较']
        metrics += [f'{cost[key]:.3f}' if cost else '旧证据不作比较'
                    for key in ('frameP50Ms', 'frameP95Ms', 'frameP99Ms')]
        draw = time_value(run, 'firstDrawMs') if strict else '未按窗口增量验证'
        rows.append('| ' + ' | '.join([label, run['evidenceVersion'], str(run['acceptedMax']),
                    time_value(run, 'firstAcceptedMs'), draw, *metrics]) + ' |')
    c = result['candidate']['conditions']
    scope = (f"NeoForge {c['neoforge']}；RTF {c['rtfPreset']} 预设，位置 ({c['sceneX']}, {c['sceneZ']})；"
             f"180 秒冷缓存窗口；最大堆 {c['heapMiB']} MiB；Voxy 服务线程 {c['serviceThreads']}，"
             f"后台生成 {c['backgroundWorkers']}，精修并发上限 {c['refinementWorkers']}；"
             f"预测距离 {c['predictionDistanceBlocks']}、精细距离 {c['predictionFineDistanceBlocks']} 方块。")
    contents = '# 0.3.0 精细预测实测\n\n' + scope + '\n\n'
    contents += '比较等级：`' + result['comparison'] + '`。\n\n'
    contents += '| 版本 | 证据版本 | 最多合格网格 | 首次合格网格 | 首次窗口内上传并绘制 | 采样堆峰值 MiB | 帧间隔 p50 ms | p95 ms | p99 ms |\n'
    contents += '| --- | --- | ---: | --- | --- | ---: | ---: | ---: | ---: |\n' + '\n'.join(rows) + '\n\n'
    contents += ('计时从到达测试位置开始，包含随后发生的预测初始化；不包含启动游戏和到达位置前的等待。'
                 '首次合格/绘制由每秒采样发现，因此是观察时刻，存在采样和卡顿延迟。绘制证据要求窗口内新增 GPU 上传和渲染计数，'
                 '并且合格快照非空；它不衡量整片视野的完成率、画面美观程度或与真实地形的准确率。\n\n'
                 '堆峰值为每秒采样的已使用 Java 堆，不含堆外内存、显存，也不是绝对瞬时峰值。'
                 '帧间隔包含游戏、世界生成、GC、系统竞争和限帧影响，不是模组独占耗时，不能据此承诺所有电脑的 FPS。'
                 '这是单场景单次运行，未建立统计显著性；不换算生成速度倍数。外形审计模式禁止混入正式结果。\n\n')
    contents += f"候选 JAR SHA-256：`{result['sha256']}`。\n\n"
    for label in ('baseline', 'candidate'):
        if label in result:
            run = result[label]
            contents += f"- {label} 原始记录：`{Path(run['log']).parent.name}/console.log`"
            if 'sha256' in run:
                contents += f"；JAR SHA-256：`{run['sha256']}`"
            contents += '。\n'
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", help="Native progress console.log; detailed geometry must reach the GPU")
    parser.add_argument("--baseline", help="Equivalent old-version cold-cache log, or older diagnostic baseline")
    parser.add_argument("--output", help="Optional JSON result path")
    parser.add_argument('--benchmark', nargs='?', const=str(ROOT / 'docs/optimization-0.3.0/BENCHMARK.md'),
                        help='Write certified Markdown benchmark; optional path overrides the default')
    args = parser.parse_args()
    candidate = read_run(args.candidate)
    result = {"candidate": candidate}
    if args.baseline:
        result["baseline"] = read_run(args.baseline)
    digest=hashlib.sha256(artifact().read_bytes()).hexdigest()
    comparison = certify(candidate, result.get('baseline'), digest)
    result.update(sha256=digest,version=VERSION,neoforge=NEOFORGE_VERSION,allChecksPassed=True,
                  comparison=comparison,
                  scope='180-second cold-cache custom RTF; measured window counter deltas; single-run scene, no FPS or speed multiplier claim')
    if args.output:
        Path(args.output).write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    if args.benchmark:
        write_benchmark(result, args.benchmark)
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
