import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC = path.join(ROOT, 'src')

function resolveModule(spec, fromFile) {
  let target
  if (spec.startsWith('@/')) {
    target = path.join(SRC, spec.slice(2))
  } else if (spec.startsWith('.')) {
    target = path.normalize(path.join(path.dirname(fromFile), spec))
  } else return { ok: true, external: true }

  const tries = [
    target,
    `${target}.ts`,
    `${target}.tsx`,
    path.join(target, 'index.ts'),
    path.join(target, 'index.tsx'),
  ]
  for (const t of tries) {
    if (fs.existsSync(t)) {
      return { ok: true, resolved: path.relative(SRC, t).replace(/\\/g, '/') }
    }
  }
  return { ok: false }
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
        const r = resolveModule(spec, p)
        if (!r.ok) {
          issues.push({
            file: path.relative(SRC, p).replace(/\\/g, '/'),
            spec,
          })
        }
      }
    }
  }
}
walk(SRC)
console.log('broken imports:', issues.length)
for (const i of issues) console.log(`  ${i.file} -> ${i.spec}`)
