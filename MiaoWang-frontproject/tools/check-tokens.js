/**
 * 令牌引用校验
 *
 * 为什么需要它：CSS 自定义属性引用不存在的变量会**静默失效**（声明被整条丢弃），
 * SCSS 变量引用不存在的变量会**直接编译报错**。这里把两端的设计令牌当契约检查：
 * 任何 var(--mw-*) / $mw-* 的引用都必须在令牌唯一真源里有定义。
 *
 * 真源：
 *   Web  MiaoWang-web/src/assets/styles/tokens.css
 *   手机 MiaoWang-phone/uni.scss
 *
 * 用法：node check-tokens.js     （有问题时退出码为 1）
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')

function read(rel) {
  return fs.readFileSync(path.join(ROOT, rel), 'utf8')
}

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (['node_modules', 'unpackage', '.git', 'dist', '.tmp-lint'].includes(e.name)) continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, out)
    else if (/\.(vue|css|scss)$/.test(e.name)) out.push(p)
  }
  return out
}

const cssTokens = read('MiaoWang-web/src/assets/styles/tokens.css')
const definedCss = new Set([...cssTokens.matchAll(/^\s*(--mw-[a-z0-9-]+)\s*:/gm)].map((m) => m[1]))

const scssTokens = read('MiaoWang-phone/uni.scss')
const definedScss = new Set([...scssTokens.matchAll(/^\s*(\$mw-[a-z0-9-]+)\s*:/gm)].map((m) => m[1]))

const files = walk(path.join(ROOT, 'MiaoWang-web', 'src')).concat(
  walk(path.join(ROOT, 'MiaoWang-phone')),
)

const problems = []
for (const f of files) {
  const rel = path.relative(ROOT, f).replace(/\\/g, '/')
  const src = fs.readFileSync(f, 'utf8')
  for (const m of src.matchAll(/var\(\s*(--mw-[a-z0-9-]+)/g)) {
    if (!definedCss.has(m[1])) problems.push(`未定义 CSS 变量 ${m[1]}  ← ${rel}`)
  }
  for (const m of src.matchAll(/(\$mw-[a-z0-9-]+)/g)) {
    if (!definedScss.has(m[1])) problems.push(`未定义 SCSS 变量 ${m[1]}  ← ${rel}`)
  }
}

console.log(`[tokens] Web 定义 ${definedCss.size} 个，手机定义 ${definedScss.size} 个，扫描 ${files.length} 个文件`)
const uniq = [...new Set(problems)]
if (uniq.length) {
  console.error(uniq.join('\n'))
  console.error(`[tokens] ${uniq.length} 处引用未定义`)
  process.exit(1)
}
console.log('[tokens] 所有令牌引用都有定义 ✅')