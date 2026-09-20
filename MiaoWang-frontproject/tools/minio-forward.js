#!/usr/bin/env node
/**
 * MinIO 开发用「代转发」小工具（无第三方依赖，只用 Node 内置模块）
 * ================================================================
 *
 * 【它解决什么问题】
 * 开发阶段 MinIO 跑在 VM 上（默认 192.168.137.128:9000），那是 VM/独立网段：
 * **开发机（PC）到得了，手机等外部设备到不了**。后端把 MinIO 直链下发给客户端时，
 * 如果填的是这个内网地址，就会出现"PC 浏览器能看图、手机端整片图裂、接口却是通的"。
 *
 * 本工具在开发机上监听一个端口（默认 9000），把收到的请求**原样转发**到 VM 的 MinIO，
 * 于是手机只要访问「开发机局域网IP:9000」就能拿到同一张图。
 * 后端「下发 URL」的取址是三级优先级（唯一实现在 common 的 ObjectStorageUrlBuilder）：
 *   ① minio.public-base-url 配了 → 用它；② 没配 → 按本次请求的 Host 推导（端口 = minio.public-port，
 *   默认 9000）；③ 拿不到请求上下文 → 内网 endpoint。
 * 所以只要**本工具的端口 = minio.public-port（默认 9000 = endpoint 端口）**，
 * 开发期一行配置都不用填：手机用哪个 IP 调接口，图片地址就自动用哪个 IP。
 *
 * 【用途边界】
 * - 这是**开发机代转发**，只为让手机等外部设备能访问对象存储；
 * - **生产不要用它** —— 生产应把 minio.public-base-url 配成公网 endpoint 或 CDN 域名；
 * - 转发是透明的（method / path / query / headers / 状态码 / 响应头 / 二进制 body 全透传），
 *   不做鉴权、不做签名，等于把 MinIO 的匿名读能力开在开发机上，仅限局域网内自己用。
 *
 * 【用法】
 *   node minio-forward.js                       # 监听 0.0.0.0:9000 → 192.168.137.128:9000
 *   node minio-forward.js --port 9100           # 换本机端口（9000 被占用时）
 *   node minio-forward.js --target 10.0.0.5:9000
 *   npm run forward:minio                       # 等价于第一条
 *   环境变量：MINIO_FORWARD_PORT / MINIO_FORWARD_TARGET
 *
 * 启动后它会打印本机所有**非回环 IPv4 地址**（手机要用的就是其中之一），
 * 端口被占用时会清晰报错并退出（不会静默失败）。
 */

'use strict';

const http = require('http');
const os = require('os');

const DEFAULT_PORT = 9000;
const DEFAULT_TARGET = '192.168.137.128:9000';

/** 解析命令行参数：--port / --target / --help（-h） */
function parseArgs(argv) {
  const options = {
    port: Number(process.env.MINIO_FORWARD_PORT) || DEFAULT_PORT,
    target: process.env.MINIO_FORWARD_TARGET || DEFAULT_TARGET,
    help: false,
  };
  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--help' || arg === '-h') {
      options.help = true;
    } else if (arg === '--port' || arg === '-p') {
      const value = argv[i + 1];
      if (!value) throw new Error('--port 缺少取值，例如：--port 9100');
      options.port = Number(value);
      i += 1;
    } else if (arg === '--target' || arg === '-t') {
      const value = argv[i + 1];
      if (!value) throw new Error('--target 缺少取值，例如：--target 192.168.137.128:9000');
      options.target = value;
      i += 1;
    } else {
      throw new Error(`无法识别的参数：${arg}（用 --help 看用法）`);
    }
  }
  if (!Number.isInteger(options.port) || options.port < 1 || options.port > 65535) {
    throw new Error(`端口不合法：${options.port}（应为 1~65535）`);
  }
  const [targetHost, targetPort] = splitTarget(options.target);
  if (!targetHost || !targetPort) {
    throw new Error(`转发目标不合法：${options.target}（应为 host:port，例如 192.168.137.128:9000）`);
  }
  return { ...options, targetHost, targetPort };
}

