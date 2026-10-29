// continue.js 最小单元测试：node .cursor/hooks/continue.test.js
// 在临时目录里造 .cursor/runs 运行状态，锁住认领、续跑、无进展与轮次上限。
const { test, beforeEach, afterEach } = require("node:test");
const assert = require("node:assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { decide, field } = require("./continue.js");

let dir;
let prevCwd;

function writeRun(name, text) {
  const runs = path.join(dir, ".cursor", "runs");
  fs.mkdirSync(runs, { recursive: true });
  fs.writeFileSync(path.join(runs, name), text);
}

beforeEach(() => {
  prevCwd = process.cwd();
  dir = fs.mkdtempSync(path.join(os.tmpdir(), "continue-test-"));
  process.chdir(dir);
});

afterEach(() => {
  process.chdir(prevCwd);
  fs.rmSync(dir, { recursive: true, force: true });
});

test("field 解析", () => {
  assert.strictEqual(field("mode: fix\nstatus: running\nround: 3 / 10", "round"), "3 / 10");
  assert.strictEqual(field("status: running", "conversation"), "");
});

test("没有运行状态 → skip", () => {
  assert.strictEqual(decide({ status: "completed", conversation_id: "c1" }), "skip no running run");
});

test("status 不是 completed → skip", () => {
  writeRun("fix-a.md", "mode: fix\nstatus: running\nround: 0 / 10\n");
  assert.match(String(decide({ status: "aborted", conversation_id: "c1" })), /^skip status=/);
});

test("未认领的新运行被本会话认领并续跑，第二次同 mtime 判无进展", () => {
  writeRun("fix-a.md", "mode: fix\nstatus: running\nround: 1 / 10\n");
  const first = decide({ status: "completed", conversation_id: "c1" });
  assert.ok(first && typeof first === "object" && /fix-a\.md/.test(first.followup_message));
  const text = fs.readFileSync(path.join(dir, ".cursor", "runs", "fix-a.md"), "utf8");
  assert.match(text, /conversation: c1/);
  const second = decide({ status: "completed", conversation_id: "c1" });
  assert.match(String(second), /^stop no progress/);
});

test("round 到上限 → stop", () => {
  writeRun("fix-a.md", "mode: fix\nstatus: running\nround: 10 / 10\nconversation: c1\n");
  assert.match(String(decide({ status: "completed", conversation_id: "c1" })), /^stop round limit/);
});

test("status 非 running 的运行不被接手", () => {
  writeRun("fix-a.md", "mode: fix\nstatus: blocked\nround: 1 / 10\n");
  assert.strictEqual(decide({ status: "completed", conversation_id: "c1" }), "skip no running run");
});

test("两个未认领运行 → 不接手（避免接错）", () => {
  writeRun("fix-a.md", "mode: fix\nstatus: running\nround: 1 / 10\n");
  writeRun("fix-b.md", "mode: fix\nstatus: running\nround: 1 / 10\n");
  assert.strictEqual(decide({ status: "completed", conversation_id: "c1" }), "skip no running run");
});
