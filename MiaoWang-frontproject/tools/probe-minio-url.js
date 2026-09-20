#!/usr/bin/env node
/**
 * 探测「下发 URL 的取址」到底生效到哪一级（开发用，零第三方依赖）
 * =================================================================
 *
 * 【它回答什么问题】
 * 后端下发图片 URL 是三级取址（唯一实现在 common 的 ObjectStorageUrlBuilder）：
 *   ① minio.public-base-url 配了 → 用它
 *   ② 没配 → 按【本次请求的 Host】推导（X-Forwarded-Host 优先，端口 minio.public-port，默认 9000）
 *   ③ 没配 + 无请求上下文 → 内网 endpoint
 * 第 ② 级依赖"客户端用的 Host 能传到社区服务"。而网关默认**不透传** Host
 * （SCG 的 NettyRoutingFilter 无条件 remove("Host")，只有 preserveHost=true 才加回来），
 * 所以经网关调用时下游看到的是 Nacos 实例地址（如 192.168.100.45:8053），
 * 推导出来的图片地址手机照样到不了。本工具就是用来实测这一点的。
 *
 * 【怎么读结果】看打印出来的 `data.url` 的 host：
 *   = 你 --host/--xfh 传的值        → 第 ② 级生效（该头确实传到了下游）✅
 *   = Nacos 实例地址(192.168.100.45) → 第 ② 级拿到的是实例地址（Host 没被透传）❌
 *   = 192.168.137.128:9000          → 第 ② 级没生效：旧包还在跑 / Nacos 配了 public-base-url=内网 / 无上下文
 *   = CDN 域名                      → 第 ① 级生效（Nacos 配了 minio.public-base-url）
 *
 * 【用法】
 *   node probe-minio-url.js                                  # 基线：不伪装头，上传一张小 PNG 看 url
 *   node probe-minio-url.js --host 192.168.225.183:7000      # 伪装客户端 Host（手机网段）
 *   node probe-minio-url.js --xfh 192.168.225.183:7000       # 伪装 X-Forwarded-Host（哨兵值更易判定）
 *   node probe-minio-url.js --all                            # 三个变体都跑
 *   node probe-minio-url.js --host 192.168.225.183:7000 --put-post 181
 *                                                            # 上传成功顺手用真接口换掉 181 的图并读回
 *   node probe-minio-url.js --read-post 181                  # 只读回帖子（不写任何东西）
 *   环境变量：MIAOWANG_PROBE_GATEWAY / MIAOWANG_PROBE_USER / MIAOWANG_PROBE_PASSWORD
 *
 * 账号默认 probe114904（probe-auth.ps1 建的一次性 dev 演示账号，仅本地联调用）。
 * 上传会在 MinIO 里留下小对象（community/post/…），这是接口本身的行为，不是本工具的副作用。
 */

'use strict';

const http = require('http');
const zlib = require('zlib');

const DEFAULTS = {
  gateway: process.env.MIAOWANG_PROBE_GATEWAY || '192.168.100.45:7000',
  username: process.env.MIAOWANG_PROBE_USER || 'probe114904',
  password: process.env.MIAOWANG_PROBE_PASSWORD || 'MiaoWang123',
  // 证据里那张 186B PNG：既能当上传载荷，也便于比对
  pngUrl: 'http://192.168.137.128:9000/miaowang-community/community/post/2026/09/19/821590127bf640f48068d57b26958b97.png',
  uploadPath: '/miaowang-community/mapleleaf/miaowang/v1/community/file/image',
  loginPath: '/miaowang-user/mapleleaf/miaowang/v1/user/login',
};

function parseArgs(argv) {
  const options = { ...DEFAULTS, host: null, xfh: null, putPost: null, readPost: null, all: false, pngSource: 'auto' };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    const next = () => {
      const v = argv[i + 1];
      if (!v) throw new Error(`${arg} 缺少取值`);
      i += 1;
      return v;
    };
    if (arg === '--help' || arg === '-h') { options.help = true; }
    else if (arg === '--gateway') { options.gateway = next(); }
    else if (arg === '--user') { options.username = next(); }
    else if (arg === '--password') { options.password = next(); }
    else if (arg === '--host') { options.host = next(); }
    else if (arg === '--xfh') { options.xfh = next(); }
    else if (arg === '--put-post') { options.putPost = Number(next()); }
    else if (arg === '--read-post') { options.readPost = Number(next()); }
    else if (arg === '--all') { options.all = true; }
    else if (arg === '--png') {
      const value = next();
      if (value === 'generate') { options.pngSource = 'generate'; } else { options.pngUrl = value; options.pngSource = 'url'; }
    }
    else { throw new Error(`无法识别的参数：${arg}（用 --help）`); }
  }
  const [gh, gp] = splitHostPort(options.gateway, 7000);
  options.gatewayHost = gh;
  options.gatewayPort = gp;
  return options;
}

