/**
 * Agent Console V2 P0：Agent 工作区不得写平台模型默认位。
 *
 * 扫描 `src/pages/agents/**`：禁止调用 createModelEntry / updateModelEntry，
 * 也禁止字面量 `defaultEntry: true`（平台默认只能在模型 API 管理页维护）。
 * 读取 `entry.defaultEntry` 做展示仍允许。
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const AGENTS_DIR = path.join(ROOT, 'src', 'pages', 'agents')

const FORBIDDEN = [
  { name: 'createModelEntry', re: /\bcreateModelEntry\b/ },
  { name: 'updateModelEntry', re: /\bupdateModelEntry\b/ },
  { name: 'defaultEntry: true', re: /defaultEntry\s*:\s*true\b/ },
]

const issues = []

/** Strip block/line comments so docstrings mentioning API names do not false-positive. */
function codeWithoutComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/(^|[^:])\/\/.*$/gm, '$1')
}

function walk(dir) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory()) {
      walk(p)
      continue
    }
    if (!/\.(ts|tsx)$/.test(ent.name)) continue
    const content = codeWithoutComments(fs.readFileSync(p, 'utf8'))
    const rel = path.relative(ROOT, p).replace(/\\/g, '/')
    for (const rule of FORBIDDEN) {
      if (rule.re.test(content)) {
        issues.push(`${rel}: forbidden ${rule.name}`)
      }
    }
  }
}

if (!fs.existsSync(AGENTS_DIR)) {
  console.error(`missing agents pages dir: ${AGENTS_DIR}`)
  process.exit(1)
}

walk(AGENTS_DIR)

if (issues.length > 0) {
  console.error('Agent pages must not write platform model defaults:')
  for (const line of issues) console.error(`  - ${line}`)
  process.exit(1)
}

console.log('ok: pages/agents has no create/updateModelEntry or defaultEntry:true writes')
