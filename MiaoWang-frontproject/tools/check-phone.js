/**
 * 手机端（HBuilderX 工程）离线静态校验
 *
 * 为什么需要它：本工程不能用 npm / CLI，Agent 也跑不了 HBuilderX，
 * 所以用**真正的 Vue SFC 编译器 + JS 解析器**在提交前兜住五类会直接炸掉编译/渲染的错误：
 *   1. 模板语法错误（compileTemplate）
 *   2. <script> 语法错误（含 renderjs 那个额外的 script 块）
 *   3. import 路径落到不存在的文件 / 裸导入（本工程没有 npm 依赖，裸导入必炸）
 *   4. 模板里引用了**实例上不存在**的名字（编译产物统一写成 `_ctx.X` → 拿它当信号）
 *      —— 2026-09-17 真机实测：mine.vue 模板里写了 `auth.initial()`，`auth` 只是 import 进来的
 *      普通对象、不在实例上，渲染期直接抛错并**白屏**。Options API 的模板只能访问
 *      data / computed / methods / props；store、utils 这类模块对象必须先经 computed 或 methods 转一手。
 *      （唯一例外：renderjs 的模块名，如 `<script module="stage">` 后模板里的 `stage.loadModel` —— 已白名单）
 *   5. **computed 被当函数调用**（`this.x(...)` 或模板 `x(...)`）
 *      —— 2026-09-18 实测：vip.vue 把 `quotaText` 写进了 computed，却按方法调用 `this.quotaText(v, l, d)`。
 *      Vue3 的 computed 取到的是**值本身**（这里是字符串），再调用它就是
 *      `TypeError: this.quotaText is not a function`；渲染期（或渲染读到的 computed）抛错时
 *      Vue 会把整棵子树替换成注释节点 → **整页白屏**（已用 HBuilderX 自带的 Vue 3.4.21 + 自建渲染器实测：
 *      errorHandler 收到 info="render function"，出错的组件子树变成 `#comment`）。
 *      ⚠️ 这条是硬规则：本工程**不允许**"computed 返回函数再调用"的写法（语义靠返回类型隐藏，太容易踩），
 *      需要按参数算的展示逻辑一律写 methods。
 *
 * 已知的**非问题**（脚本已过滤，不要误报）：
 *   - App.vue 没有 <template>：uni-app 的应用入口本来就没有
 *   - main.js 里 `from 'vue'`：由 uni-app 运行时提供
 *   - libs/ 下的 three.js 是第三方源码，注释里的 URL/字符串会被误判
 *
 * 用法：node check-phone.js     （有问题时退出码为 1）
 */
const fs = require('fs')
const path = require('path')
const sfc = require('@vue/compiler-sfc')
const acorn = require('acorn')

const ROOT = path.resolve(__dirname, '..', 'MiaoWang-phone')
const SKIP_DIRS = ['node_modules', 'unpackage', '.git', 'libs']
/** 由 uni-app 运行时/编译器提供的裸导入，不算错 */
const RUNTIME_BARE = new Set(['vue', 'vue-i18n'])
const EXTS = ['', '.js', '.vue', '/index.js', '.json', '.scss']

function collect(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.includes(e.name)) continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) collect(p, out)
    else if (/\.(vue|js)$/.test(e.name)) out.push(p)
  }
  return out
}

const PARSE_OPTS = {
  ecmaVersion: 2022,
  sourceType: 'module',
  allowReturnOutsideFunction: true,
  allowAwaitOutsideFunction: true,
}

function parse(code) {
  try {
    return acorn.parse(code, PARSE_OPTS)
  } catch {
    return null
  }
}

function syntaxError(code, label) {
  try {
    acorn.parse(code, PARSE_OPTS)
    return null
  } catch (e) {
    return `JS 语法错误 [${label}] ${e.message}`
  }
}

