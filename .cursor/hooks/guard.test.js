// guard.js 最小单元测试：node .cursor/hooks/guard.test.js
// 覆盖 deny / ask / allow 主要样本，重点锁住 git 全局参数写法的绕过回归。
const { test } = require("node:test");
const assert = require("node:assert");
const { shellVerdict, fileVerdict } = require("./guard.js");

const denyCmds = [
  "git push origin dev",
  "git -C /repo push origin dev",
  "git -c user.name=x push",
  "git --git-dir=/repo/.git push",
  "git --git-dir /repo/.git push origin dev",
  "git -C /repo -c user.name=x push",
  "git rebase dev",
  "git -C /repo merge dev",
  "git reset --hard HEAD",
  "git -C /repo reset --hard",
  "git checkout -- src/App.tsx",
  "git restore .",
  "git clean -fd",
  "git stash drop",
  "git stash clear",
  "git branch -D feat/x",
  "git -C /repo branch -D feat/x",
];
for (const cmd of denyCmds) {
  test(`deny: ${cmd}`, () => {
    assert.strictEqual((shellVerdict(cmd) || [])[0], "deny");
  });
}

const askCmds = ["git commit -m x", "git -C /repo commit -m x", "rm -rf build", "rm -fr node_modules"];
for (const cmd of askCmds) {
  test(`ask: ${cmd}`, () => {
    assert.strictEqual((shellVerdict(cmd) || [])[0], "ask");
  });
}

const allowCmds = [
  "git status",
  "git -C /repo log --oneline -3",
  "git -C /repo diff --stat",
  "git checkout -b feat/x",
  "git branch -d feat/x",
  "git stash list",
  "npm test",
  "pnpm lint",
  "mvn -pl admin -am test",
];
for (const cmd of allowCmds) {
  test(`allow: ${cmd}`, () => {
    assert.strictEqual(shellVerdict(cmd), null);
  });
}

test("file deny: 本地 .env", () => {
  assert.strictEqual((fileVerdict("Write", "apps/x/.env", "/repo") || [])[0], "deny");
  assert.strictEqual((fileVerdict("Write", "apps/x/.env.local", "/repo") || [])[0], "deny");
});

test("file allow: .env.example", () => {
  assert.strictEqual(fileVerdict("Write", "apps/x/.env.example", "/repo"), null);
});

test("file deny: secrets 目录与会话记录", () => {
  assert.strictEqual((fileVerdict("Write", "services/website-services/secrets/k.txt", "/repo") || [])[0], "deny");
  assert.strictEqual((fileVerdict("Write", "x/agent-transcripts/t.md", "/repo") || [])[0], "deny");
});

test("file deny: 删除测试", () => {
  assert.strictEqual((fileVerdict("Delete", "apps/x/src/foo.test.ts", "/repo") || [])[0], "deny");
  assert.strictEqual(fileVerdict("Write", "apps/x/src/foo.test.ts", "/repo"), null);
});

test("file ask: 改 hooks 本身", () => {
  assert.strictEqual((fileVerdict("Write", ".cursor/hooks/guard.js", "/repo") || [])[0], "ask");
  assert.strictEqual((fileVerdict("Write", ".cursor/hooks.json", "/repo") || [])[0], "ask");
});
