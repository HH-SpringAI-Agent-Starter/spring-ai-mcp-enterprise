#!/usr/bin/env node
/**
 * MCP Enterprise Server — Streamable HTTP 客户端示例 (Node.js)
 *
 * 演示 2026-07-28 无状态协议 + Streamable HTTP 双通道调用：
 *   POST /api/mcp/v2/message — JSON-RPC 请求（initialize / tools/list / tools/call）
 *   GET  /api/mcp/v2/stream  — server→client 事件流（endpoint 事件 + listChanged 通知）
 *   POST /api/mcp/v2/notify  — 触发 tools/listChanged 广播
 *
 * 零依赖，仅用 Node 18+ 原生 fetch + ReadableStream。
 *
 * 使用方式:
 *   node mcp_streamable_client.mjs
 *   MCP_API_KEY=my-key node mcp_streamable_client.mjs
 */

const BASE_URL = process.env.MCP_BASE_URL || 'http://localhost:8081';
const API_KEY = process.env.MCP_API_KEY || 'default-admin-key';
const PROTOCOL_VERSION = '2026-07-28';

const headers = { 'Content-Type': 'application/json', 'X-API-Key': API_KEY };

async function main() {
  console.log('🚀 MCP Enterprise Streamable HTTP Client (Node.js)');
  console.log('===================================================');

  // 1. 协议能力声明
  console.log('\n📡 1. 拉取协议能力声明 (GET /api/mcp/v2)...');
  const caps = await get('/api/mcp/v2');
  console.log('   protocolVersion:', caps.protocolVersion);
  console.log('   transports:', caps.transport.supportedTransports.join(', '));
  console.log('   streamableHttp 通道:', caps.transport.streamableHttp.message);

  // 2. initialize（无状态，携带协议版本）
  console.log('\n🔌 2. initialize (POST /api/mcp/v2/message)...');
  const init = await rpc('init-1', 'initialize', {
    protocolVersion: PROTOCOL_VERSION,
    clientInfo: { name: 'node-streamable-demo', version: '1.0.0' }
  });
  const serverInfo = init.result.serverInfo;
  console.log(`   server: ${serverInfo.name} v${serverInfo.version}`);

  // 3. tools/list
  console.log('\n🔧 3. tools/list...');
  const toolsResp = await rpc('tools-1', 'tools/list', {});
  const tools = toolsResp.result.tools;
  console.log(`   工具数: ${tools.length}`);
  for (const t of tools) console.log(`   - ${t.name} (${t.description})`);

  // 4. tools/call — 调用计算器
  console.log('\n⚡ 4. tools/call calculator...');
  const callResp = await rpc('call-1', 'tools/call', {
    name: 'calculator',
    arguments: { expression: '1 + 2 * 3' }
  });
  const content = callResp.result?.content;
  console.log(content && content.length
    ? `   计算结果: ${content[0].text}`
    : `   响应: ${JSON.stringify(callResp)}`);

  // 5. 事件流演示
  console.log('\n🔔 5. Streamable HTTP 事件流演示...');
  await demoEventStream();

  console.log('\n✅ Node.js Streamable HTTP 示例运行完成!');
}

/** 事件流演示：打开 GET /stream，触发 notify，收到 listChanged 后关闭 */
async function demoEventStream() {
  const controller = new AbortController();
  const streamUrl = `${BASE_URL}/api/mcp/v2/stream`;

  // 打开 SSE 事件流
  const resp = await fetch(streamUrl, {
    headers: { Accept: 'text/event-stream', 'X-API-Key': API_KEY },
    signal: controller.signal
  });
  console.log(`   ✅ 事件流已连接 (HTTP ${resp.status})`);
  if (!resp.body) throw new Error('响应无 body');

  // 后台解析 SSE 行
  const eventPromise = (async () => {
    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let currentEvent = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('event:')) currentEvent = trimmed.slice(6).trim();
        else if (trimmed.startsWith('data:')) {
          console.log(`   事件 [${currentEvent}]: ${trimmed.slice(5).trim()}`);
        }
      }
    }
  })().catch(() => {}); // AbortError 忽略

  // 触发 tools/listChanged 广播
  console.log('   触发 notify 广播...');
  const notify = await post('/api/mcp/v2/notify', {});
  console.log(`   广播投递: ${notify.delivered} 个连接`);

  // 等待 2 秒接收广播（listChanged 事件应出现在日志中），然后关闭
  await new Promise(r => setTimeout(r, 2000));
  controller.abort();
  console.log('   📴 事件流已关闭');
}

// ===== HTTP 工具方法 =====

async function get(path) {
  const resp = await fetch(BASE_URL + path, { headers });
  return resp.json();
}

async function post(path, body) {
  const resp = await fetch(BASE_URL + path, {
    method: 'POST', headers, body: JSON.stringify(body)
  });
  return resp.json();
}

async function rpc(id, method, params) {
  return post('/api/mcp/v2/message', { jsonrpc: '2.0', id, method, params });
}

main().catch(err => {
  console.error('❌ 运行失败:', err.message);
  process.exit(1);
});
