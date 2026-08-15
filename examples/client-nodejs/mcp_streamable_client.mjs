/**
 * MCP Enterprise Server — Node.js Streamable HTTP 客户端示例
 * 基于 2026-07-28 无状态规范
 */

const BASE_URL = process.env.MCP_SERVER_URL || 'http://localhost:8081';
const API_KEY = process.env.MCP_API_KEY || 'default-admin-key';

async function request(path, method = 'GET', body = null) {
  const url = `${BASE_URL}${path}`;
  const options = {
    method,
    headers: {
      'X-API-Key': API_KEY,
      'Content-Type': 'application/json',
    },
  };
  if (body) options.body = JSON.stringify(body);

  const resp = await fetch(url, options);
  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}: ${await resp.text()}`);
  }
  return resp.json();
}

async function main() {
  console.log('🚀 MCP Enterprise Streamable HTTP Client (Node.js)');
  console.log('==================================================');

  console.log('\n📡 1. Server capabilities');
  const caps = await request('/api/mcp/v2');
  console.log('   Protocol:', caps.protocolVersion);
  console.log('   Server:', caps.serverInfo?.name);

  console.log('\n🚀 2. Initialize');
  const init = await request('/api/mcp/v2/initialize', 'POST', {
    protocolVersion: '2026-07-28',
    clientInfo: { name: 'nodejs-streamable-client', version: '1.0.0' },
  });
  console.log('   Initialized:', JSON.stringify(init, null, 2));

  console.log('\n🔧 3. List tools');
  const tools = await request('/api/mcp/v2/tools');
  console.log('   Tools:', JSON.stringify(tools, null, 2));

  console.log('\n⚡ 4. Call tool');
  const result = await request('/api/mcp/v2/tools/call', 'POST', {
    name: 'system_info',
    arguments: {},
  });
  console.log('   Result:', JSON.stringify(result, null, 2));

  console.log('\n✅ Node.js Streamable HTTP 示例运行完成!');
}

main().catch(console.error);
