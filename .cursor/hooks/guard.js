// 全自动运行的闸门：preToolUse 拦文件写入，beforeShellExecution 拦命令。
// 定位：防误触＋破坏性操作硬拒。只匹配命令文本与文件工具路径；把命令写进
// 脚本文件再执行、shell 间接改文件等不在拦截范围（见 .cursor/README.md「Hooks」）。
// 规则说明见 .cursor/README.md「Hooks」；测试见 guard.test.js（node 直接跑）。
const fs = require("fs");
const path = require("path");

// git 全局参数（-C <path>、-c <k=v>、--git-dir 等）可插在 git 与子命令之间，
// 匹配时必须容忍，否则 `git -C <path> rebase` 一类写法会整条绕过。
const GIT =
  "git(?:\\s+(?:-C\\s+\\S+|-c\\s+\\S+|--git-dir(?:=\\S+|\\s+\\S+)?|--work-tree(?:=\\S+|\\s+\\S+)?|--namespace(?:=\\S+|\\s+\\S+)?|--exec-path(?:=\\S+|\\s+\\S+)?|-[pP]|--paginate|--no-pager))*\\s+";
const gitRe = (body) => new RegExp(`\\b${GIT}${body}`);

const SHELL_DENY = [
  [gitRe("(rebase|cherry-pick|am)\\b"), "不自动改写 git 历史（rebase／cherry-pick／am）"],
  [gitRe("reset\\s+--hard\\b"), "不丢弃工作区改动"],
  [gitRe("(checkout|restore)\\s+(--\\s|\\.(\\s|$)|--worktree|--source)"), "不丢弃工作区改动"],
  [gitRe("clean\\b"), "不删除未跟踪文件"],
  [gitRe("stash\\s+(drop|clear)\\b"), "不丢弃 stash"],
  [gitRe("branch\\s+-D\\b"), "不强删分支"],
];
const SHELL_ASK = [
  [gitRe("commit\\b"), "提交需要确认；/dev /fix /hunt 循环里不要自己 commit"],
  [/\brm\s+-[a-z]*(rf|fr)\b|\bRemove-Item\b[^|;]*-Recurse/i, "递归删除需要用户确认"],
];

function norm(p) {
  return String(p || "").replace(/\\/g, "/");
}

function fileVerdict(toolName, filePath, cwd) {
  const p = norm(filePath);
  const base = path.posix.basename(p);
  const isDelete = /delete/i.test(toolName);

  if ((base === ".env" || base.startsWith(".env.")) && base !== ".env.example") {
    return ["deny", "本地 .env 含真实密钥，只改 .env.example 和 docs/operations/ENVIRONMENT_VARIABLES.md"];
  }
  if (/\/agent-transcripts\//.test(p)) {
    return ["deny", "会话记录只读"];
  }
  if (/services\/website-services\/secrets\//.test(p)) {
    return ["deny", "密钥目录只读"];
  }
  if (/\/db\/migration\/V\d+__[^/]+\.sql$/.test(p)) {
    const abs = path.isAbsolute(filePath) ? filePath : path.join(cwd || process.cwd(), filePath);
    if (isDelete || fs.existsSync(abs)) {
      return ["deny", "已有 Flyway 迁移不可修改或删除，改表请新增下一个版本号的迁移"];
    }
  }
  if (isDelete && /(\/src\/test\/|\/tests\/|__tests__\/|\.(test|spec)\.[jt]sx?$|\/test_[^/]+\.py$)/.test(p)) {
    return ["deny", "不删除测试；测试失败要修实现或改断言并说明原因"];
  }
  if (/\.cursor\/hooks(\.json$|\/)/.test(p)) {
    return ["ask", "修改闸门本身需要用户确认"];
  }
  return null;
}

// 空洞提交訊息直接拒绝（提交规范见 .cursor/README.md「提交规范」硬规矩）。
// 只拦明确的占位词；正常 Conventional Commits 标题不受影响，仍走 commit 的 ask。
const MEANINGLESS_MSG = /^(no message|wip|tmp|temp|update|updates|misc|change|changes|fix|test|\.|提交|修改)$/i;
function commitMessageVerdict(command) {
  if (!gitRe("commit\\b").test(command)) return null;
  const m =
    command.match(/(?:-m|--message)(?:=|\s+)"([^"]*)"/) ||
    command.match(/(?:-m|--message)(?:=|\s+)'([^']*)'/) ||
    command.match(/(?:-m|--message)(?:=|\s+)(\S+)/);
  if (!m) return null;
  const msg = m[1].trim();
  if (!msg || MEANINGLESS_MSG.test(msg)) {
    return ["deny", "提交訊息空洞：标题要用 Conventional Commits 写明做了什么（feat/fix/docs/…(scope): …）"];
  }
  return null;
}

function shellVerdict(command) {
  for (const [re, why] of SHELL_DENY) if (re.test(command)) return ["deny", why];
  const cmv = commitMessageVerdict(command);
  if (cmv) return cmv;
  for (const [re, why] of SHELL_ASK) if (re.test(command)) return ["ask", why];
  return null;
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { shellVerdict, fileVerdict };
}

if (typeof require !== "undefined" && typeof module !== "undefined" && require.main === module) {
  let raw = "";
  process.stdin.on("data", (c) => (raw += c));
  process.stdin.on("end", () => {
    let input = {};
    try {
      input = JSON.parse(raw.replace(/^\uFEFF/, "") || "{}");
    } catch {
      input = {};
    }
    let verdict = null;
    if (input.hook_event_name === "beforeShellExecution") {
      verdict = shellVerdict(String(input.command || ""));
    } else if (input.hook_event_name === "preToolUse") {
      const toolInput = input.tool_input || {};
      const filePath = toolInput.file_path || toolInput.path;
      if (filePath && input.tool_name !== "Read") {
        verdict = fileVerdict(String(input.tool_name || ""), filePath, input.cwd);
      }
    }
    if (!verdict) {
      process.stdout.write(JSON.stringify({ permission: "allow" }));
      return;
    }
    const [permission, why] = verdict;
    process.stdout.write(
      JSON.stringify({
        permission,
        user_message: `闸门：${why}`,
        agent_message: `被 .cursor/hooks/guard.js 拦下：${why}。不要绕过；需要时用 AskQuestion 问用户，或把它写进运行状态的「需要用户决定」。`,
      }),
    );
  });
}