/** 通用 AST 遍历（不引 acorn-walk；显式栈，避免大文件递归爆栈） */
function walkAst(root, visit) {
  const stack = [root]
  while (stack.length) {
    const node = stack.pop()
    if (!node || typeof node !== 'object') continue
    if (Array.isArray(node)) {
      for (const n of node) stack.push(n)
      continue
    }
    if (typeof node.type !== 'string') continue
    visit(node)
    for (const k of Object.keys(node)) {
      if (k === 'type' || k === 'start' || k === 'end') continue
      const v = node[k]
      if (v && typeof v === 'object') stack.push(v)
    }
  }
}

/** `export default { ... }` 里 computed 的键集合 */
function computedNames(code) {
  const names = new Set()
  const ast = parse(code)
  if (!ast) return names
  const exported = ast.body.find((n) => n.type === 'ExportDefaultDeclaration')
  const obj = exported && exported.declaration
  if (!obj || obj.type !== 'ObjectExpression') return names
  for (const p of obj.properties) {
    if (p.type !== 'Property' || (p.key && (p.key.name || p.key.value)) !== 'computed') continue
    if (p.value.type !== 'ObjectExpression') continue
    for (const c of p.value.properties) {
      if (c.type !== 'Property') continue // 展开（...mapGetters）不猜
      const k = c.key && (c.key.name || c.key.value)
      if (k) names.add(k)
    }
  }
  return names
}