/** 把 "host:port" 拆成 host 与数字端口；也容忍只写 host（默认 9000） */
function splitTarget(target) {
  const trimmed = String(target).trim().replace(/^https?:\/\//i, '').replace(/\/+$/, '');
  const index = trimmed.lastIndexOf(':');
  if (index < 0) return [trimmed, 9000];
  return [trimmed.slice(0, index), Number(trimmed.slice(index + 1))];
}

/** 本机所有非回环 IPv4 地址：手机等外部设备要连的就是其中之一 */
function listLanIPv4() {
  const result = [];
  const interfaces = os.networkInterfaces();
  for (const [name, addresses] of Object.entries(interfaces)) {
    for (const address of addresses || []) {
      // Node 18+ 的 family 是字符串 'IPv4'，更老的版本是数字 4，两种都认
      const isIPv4 = address.family === 'IPv4' || address.family === 4;
      if (isIPv4 && !address.internal) {
        result.push({ name, address: address.address });
      }
    }
  }
  return result;
}

function printUsage() {
  console.log(`
MinIO 开发用代转发工具（手机等外部设备 → 本机 → VM 上的 MinIO）

用法：
  node minio-forward.js [--port 9000] [--target 192.168.137.128:9000]

说明：
  本机监听 --port（默认 ${DEFAULT_PORT}，绑 0.0.0.0），把请求透明转发到 --target（默认 ${DEFAULT_TARGET}）。
  后端下发 URL 的取址：① minio.public-base-url 配了用它；② 没配按请求 Host 推导 + 端口 minio.public-port
  （默认 9000）；③ 无请求上下文则内网 endpoint。所以只要本工具端口 = minio.public-port，就无需任何配置。
  手机连不上时先用管理员 PowerShell 给该端口放行入站（只放行 LocalSubnet）。
  生产环境不要用本工具：应把 minio.public-base-url 配成公网 endpoint 或 CDN 域名。
`);
}

/** 启动时探一下目标是否可达（失败只是警告，不拦启动：可能 MinIO 还没起来） */
function probeTarget(host, port) {
  return new Promise((resolve) => {
    const req = http.request(
      { host, port, method: 'GET', path: '/', timeout: 3000 },
      (res) => {
        res.resume();
        resolve({ ok: true, status: res.statusCode });
      },
    );
    req.on('timeout', () => {
      req.destroy(new Error('连接超时'));
    });
    req.on('error', (error) => resolve({ ok: false, message: error.message }));
    req.end();
  });
}

/** 从响应头里挑出要打印的 content-type / content-length，便于肉眼确认图片是不是真的在传 */
function describeResponse(statusCode, headers) {
  const type = headers['content-type'] || '(无 content-type)';
  const length = headers['content-length'];
  return `${statusCode} ${type}${length ? ` ${length}B` : ''}`;
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  if (options.help) {
    printUsage();
    return;
  }
  const { port, targetHost, targetPort } = options;

  const server = http.createServer((req, res) => {
    // 透传 method / path（含 query）/ headers：headers 里只有 host 会被 Node 改成对端，
    // public-read 的匿名 GET 不参与签名，所以行为与直连 MinIO 一致
    const proxyReq = http.request(
      {
        host: targetHost,
        port: targetPort,
        method: req.method,
        path: req.url,
        headers: req.headers,
      },
      (proxyRes) => {
        // 状态码 + 响应头（含 Content-Type / Content-Length）原样回给客户端；body 是二进制，直接 pipe
        res.writeHead(proxyRes.statusCode, proxyRes.headers);
        proxyRes.pipe(res);
        console.log(`[转发] ${req.method} ${req.url} → ${describeResponse(proxyRes.statusCode, proxyRes.headers)}`);
      },
    );
    proxyReq.on('error', (error) => {
      // 目标不可达就明确报出来，别让客户端看到一个莫名其妙的挂起
      console.error(`[失败] ${req.method} ${req.url} → ${targetHost}:${targetPort} 不可达：${error.message}`);
      if (!res.headersSent) {
        res.writeHead(502, { 'Content-Type': 'text/plain; charset=utf-8' });
      }
      res.end(`minio-forward: 转发到 ${targetHost}:${targetPort} 失败：${error.message}\n`);
    });
    // 客户端断开时把上游也掐掉，避免连接泄漏
    req.on('aborted', () => proxyReq.destroy());
    req.pipe(proxyReq);
  });

  // 端口占用要"清晰报错并退出"，不能静默（这里是本工具最常见的失败原因）
  server.on('error', (error) => {
    if (error.code === 'EADDRINUSE') {
      console.error(`\n[启动失败] 本机端口 ${port} 已被占用。`);
      console.error(`  可能原因：① 本机自己就装着 MinIO（它默认也用 9000）；② 已有一个 minio-forward 在跑。`);
      console.error(`  处理办法：换一个端口，例如  node minio-forward.js --port 9100`);
      console.error(`  查占用进程：netstat -ano | findstr :${port}\n`);
    } else if (error.code === 'EACCES') {
      console.error(`\n[启动失败] 没有权限绑定端口 ${port}（1024 以下端口通常需要管理员权限）。`);
      console.error(`  处理办法：换一个高位端口，例如  node minio-forward.js --port 9100\n`);
    } else {
      console.error(`\n[启动失败] ${error.message}\n`);
    }
    process.exit(1);
  });

  // 绑 0.0.0.0：手机等外部设备必须能连进来，绑 127.0.0.1 就没意义了
  server.listen(port, '0.0.0.0', async () => {
    const lanAddresses = listLanIPv4();
    console.log('=========================================================');
    console.log(' MinIO 开发用代转发已启动（仅开发环境使用，生产走公网/CDN）');
    console.log('=========================================================');
    console.log(`  监听      : 0.0.0.0:${port}`);
    console.log(`  转发到    : http://${targetHost}:${targetPort}`);
    console.log('');
    console.log('  本机非回环 IPv4 地址（手机要用的就是其中之一）：');
    if (lanAddresses.length === 0) {
      console.log('    （没找到！这台机器可能没连局域网）');
    } else {
      for (const item of lanAddresses) {
        console.log(`    ${item.address}   [网卡 ${item.name}]`);
      }
    }
    console.log('');
    console.log('  接下来（默认零配置）：');
    console.log('    后端下发图片 URL 的取址是三级（唯一实现在 common 的 ObjectStorageUrlBuilder）：');
    console.log('      ① minio.public-base-url 配了 → 用它（生产填公网/CDN）');
    console.log('      ② 没配 → 按【本次请求的 Host】推导，端口取 minio.public-port');
    console.log('      ③ 没配也没请求上下文 → 回退内网 endpoint（老行为）');
    if (port === 9000) {
      console.log('    本工具正听 9000（= 默认端口）→ ②的端口默认就是 9000，后端不用加任何配置，重启服务即可。');
    } else {
      console.log(`    本工具听的是 ${port}（不是 9000）→ 必须在 Nacos 配 minio.public-port: ${port}，否则图片地址会指向 9000。`);
    }
    console.log('    重启对应后端服务（社区 = miaowang-community / 头像 = miaowang-user）');
    console.log('    （想彻底不依赖请求 Host：也可以显式配 minio.public-base-url，它优先级最高）');
    if (lanAddresses.length > 0) {
      console.log(`     例如： minio.public-base-url: http://${lanAddresses[0].address}:${port}`);
    }
    console.log('');
    console.log('  自检（在手机上打开浏览器访问，应能下到图）：');
    if (lanAddresses.length > 0) {
      console.log(`    http://${lanAddresses[0].address}:${port}/miaowang-community/community/post/<某张图的 objectKey>`);
    }
    console.log('');
    console.log('  ⚠️ 手机连不上时先查 Windows 防火墙：node.exe 第一次监听会被入站规则拦住，');
    console.log('     用**管理员** PowerShell 给这个端口放行（只放行本地子网，不开给公网）：');
    console.log(`       netsh advfirewall firewall add rule name="MiaoWang minio-forward ${port}" dir=in action=allow protocol=TCP localport=${port} remoteip=LocalSubnet`);
    console.log('     （tools/allow-lan-access.ps1 是同一套做法，它开的是网关的 7000，可作模板）');
    console.log('     还要确认：手机和 PC 在同一 Wi-Fi，且手机没开 VPN/代理（访客网络会隔离设备）');
    console.log('');
    console.log('  Ctrl+C 结束转发（结束后记得把进程关干净，别一直占着端口）');
    console.log('=========================================================');

    const probe = await probeTarget(targetHost, targetPort);
    if (probe.ok) {
      console.log(`[自检] 目标 ${targetHost}:${targetPort} 可达（HTTP ${probe.status}）`);
    } else {
      console.log(`[自检] 警告：目标 ${targetHost}:${targetPort} 暂时不可达（${probe.message}）—— 转发会在目标恢复后自动可用`);
    }
  });

  const shutdown = () => {
    console.log('\n[minio-forward] 收到退出信号，正在关闭…');
    server.close(() => process.exit(0));
    // 有 keep-alive 长连接时 close 可能不回调，兜一个超时，保证进程真的退出
    setTimeout(() => process.exit(0), 500).unref();
  };
  process.on('SIGINT', shutdown);
  process.on('SIGTERM', shutdown);
}

main().catch((error) => {
  console.error(`\n[启动失败] ${error.message}\n`);
  console.error('用 --help 查看用法。\n');
  process.exit(1);
});