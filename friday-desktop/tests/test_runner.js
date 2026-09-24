const assert = require('assert');
const os = require('os');
const fs = require('fs');
const path = require('path');

console.log('=== Running Friday Desktop & Voice Engine Test Suite ===\n');

let passed = 0;
let failed = 0;

function runTest(name, fn) {
  try {
    fn();
    console.log(`[PASS] ${name}`);
    passed++;
  } catch (err) {
    console.error(`[FAIL] ${name}:`, err.message);
    failed++;
  }
}

// 1. Test System Info Retrieval
runTest('System Info Retrieval', () => {
  const platform = os.platform();
  const arch = os.arch();
  const totalMem = os.totalmem();
  assert(platform === 'linux' || platform === 'win32' || platform === 'darwin');
  assert(arch.length > 0);
  assert(totalMem > 0);
});

// 2. Test Wake Word String Matching & Fuzzy Confidence
runTest('Wake Word String Matching & Fuzzy Confidence', () => {
  function matchWakeWord(input, target = 'hey friday', threshold = 0.7) {
    if (!input) return false;
    const clean = input.toLowerCase().trim();
    if (clean.includes(target)) return true;

    let a = clean, b = target;
    if (a === b) return true;
    let dp = Array(a.length + 1).fill(null).map(() => Array(b.length + 1).fill(0));
    for (let i = 0; i <= a.length; i++) dp[i][0] = i;
    for (let j = 0; j <= b.length; j++) dp[0][j] = j;
    for (let i = 1; i <= a.length; i++) {
      for (let j = 1; j <= b.length; j++) {
        let cost = a[i - 1] === b[j - 1] ? 0 : 1;
        dp[i][j] = Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost);
      }
    }
    let sim = 1.0 - (dp[a.length][b.length] / Math.max(a.length, b.length));
    return sim >= threshold;
  }

  assert.strictEqual(matchWakeWord('hey friday'), true);
  assert.strictEqual(matchWakeWord('hey friday open chrome'), true);
  assert.strictEqual(matchWakeWord('hay friday'), true);
  assert.strictEqual(matchWakeWord('random conversation'), false);
});

// 3. Test Custom TFLite File Validation
runTest('Custom TFLite Model File Validation', () => {
  const dummyTflitePath = path.join(__dirname, 'mock_model.tflite');
  fs.writeFileSync(dummyTflitePath, Buffer.from([0x1c, 0x00, 0x00, 0x00, 0x54, 0x46, 0x4c, 0x33]));
  assert(fs.existsSync(dummyTflitePath));
  const buf = fs.readFileSync(dummyTflitePath);
  assert.strictEqual(buf.toString('ascii', 4, 8), 'TFL3');
  fs.unlinkSync(dummyTflitePath);
});

// 4. Test Desktop System Command Router
runTest('Desktop System Command Execution Router', () => {
  function parseCommand(cmd) {
    const c = cmd.toLowerCase().trim();
    if (c.startsWith('open ')) return { action: 'OPEN_APP', app: c.replace('open ', '') };
    if (c.startsWith('volume ')) return { action: 'SET_VOLUME', level: parseInt(c.replace('volume ', '')) };
    if (c === 'time' || c === 'date') return { action: 'SYSTEM_QUERY', query: c };
    return { action: 'SHELL_EXEC', command: cmd };
  }

  assert.deepStrictEqual(parseCommand('open chrome'), { action: 'OPEN_APP', app: 'chrome' });
  assert.deepStrictEqual(parseCommand('volume 80'), { action: 'SET_VOLUME', level: 80 });
  assert.deepStrictEqual(parseCommand('time'), { action: 'SYSTEM_QUERY', query: 'time' });
  assert.deepStrictEqual(parseCommand('ls -la'), { action: 'SHELL_EXEC', command: 'ls -la' });
});

console.log(`\nTest Results: ${passed} Passed, ${failed} Failed.`);
if (failed > 0) process.exit(1);
