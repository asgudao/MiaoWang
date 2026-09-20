/**
 * Web 端 SFC 离线编译校验
 *
 * 由来：沙箱里 `vite build` 必然失败（Vite 启动时会 `child_process.exec('net use')` → `spawn EPERM`，
 * 见项目挂账第 32 条），所以生产构建没法在 Agent 会话里验证。而 `vue-tsc` 只管类型，
 * **不管模板与样式的编译**（模板里写错一个指令、样式里写坏一条声明它都不报）。
 *
 * 这个脚本直接拿 `@vue/compiler-sfc` 把每个 `.vue` 过一遍：parse → compileScript →
 * compileTemplate → compileStyle(scoped)，任何一步报错就以退出码 1 结束。
 * 它替代不了真实构建（不做打包、不做 tree-shake），但能挡住"模板/样式写坏"这一类错误。
 *
 * 用法：node tools/check-sfc.js
 */
const fs = require('fs')
const path = require('path')
const { createRequire } = require('module')

const WEB = path.resolve(__dirname, '..', 'MiaoWang-web')
const SRC = path.join(WEB, 'src')
// @vue/compiler-sfc 装在 Web 工程里，不在 tools 里，所以从那边解析
const req = createRequire(path.join(WEB, 'package.json'))
const { parse, compileScript, compileTemplate, compileStyle } = req('@vue/compiler-sfc')

const files = fs
  .readdirSync(SRC, { recursive: true, withFileTypes: true })
  .filter((d) => d.isFile() && d.name.endsWith('.vue'))
  .map((d) => path.join(d.parentPath ?? d.path, d.name))

let problems = []

for (const file of files) {
  const rel = path.relative(WEB, file).replace(/\\/g, '/')
  const source = fs.readFileSync(file, 'utf8')
  const id = 'sfc-' + Buffer.from(rel).toString('hex').slice(0, 12)

  try {
    const { descriptor, errors } = parse(source, { filename: file })
    for (const e of errors) problems.push(`${rel}: 解析失败 ${e.message}`)

    let bindings
    if (descriptor.script || descriptor.scriptSetup) {
      const script = compileScript(descriptor, { id })
      bindings = script.bindings
    }

    if (descriptor.template) {
      const tpl = compileTemplate({
        source: descriptor.template.content,
        filename: file,
        id,
        scoped: true,
        bindingMetadata: bindings,
      })
      for (const e of tpl.errors) {
        problems.push(`${rel}: 模板编译失败 ${typeof e === 'string' ? e : e.message}`)
      }
    }

    for (const style of descriptor.styles) {
      const out = compileStyle({
        source: style.content,
        filename: file,
        id,
        scoped: style.scoped,
      })
      for (const e of out.errors) {
        problems.push(`${rel}: 样式编译失败 ${typeof e === 'string' ? e : e.message}`)
      }
    }
  } catch (e) {
    problems.push(`${rel}: 编译抛异常 ${e.message}`)
  }
}

for (const p of problems) console.log('  ✗ ' + p)

console.log(`[sfc] 校验 ${files.length} 个 .vue 文件，问题 ${problems.length} 个`)
console.log(problems.length ? '[sfc] 未通过 ❌' : '[sfc] 模板与样式全部编译通过 ✅')
process.exit(problems.length ? 1 : 0)