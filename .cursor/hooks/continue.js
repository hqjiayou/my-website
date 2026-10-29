// stop 续跑：本会话有 status: running 的运行状态文件时，让 Agent 继续下一轮。
// 运行状态格式见 .cursor/README.md「运行状态」。
// 判定逻辑可被 require 供测试（continue.test.js，node 直接跑）；作为脚本被
// hooks.json 调用时才走 stdin 主流程。
const fs = require("fs");
const path = require("path");

const RUNS = ".cursor/runs";
const STATE = path.join(RUNS, ".continue-state.json");
const LOG = path.join(RUNS, "continue.log");
// 未认领的运行文件只在刚写过时才归本会话，避免别的会话的旧任务被接走。
const CLAIM_WINDOW_MS = 30 * 60 * 1000;

function field(text, name) {
  const m = text.match(new RegExp(`^${name}:\\s*(.+?)\\s*(#.*)?$`, "m"));
  return m ? m[1] : "";
}

function readJson(file, fallback) {
  try {
    return JSON.parse(fs.readFileSync(file, "utf8"));
  } catch {
    return fallback;
  }
}

function log(line) {
  fs.appendFileSync(LOG, `${new Date().toISOString()} ${line}\n`);
}

function findRun(conversationId) {
  const files = fs.existsSync(RUNS) ? fs.readdirSync(RUNS).filter((f) => f.endsWith(".md")) : [];
  const runs = files.map((f) => {
    const file = path.join(RUNS, f);
    const text = fs.readFileSync(file, "utf8");
    return { file, text, mtime: fs.statSync(file).mtimeMs };
  });
  const running = runs.filter((r) => field(r.text, "status") === "running");
  const mine = running.find((r) => field(r.text, "conversation") === conversationId);
  if (mine) return mine;
  const unclaimed = running.filter(
    (r) => !field(r.text, "conversation") && Date.now() - r.mtime < CLAIM_WINDOW_MS,
  );
  if (unclaimed.length !== 1) return null;
  const run = unclaimed[0];
  run.text = run.text.replace(/^(status:.*)$/m, `$1\nconversation: ${conversationId}`);
  fs.writeFileSync(run.file, run.text);
  run.mtime = fs.statSync(run.file).mtimeMs;
  log(`claim ${run.file}`);
  return run;
}

function decide(input) {
  if (input.status && input.status !== "completed") return `skip status=${input.status}`;
  const run = findRun(input.conversation_id);
  if (!run) return "skip no running run";

  const [done, max] = field(run.text, "round").split("/").map((s) => parseInt(s, 10));
  if (Number.isFinite(done) && Number.isFinite(max) && done >= max) return `stop round limit ${run.file}`;

  const state = readJson(STATE, {});
  if (state[run.file] === run.mtime) return `stop no progress ${run.file}`;
  state[run.file] = run.mtime;
  fs.writeFileSync(STATE, JSON.stringify(state));

  const mode = field(run.text, "mode");
  const runPath = run.file.replace(/\\/g, "/");
  return {
    followup_message:
      `继续 ${runPath}：先读这份运行状态，按 .cursor/skills/${mode}/SKILL.md 做下一轮。` +
      `做完更新 round 和「每轮记录」；满足 stop 把 status 改成 done，需要用户决定就改成 blocked 并写进「需要用户决定」。`,
  };
}

if (typeof module !== "undefined" && module.exports) {
  module.exports = { decide, findRun, field };
}

if (typeof require !== "undefined" && typeof module !== "undefined" && require.main === module) {
  let raw = "";
  process.stdin.on("data", (c) => (raw += c));
  process.stdin.on("end", () => {
    let out = {};
    try {
      fs.mkdirSync(RUNS, { recursive: true });
      const input = JSON.parse(raw.replace(/^\uFEFF/, "") || "{}");
      const result = decide(input);
      if (typeof result === "string") {
        log(`${result} (status=${input.status} loop=${input.loop_count})`);
      } else {
        log(`continue (loop=${input.loop_count})`);
        out = result;
      }
    } catch (e) {
      try {
        log(`error ${e && e.message}`);
      } catch {}
    }
    process.stdout.write(JSON.stringify(out));
  });
}
