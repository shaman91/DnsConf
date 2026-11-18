"""Exercise the actual workflow shell block with fake Maven and sleep, never DNS."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]


def build_script():
    lines = (ROOT / '.github/workflows/github_action.yml').read_text().splitlines()
    start = lines.index('      - name: Build with Maven')
    start = lines.index('        run: |', start) + 1
    result = []
    for line in lines[start:]:
        if line and not line.startswith('          '):
            break
        result.append(line[10:] if line else '')
    return '\n'.join(result) + '\n'


THROTTLED = '[ERROR] Could not transfer artifact example from/to central: status code: 429, reason phrase: Too Many Requests (429)'


class RetryPolicyTest(unittest.TestCase):
    def exercise(self, responses):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)
            for number, (status, message) in enumerate(responses, 1):
                (path / f'response-{number}').write_text(message + '\n')
                (path / f'status-{number}').write_text(str(status))
            (path / 'mvn').write_text('''#!/bin/bash
set -eu
count=0
if [[ -f "$RUNNER_TEMP/count" ]]; then count=$(cat "$RUNNER_TEMP/count"); fi
count=$((count + 1))
printf '%s' "$count" > "$RUNNER_TEMP/count"
printf '%s\\n' "$*" >> "$RUNNER_TEMP/arguments"
cat "$RUNNER_TEMP/response-$count"
exit "$(cat "$RUNNER_TEMP/status-$count")"
''')
            (path / 'sleep').write_text('''#!/bin/bash
printf '%s\\n' "$*" >> "$RUNNER_TEMP/sleeps"
''')
            for name in ('mvn', 'sleep'):
                (path / name).chmod(0o755)
            env = {'PATH': f'{tmp}:/usr/bin:/bin', 'RUNNER_TEMP': tmp}
            result = subprocess.run(['bash', '-e', '-o', 'pipefail', '-c', build_script()],
                                    env=env, capture_output=True, text=True, timeout=5)
            count = int((path / 'count').read_text())
            waits = (path / 'sleeps').read_text().splitlines() if (path / 'sleeps').exists() else []
            self.assertEqual((path / 'arguments').read_text().splitlines(),
                             ['-B -V clean package'] * count)
            return result, count, waits

    def test_success_first_try(self):
        result, count, waits = self.exercise([(0, 'BUILD SUCCESS')])
        self.assertEqual((result.returncode, count, waits), (0, 1, []))
        self.assertIn('BUILD SUCCESS', result.stdout)

    def test_429_then_success(self):
        result, count, waits = self.exercise([(1, THROTTLED), (0, 'BUILD SUCCESS')])
        self.assertEqual((result.returncode, count, waits), (0, 2, ['60']))
        self.assertIn(THROTTLED, result.stdout)

    def test_three_429_fail_preserving_final_status(self):
        result, count, waits = self.exercise([(1, THROTTLED), (1, THROTTLED), (7, THROTTLED)])
        self.assertEqual((result.returncode, count, waits), (7, 3, ['60', '120']))

    def test_metadata_429(self):
        result, count, waits = self.exercise([(1, THROTTLED.replace('artifact', 'metadata')),
                                             (0, 'BUILD SUCCESS')])
        self.assertEqual((result.returncode, count, waits), (0, 2, ['60']))

    def test_non_429_transfer_errors_do_not_retry(self):
        for code in (401, 403, 404, 500, 4290):
            with self.subTest(code=code):
                result, count, waits = self.exercise([(9, THROTTLED.replace('429', str(code)))])
                self.assertEqual((result.returncode, count, waits), (9, 1, []))

    def test_compilation_and_test_errors_do_not_retry(self):
        for message in ('[ERROR] COMPILATION ERROR', '[ERROR] There are test failures.',
                        '[ERROR] test 429 failed', 'INFO ' + THROTTLED):
            with self.subTest(message=message):
                result, count, waits = self.exercise([(2, message)])
                self.assertEqual((result.returncode, count, waits), (2, 1, []))

    def test_failure_after_retry_is_not_hidden(self):
        result, count, waits = self.exercise([(1, THROTTLED), (3, '[ERROR] There are test failures.')])
        self.assertEqual((result.returncode, count, waits), (3, 2, ['60']))

    def test_two_retries_then_success(self):
        result, count, waits = self.exercise([(1, THROTTLED), (1, THROTTLED), (0, 'BUILD SUCCESS')])
        self.assertEqual((result.returncode, count, waits), (0, 3, ['60', '120']))

    def test_shell_syntax(self):
        subprocess.run(['bash', '-n'], input=build_script(), text=True, check=True)


if __name__ == '__main__':
    unittest.main(verbosity=2)
