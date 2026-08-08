/**
 * Rename admin-web src files to PascalCase (首字母大写驼峰).
 * Keeps: index.ts(x), main.tsx, App.tsx, zh-CN|en-US|ja-JP locale files.
 */
import fs from 'fs'
import path from 'path'
import { execSync } from 'child_process'
import { fileURLToPath } from 'url'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const SRC = path.join(ROOT, 'src')

const SKIP = new Set(['index.ts', 'index.tsx', 'main.tsx', 'App.tsx'])
const LOCALE = /^(zh-CN|en-US|ja-JP)\.ts$/

function toPascalBase(name) {
  if (name.endsWith('.mock')) {
    return toPascalBase(name.slice(0, -5)) + 'Mock'
  }
  return name
    .split(/[-_.]+/)
    .filter(Boolean)
    .map((p) => p[0].toUpperCase() + p.slice(1))
    .join('')
}

function walk(dir, list = []) {
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, ent.name)
    if (ent.isDirectory()) walk(p, list)
    else if (/\.(ts|tsx)$/.test(ent.name)) list.push(p)
  }
  return list
}

function collectRenames() {
  const renames = []
  for (const abs of walk(SRC)) {
    const rel = path.relative(SRC, abs).split(path.sep).join('/')
    const dir = path.dirname(rel)
    const file = path.basename(rel)
    if (SKIP.has(file) || LOCALE.test(file)) continue
    const ext = path.extname(file)
    const base = file.slice(0, -ext.length)
    if (/^[A-Z][a-zA-Z0-9]*$/.test(base) && !base.includes('-') && !base.includes('_')) continue
    const newBase = toPascalBase(base)
    const newFile = newBase + ext
    if (newFile === file) continue
    const to = dir === '.' ? newFile : `${dir}/${newFile}`
    renames.push({ from: rel, to, oldBase: base, newBase, ext })
  }
  renames.sort((a, b) => b.from.length - a.from.length)
  return renames
}

function gitMv(fromAbs, toAbs) {
  const from = path.relative(ROOT, fromAbs).split(path.sep).join('/')
  const to = path.relative(ROOT, toAbs).split(path.sep).join('/')
  const tmp = `${to}.rename-tmp`
  execSync(`git mv "${from}" "${tmp}"`, { cwd: ROOT, stdio: 'pipe' })
  execSync(`git mv "${tmp}" "${to}"`, { cwd: ROOT, stdio: 'pipe' })
}

function updateImports(renames) {
  const scanDirs = [SRC, path.join(ROOT, 'index.html')]
  const files = []
  function scan(p) {
    if (fs.statSync(p).isFile()) {
      if (/\.(ts|tsx|html|md|mdc)$/.test(p)) files.push(p)
      return
    }
    for (const ent of fs.readdirSync(p, { withFileTypes: true })) {
      if (ent.name === 'node_modules') continue
      scan(path.join(p, ent.name))
    }
  }
  scan(SRC)
  const html = path.join(ROOT, 'index.html')
  if (fs.existsSync(html)) files.push(html)

  for (const filePath of files) {
    let content = fs.readFileSync(filePath, 'utf8')
    let changed = false
    for (const { from, to, oldBase, newBase } of renames) {
      const oldRel = from.replace(/\.(tsx?)$/, '')
      const newRel = to.replace(/\.(tsx?)$/, '')
      const pairs = [
        [`@/${oldRel}`, `@/${newRel}`],
        [`'@/${oldRel}'`, `'@/${newRel}'`],
        [`"@/${oldRel}"`, `"@/${newRel}"`],
        [`'./${oldBase}'`, `'./${newBase}'`],
        [`"./${oldBase}"`, `"./${newBase}"`],
        [`'../${oldBase}'`, `'../${newBase}'`],
        [`"../${oldBase}"`, `"../${newBase}"`],
      ]
      for (const [a, b] of pairs) {
        if (content.includes(a)) {
          content = content.split(a).join(b)
          changed = true
        }
      }
      const relRe = new RegExp(
        `((?:@/|\\.{1,2}/)(?:[\\w./]+/)+)${escapeRe(oldBase)}(?=['"])`,
        'g',
      )
      if (relRe.test(content)) {
        content = content.replace(
          new RegExp(
            `((?:@/|\\.{1,2}/)(?:[\\w./]+/)+)${escapeRe(oldBase)}(?=['"])`,
            'g',
          ),
          `$1${newBase}`,
        )
        changed = true
      }
    }
    if (changed) fs.writeFileSync(filePath, content)
  }
}

function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function main() {
  const renames = collectRenames()
  console.log(`Renaming ${renames.length} files...`)
  for (const { from, to } of renames) {
    const fromAbs = path.join(SRC, from.split('/').join(path.sep))
    const toAbs = path.join(SRC, to.split('/').join(path.sep))
    fs.mkdirSync(path.dirname(toAbs), { recursive: true })
    console.log(`  ${from} -> ${to}`)
    try {
      gitMv(fromAbs, toAbs)
    } catch {
      fs.renameSync(fromAbs, toAbs)
    }
  }
  console.log('Updating imports...')
  updateImports(renames)
  console.log('Done.')
}

main()
