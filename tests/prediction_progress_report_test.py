"""Report-contract fixtures only: these synthetic records are never benchmark evidence."""
from pathlib import Path
import hashlib
import json
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from prediction_progress_report import read_run, certify, write_benchmark
from paths import NEOFORGE_VERSION


class ReportContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='report-contract-', dir=ROOT / 'build')
        self.root = Path(self.temp.name).resolve()
        assert self.root.parent == (ROOT / 'build').resolve()
        self.addCleanup(self.temp.cleanup)

    def fixture(self, name='candidate', baseline=False, strict=True, audit=False):
        work = self.root / name
        (work / 'mods').mkdir(parents=True)
        (work / 'config').mkdir()
        jar = b'synthetic report-contract data; not a real mod'
        (work / 'mods/fantasy-town-voxy-test.jar').write_bytes(jar)
        digest = hashlib.sha256(jar).hexdigest()
        (work / 'result.txt').write_text('PASS synthetic fixture', encoding='utf-8')
        launch = dict(jarSha256=digest, neoforge=NEOFORGE_VERSION, rtfPreset='custom',
                      predictionProgress=True, coldProgressFixture=True, baseline=baseline, exteriorAudit=audit)
        (work / 'launch-record.json').write_text(json.dumps(launch), encoding='utf-8')
        (work / 'launch-args.txt').write_text('"-Xmx3072M"\n', encoding='gbk')
        (work / 'config/voxy-config.json').write_text(json.dumps(dict(service_threads=2)), encoding='utf-8')
        (work / 'config/vss-client-config.json').write_text(json.dumps(dict(predictionBackgroundWorkers=1,
            predictionRefinementWorkers=2, predictionDistanceBlocks=2048, predictionFineDistanceBlocks=1024)), encoding='utf-8')
        text = 'HXZ_FINE_START actual RTF mountain=(3080,-4088) vanillaRadius=2 qualityFirst=true\n'
        if strict:
            text += 'HXZ_FINE_WINDOW_START proofVersion=2 windowMs=180000 meshUploads=12 rendered=9000\n'
        text += ('HXZ_FINE_PROGRESS acceptedMax=0 coarseHiddenMax=5 firstAcceptedMs=-1 firstDrawMs=-1\n' if baseline else
                 'HXZ_FINE_PROGRESS PASS HXZ_FINE_PROGRESS acceptedMax=7 coarseHiddenMax=0 firstAcceptedMs=31000 firstDrawMs=32000\n')
        text += 'HXZ_FINE_COST sampledPeakHeapMiB=1400 frameSamples=1000 frameDropped=0 frameP50Ms=20.000 frameP95Ms=50.000 frameP99Ms=100.000\n'
        if strict:
            text += ('HXZ_FINE_WINDOW_END proofVersion=2 elapsedMs=180010 meshUploads=12 rendered=9000\n' if baseline else
                     'HXZ_FINE_WINDOW_END proofVersion=2 elapsedMs=180010 meshUploads=19 rendered=37672\n')
        path = work / 'console.log'
        path.write_text(text, encoding='utf-8')
        return path

    def test_strict_same_settings_and_document(self):
        candidate = read_run(self.fixture())
        baseline = read_run(self.fixture('baseline', baseline=True))
        comparison = certify(candidate, baseline, candidate['sha256'])
        self.assertEqual(comparison, 'same-settings-strict-window-v2')
        target = self.root / 'BENCHMARK.md'
        write_benchmark(dict(candidate=candidate, baseline=baseline, comparison=comparison,
                             sha256=candidate['sha256']), target)
        self.assertIn('32.000 s', target.read_text(encoding='utf-8'))
        self.assertIn('未建立统计显著性', target.read_text(encoding='utf-8'))

    def test_prior_draw_without_increment_is_not_evidence(self):
        candidate = read_run(self.fixture())
        candidate['window']['uploadDelta'] = candidate['window']['renderedDelta'] = 0
        with self.assertRaisesRegex(AssertionError, 'Cumulative renderer'):
            certify(candidate)

    def test_short_window_rejected(self):
        candidate = read_run(self.fixture())
        candidate['window']['declaredMs'] = 60_000
        with self.assertRaisesRegex(AssertionError, '180-second'):
            certify(candidate)

    def test_audit_rejected(self):
        with self.assertRaisesRegex(AssertionError, 'Exterior audit'):
            read_run(self.fixture(audit=True))

    def test_old_manifest_cannot_hide_audit_argument(self):
        path = self.fixture()
        with (path.parent / 'launch-args.txt').open('a', encoding='gbk') as out:
            out.write('"-Dhxz.smoke.exteriorAudit=true"\n')
        with self.assertRaisesRegex(AssertionError, 'Exterior audit'):
            read_run(path)

    def test_legacy_candidate_rejected(self):
        with self.assertRaisesRegex(AssertionError, 'counter baselines'):
            certify(read_run(self.fixture(strict=False)))

    def test_legacy_zero_baseline_explicitly_limited(self):
        candidate = read_run(self.fixture())
        baseline = read_run(self.fixture('baseline', baseline=True, strict=False))
        self.assertTrue(certify(candidate, baseline).startswith('legacy-zero-output-only'))
        baseline['acceptedMax'] = 1
        with self.assertRaisesRegex(AssertionError, 'zero-output'):
            certify(candidate, baseline)

    def test_budget_mismatch_rejected(self):
        candidate = read_run(self.fixture())
        baseline = read_run(self.fixture('baseline', baseline=True))
        baseline['conditions']['refinementWorkers'] = 4
        with self.assertRaisesRegex(AssertionError, 'conditions differ'):
            certify(candidate, baseline)

    def test_wrong_artifact_rejected(self):
        with self.assertRaisesRegex(AssertionError, 'different artifact'):
            certify(read_run(self.fixture()), expected_digest='wrong')


if __name__ == '__main__':
    unittest.main()
