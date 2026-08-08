import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC = path.join(ROOT, 'src')

function getActualPath(relPath) {
  const parts = relPath.split('/')
  let current = SRC
  const actual = []
  for (const part of parts) {
    if (!fs.existsSync(current)) return null
    const entries = fs.readdirSync(current)
    // import 段不带扩展名，磁盘上的文件段带 .ts/.tsx（如 index.tsx）。
    // 先尝试精确（目录/带扩展名同名）匹配，再回退到“忽略扩展名”匹配，
    // 并把磁盘真实大小写（去扩展名）回读进 actual，才能与不带扩展名的 import 段做大小写敏感对比。
    let match = entries.find((e) => e.toLowerCase() === part.toLowerCase())
    let resolvedSeg = match
    if (!match) {
      const withExt = entries.find((e) => e.replace(/\.(tsx?)$/, '').toLowerCase() === part.toLowerCase())
      if (!withExt) return null
      match = withExt
      resolvedSeg = withExt.replace(/\.(tsx?)$/, '')
    }
    actual.push(resolvedSeg)
    current = path.join(current, match)
  }
  return actual.join('/')
}

function resolveFile(spec, fromFile) {
  let target
  if (spec.startsWith('@/')) target = path.join(SRC, spec.slice(2))
  else if (spec.startsWith('.')) target = path.normalize(path.join(path.dirname(fromFile), spec))
  else return null

  const tries = [`${target}.ts`, `${target}.tsx`, path.join(target, 'index.ts'), path.join(target, 'index.tsx')]
  for (const t of tries) {
    if (fs.existsSync(t)) return t
  }
  return null
}

const issues = []
function walk(dir) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory()) walk(p)
    else if (/\.(ts|tsx)$/.test(ent.name)) {
      const content = fs.readFileSync(p, 'utf8')
      const re = /from\s+['"]([^'"]+)['"]/g
      let m
      while ((m = re.exec(content))) {
        const spec = m[1]
        if (!spec.startsWith('@/') && !spec.startsWith('.')) continue
        const resolved = resolveFile(spec, p)
        if (!resolved) continue
        const rel = path.relative(SRC, resolved).replace(/\\/g, '/')
        const withoutExt = rel.replace(/\.(tsx?)$/, '')
        const specPath = spec.startsWith('@/') ? spec.slice(2) : path.relative(path.dirname(p), resolved).replace(/\\/g, '/').replace(/\.(tsx?)$/, '')
        if (spec.startsWith('.')) {
          const normalized = path.relative(SRC, resolved).replace(/\\/g, '/').replace(/\.(tsx?)$/, '')
          const importNorm = path
            .normalize(path.join(path.dirname(path.relative(SRC, p)), spec))
            .replace(/\\/g, '/')
          const actual = getActualPath(normalized)
          if (actual && actual !== normalized && actual.toLowerCase() === normalized.toLowerCase()) {
            issues.push({
              file: path.relative(SRC, p).replace(/\\/g, '/'),
              spec,
              expected: './' + path.relative(path.dirname(path.relative(SRC, p)), path.join(SRC, actual)).replace(/\\/g, '/'),
            })
          }
        } else {
          const actual = getActualPath(withoutExt)
          if (actual && actual !== withoutExt && actual.toLowerCase() === withoutExt.toLowerCase()) {
            issues.push({
              file: path.relative(SRC, p).replace(/\\/g, '/'),
              spec,
              expected: `@/${actual}`,
            })
          }
        }
      }
    }
  }
}
walk(SRC)
console.log('casing mismatches:', issues.length)
for (const i of issues.slice(0, 50)) {
  console.log(`${i.file}\n  ${i.spec} -> should be ${i.expected}`)
}