function splitHostPort(value, fallbackPort) {
  const text = String(value).trim().replace(/^https?:\/\//i, '').replace(/\/+$/, '');
  const index = text.lastIndexOf(':');
  if (index < 0) return [text, fallbackPort];
  return [text.slice(0, index), Number(text.slice(index + 1))];
}

function printUsage() {
  console.log(`
探针：下发 URL 三级取址实测（详见本文件头部注释）

  node probe-minio-url.js [--gateway ip:port] [--user u --password p]
                          [--host Host头] [--xfh X-Forwarded-Host头]
                          [--all] [--put-post <postId>] [--read-post <postId>]
                          [--png generate | --png <图片URL>]

默认网关 ${DEFAULTS.gateway}、账号 ${DEFAULTS.username}
`);
}

function call(options, { method, path, headers = {}, body = null, hostHeader }) {
  return new Promise((resolve) => {
    const finalHeaders = { ...headers };
    if (body) finalHeaders['Content-Length'] = body.length;
    if (hostHeader) finalHeaders['Host'] = hostHeader; // Node 核心 http 允许显式设 Host
    const req = http.request(
      { host: options.gatewayHost, port: options.gatewayPort, method, path, headers: finalHeaders },
      (res) => {
        const chunks = [];
        res.on('data', (c) => chunks.push(c));
        res.on('end', () => resolve({ status: res.statusCode, text: Buffer.concat(chunks).toString('utf8') }));
      });
    req.on('error', (e) => resolve({ status: 0, text: `ERROR ${e.message}` }));
    if (body) req.write(body);
    req.end();
  });
}

function multipart(field, filename, contentType, buf) {
  const boundary = `----MWProbe${Date.now().toString(16)}${Math.random().toString(16).slice(2)}`;
  const head = Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="${field}"; filename="${filename}"\r\n`
    + `Content-Type: ${contentType}\r\n\r\n`, 'utf8');
  const tail = Buffer.from(`\r\n--${boundary}--\r\n`, 'utf8');
  return { body: Buffer.concat([head, buf, tail]), contentType: `multipart/form-data; boundary=${boundary}` };
}

/** 现造一张合法 1x1 PNG（有 zlib 就够，不依赖外网/演示对象还在不在） */
function makeOnePixelPng() {
  const crcTable = [];
  for (let n = 0; n < 256; n += 1) {
    let c = n;
    for (let k = 0; k < 8; k += 1) c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1;
    crcTable[n] = c >>> 0;
  }
  const crc32 = (buf) => {
    let c = 0xFFFFFFFF;
    for (const byte of buf) c = crcTable[(c ^ byte) & 0xFF] ^ (c >>> 8);
    return (c ^ 0xFFFFFFFF) >>> 0;
  };
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length, 0);
    const typeAndData = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(crc32(typeAndData), 0);
    return Buffer.concat([len, typeAndData, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(1, 0); // width
  ihdr.writeUInt32BE(1, 4); // height
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // color type: RGBA
  const scanline = Buffer.from([0x00, 0x3A, 0x8B, 0x8B, 0xFF]); // filter 0 + 水青色 1px
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(scanline)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

function hostOf(url) {
  try { return new URL(url).host; } catch (e) { return '(不是合法 URL)'; }
}

/** 判定这次返回的 URL 命中了哪一级取址 */
function verdict(urlHost, options, variant) {
  if (!urlHost || urlHost === '(不是合法 URL)') return '无法判定（URL 不合法）';
  const sent = [variant.hostHeader, variant.xfh].filter(Boolean);
  for (const value of sent) {
    if (urlHost === value || urlHost === value.split(':')[0]) {
      return `命中第②级（推导用的正是我发的 ${value}）`;
    }
  }
  if (urlHost.startsWith('192.168.137.128')) {
    return '落到第③级内网 endpoint：第②级没生效（旧包在跑 / Nacos 配了 public-base-url=内网 / 无请求上下文）';
  }
  if (urlHost.startsWith('192.168.100.45')) {
    return '第②级推导出了【Nacos 实例地址】= 客户端 Host 没被透传（网关需要 PreserveHostHeader）';
  }
  return `命中第①级或其它来源（host=${urlHost}）`;
}

async function readPost(options, token, postId) {
  const res = await call(options, {
    method: 'GET',
    path: `/miaowang-community/mapleleaf/miaowang/v1/community/post/${postId}`,
    headers: { username: options.username, token },
  });
  console.log(`    GET /community/post/${postId} -> HTTP ${res.status}`);
  const body = JSON.parse(res.text);
  if (body.data) {
    console.log(`    title = ${JSON.stringify(body.data.title)}`);
    console.log(`    images = ${JSON.stringify(body.data.images)}`);
    console.log(`    images[0].url host = ${hostOf((body.data.images[0] || {}).url)}`);
  } else {
    console.log(`    ${res.text}`);
  }
  return body;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) { printUsage(); return; }

  const loginBody = Buffer.from(JSON.stringify({ username: options.username, password: options.password }), 'utf8');
  const login = await call(options, {
    method: 'POST', path: options.loginPath,
    headers: { 'Content-Type': 'application/json; charset=utf-8' }, body: loginBody,
  });
  console.log(`登录 ${options.username} @ ${options.gateway} -> HTTP ${login.status}`);
  let token = null;
  try { token = JSON.parse(login.text).data.token; } catch (e) { /* 下面统一报错 */ }
  if (!token) { console.log(`  登录失败：${login.text}`); process.exit(1); }
  console.log(`  token = ${token.slice(0, 24)}...`);

  if (options.readPost && !options.host && !options.xfh && !options.all) {
    console.log(`\n只读回帖子（不写任何东西）：`);
    await readPost(options, token, options.readPost);
    return;
  }

  const pngRes = await fetch(options.pngUrl).catch(() => null);
  let png = null;
  if (options.pngSource === 'generate') {
    png = makeOnePixelPng();
    console.log(`上传载荷：本地现造的 1x1 PNG（--png generate）${png.length}B`);
  } else if (pngRes && pngRes.ok) {
    png = Buffer.from(await pngRes.arrayBuffer());
    console.log(`上传载荷：${options.pngUrl} -> HTTP ${pngRes.status} ${png.length}B`);
  } else {
    png = makeOnePixelPng();
    console.log(`上传载荷：取不到 ${options.pngUrl}（HTTP ${pngRes ? pngRes.status : 'error'}），改用本地现造的 1x1 PNG ${png.length}B`);
  }

  const variants = [];
  if (options.all || (!options.host && !options.xfh)) {
    variants.push({ name: '基线（不伪装任何头）', headers: {}, hostHeader: null, xfh: null });
  }
  if (options.all || options.host) {
    variants.push({ name: `伪装 Host: ${options.host}`, headers: {}, hostHeader: options.host, xfh: null });
  }
  if (options.all || options.xfh) {
    variants.push({ name: `伪装 X-Forwarded-Host: ${options.xfh}`, headers: { 'X-Forwarded-Host': options.xfh }, hostHeader: null, xfh: options.xfh });
  }

  let lastUpload = null;
  for (const variant of variants) {
    const mp = multipart('file', 'probe.png', 'image/png', png);
    const res = await call(options, {
      method: 'POST', path: options.uploadPath,
      headers: { username: options.username, token, 'Content-Type': mp.contentType },
      body: mp.body, hostHeader: variant.hostHeader,
    });
    console.log(`\n--- ${variant.name}`);
    console.log(`    POST ${options.uploadPath} -> HTTP ${res.status}`);
    let body = null;
    try { body = JSON.parse(res.text); } catch (e) { /* ignore */ }
    if (!body || !body.data || !body.data.url) {
      console.log(`    body: ${res.text}`);
      continue;
    }
    console.log(`    data.url      = ${body.data.url}`);
    console.log(`    data.objectKey= ${body.data.objectKey}`);
    console.log(`    host          = ${hostOf(body.data.url)}`);
    console.log(`    判定          = ${verdict(hostOf(body.data.url), options, variant)}`);
    lastUpload = body.data;
  }

  if (options.putPost && lastUpload) {
    console.log(`\n--- PUT /community/post/${options.putPost} 换图（字段名取自 PostUpdateReqDTO：images[].url/objectKey）`);
    const putBody = Buffer.from(JSON.stringify({ images: [{ url: lastUpload.url, objectKey: lastUpload.objectKey }] }), 'utf8');
    const put = await call(options, {
      method: 'PUT', path: `/miaowang-community/mapleleaf/miaowang/v1/community/post/${options.putPost}`,
      headers: { username: options.username, token, 'Content-Type': 'application/json; charset=utf-8' }, body: putBody,
    });
    console.log(`    -> HTTP ${put.status}`);
    console.log(`    -> ${put.text.slice(0, 400)}`);
    console.log(`\n--- 读回确认`);
    await readPost(options, token, options.putPost);
  } else if (options.putPost) {
    console.log('\n（没有成功上传的 URL，跳过 PUT）');
  } else if (options.readPost) {
    console.log(`\n--- 读回确认（只读）`);
    await readPost(options, token, options.readPost);
  }
}

main().catch((error) => {
  console.error(`\n[失败] ${error.message}\n`);
  console.error('用 --help 查看用法。\n');
  process.exit(1);
});