/** 被当函数调用的名字：脚本里的 `this.x(` + 模板编译产物里的 `_ctx.x(` */
function calledNames(code, templateCode) {
  const called = new Set()
  const ast = parse(code)
  if (ast) {
    walkAst(ast, (n) => {
      if (n.type !== 'CallExpression') return
      const c = n.callee
      if (
        c &&
        c.type === 'MemberExpression' &&
        c.object.type === 'ThisExpression' &&
        !c.computed &&
        c.property.type === 'Identifier'
      ) {
        called.add(c.property.name)
      }
    })
  }
  for (const m of (templateCode || '').matchAll(/_ctx\.([A-Za-z_$][\w$]*)\s*\(/g)) called.add(m[1])
  return called
}

function resolveSpec(spec, fromFile) {
  let base
  if (spec.startsWith('@/')) base = path.join(ROOT, spec.slice(2))
  else if (spec.startsWith('.')) base = path.resolve(path.dirname(fromFile), spec)
  else return { bare: true }

  for (const ext of EXTS) {
    const p = base + ext
    if (fs.existsSync(p) && fs.statSync(p).isFile()) return { file: p }
  }
  return { missing: true }
}

/**
 * 从主 <script> 的 AST 里取出"实例上暴露了哪些名字"：
 * data() 的返回对象键 + computed / methods / props / components / inject 的键。
 * 解析不出来就返回空集合（不因为自己的解析失败而误报）。
 */
function instanceBindings(code) {
  const names = new Set()
  if (!code) return names
  const ast = parse(code)
  if (!ast) return names
  const exported = ast.body.find((n) => n.type === 'ExportDefaultDeclaration')
  const obj = exported && exported.declaration
  if (!obj || obj.type !== 'ObjectExpression') return names

  const keyOf = (p) => (p.key && (p.key.name || p.key.value)) || null
  const addKeys = (node) => {
    if (!node) return
    if (node.type === 'ObjectExpression') {
      for (const p of node.properties) {
        if (p.type !== 'Property') continue // 展开（...mapState）不猜
        const k = keyOf(p)
        if (k) names.add(k)
      }
    } else if (node.type === 'ArrayExpression') {
      for (const el of node.elements) {
        if (el && el.type === 'Literal' && typeof el.value === 'string') names.add(el.value)
      }
    }
  }

  for (const p of obj.properties) {
    if (p.type !== 'Property') continue
    const k = keyOf(p)
    if (!k) continue
    if (k === 'data') {
      const fn = p.value
      const body =
        fn.type === 'FunctionExpression' || fn.type === 'ArrowFunctionExpression' ? fn.body : null
      if (body && body.type === 'ObjectExpression') addKeys(body) // data: () => ({ ... })
      else if (body && body.type === 'BlockStatement') {
        for (const st of body.body) {
          if (st.type === 'ReturnStatement') addKeys(st.argument)
        }
      }
    } else if (['computed', 'methods', 'props', 'components', 'inject'].includes(k)) {
      addKeys(p.value)
    }
  }
  return names
}

const files = collect(ROOT)
const problems = []

for (const f of files) {
  const rel = path.relative(ROOT, f).replace(/\\/g, '/')
  const src = fs.readFileSync(f, 'utf8')
  const blocks = []

  if (f.endsWith('.js')) {
    blocks.push({ code: src, label: rel })
  } else {
    /** 主 <script>（renderjs 的 module="stage" 块不算：它由 uni-app 单独编译） */
    let mainScript = ''
    for (const m of src.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)) {
      const attrs = m[1]
      blocks.push({ code: m[2], label: `${rel} <script ${attrs.replace(/\s+/g, ' ').trim()}>` })
      if (!/\bmodule\s*=/.test(attrs) && !/\bsetup\b/.test(attrs)) mainScript += m[2] + '\n'
    }
    const tpl = /<template>([\s\S]*)<\/template>/.exec(src)
    if (tpl) {
      const r = sfc.compileTemplate({ source: tpl[1], filename: f, id: 'mw' })
      for (const e of r.errors || []) problems.push(`模板 [${rel}] ${e.message || e}`)
      // 模板里用到的名字：编译器会把非局部标识符统一写成 _ctx.X（v-for 别名、$event 是局部，不会出现）
      const bindings = instanceBindings(mainScript)
      // renderjs 的模块名在模板里是合法的（如 `:change:murl="stage.loadModel"`），不算未定义
      for (const m of src.matchAll(/<script\b[^>]*\bmodule\s*=\s*["']([^"']+)["']/g)) bindings.add(m[1])
      const used = [...new Set([...r.code.matchAll(/_ctx\.([A-Za-z_$][\w$]*)/g)].map((m) => m[1]))]
      for (const name of used) {
        if (name.startsWith('$') || bindings.has(name)) continue // $slots/$refs 等是 Vue 内建
        problems.push(
          `模板引用了实例上不存在的 "${name}" [${rel}] —— Options API 模板只认 data/computed/methods/props，` +
            `import 进来的模块对象（store/utils）必须先经 computed 或 methods 转一手，否则渲染期报错并白屏`,
        )
      }

      // 5) computed 被当函数调用：Vue3 里 `this.x` 已经是 getter 的值，再 `x(...)` 必抛
      //    TypeError，渲染期抛错 → 整页变空白（vip.vue 白屏事故就是这么来的）
      const computeds = computedNames(mainScript)
      for (const name of calledNames(mainScript, r.code)) {
        if (!computeds.has(name)) continue
        problems.push(
          `computed "${name}" 被当函数调用 [${rel}] —— Vue3 的 computed 取到的是值本身，` +
            `\`this.${name}(...)\` 会抛 TypeError: this.${name} is not a function；` +
            `渲染期抛错会让整页白屏。按参数算的展示逻辑请写进 methods（本工程不允许 computed 返回函数再调用）`,
        )
      }
    } else if (rel !== 'App.vue') {
      problems.push(`缺 <template> [${rel}]`)
    }
  }

  for (const b of blocks) {
    const err = syntaxError(b.code, b.label)
    if (err) problems.push(err)
    const specs = [...b.code.matchAll(/(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g)].map((m) => m[1])
    for (const spec of new Set(specs)) {
      const r = resolveSpec(spec, f)
      if (r.bare) {
        if (!RUNTIME_BARE.has(spec)) {
          problems.push(`裸导入（本工程无 npm 依赖，会失败）[${rel}] "${spec}"`)
        }
      } else if (r.missing) {
        problems.push(`导入路径不存在 [${rel}] "${spec}"`)
      }
    }
  }
}

console.log(`[phone] 校验 ${files.length} 个文件（已跳过 libs/ 第三方源码）`)
if (problems.length) {
  console.error(problems.join('\n'))
  console.error(`[phone] ${problems.length} 个问题`)
  process.exit(1)
}
console.log('[phone] 模板 + 语法 + import 路径 + 模板绑定 + computed 调用 全部通过 ✅')