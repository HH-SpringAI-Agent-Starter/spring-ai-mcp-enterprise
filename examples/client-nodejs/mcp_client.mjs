/**
 * MCP Enterprise Server — Node.js/TypeScript 客户端示例
 *
 * 演示 Node.js 应用如何通过 REST API 调用 MCP Enterprise Server。
 * 支持 API Key 和 OAuth2 Bearer Token 两种认证方式。
 *
 * 使用方式:
 *   npm install
 *   node mcp_client.mjs
 *   # 或
 *   MCP_API_KEY=my-key MCP_AUTH=Bearer node mcp_client.mjs
 */

const BASE_URL = process.env.MCP_URL || 'http://localhost:8081';
const API_KEY = process.env.MCP_API_KEY || 'default-admin-key';
const AUTH_MODE = process.env.MCP_AUTH || 'api-key'; // 'api-key' | 'bearer'

class McpEnterpriseClient {
  constructor(baseUrl, apiKey) {
    this.baseUrl = baseUrl.replace(/\/+$/, '');
    this.apiKey = apiKey;
    this.sessionId = null;
  }

  /**
   * 通用 HTTP 请求
   */
  async _request(method, path, body = null, useAuth = true) {
    const url = `${this.baseUrl}${path}`;
    const headers = { 'Content-Type': 'application/json' };

    if (useAuth) {
      if (AUTH_MODE === 'bearer') {
        headers['Authorization'] = `Bearer ${this.apiKey}`;
      } else {
        headers['X-API-Key'] = this.apiKey;
      }
    }

    const options = { method, headers };
    if (body) {
      options.body = JSON.stringify(body);
    }

    const response = await fetch(url, options);
    const data = await response.json();

    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${data.error || JSON.stringify(data)}`);
    }

    return data;
  }

  /** 健康检查 */
  async health() {
    return this._request('GET', '/api/mcp/health', null, false);
  }

  /** 获取认证信息 */
  async authInfo() {
    return this._request('GET', '/api/auth/login', null, false);
  }

  /** 交换 API Key 为会话令牌 */
  async exchangeToken() {
    const result = await this._request('POST', '/api/auth/exchange');
    if (result.success) {
      this.apiKey = result.token;
      console.log(`   ✅ 已交换为会话令牌（有效期 ${result.expiresIn}s）`);
      console.log(`   📌 后续可用: Authorization: Bearer ${result.token.substring(0, 20)}...`);
    }
    return result;
  }

  /** 验证当前令牌 */
  async verifyToken() {
    return this._request('POST', '/api/auth/verify', null, true);
  }

  /** 获取当前用户信息 */
  async me() {
    return this._request('GET', '/api/auth/me');
  }

  /** 连接 MCP Server */
  async connect(clientName = 'nodejs-demo') {
    const result = await this._request('POST', '/api/mcp/connect', { clientName });
    if (result.success) {
      this.sessionId = result.sessionId;
    }
    return result;
  }

  /** 断开连接 */
  async disconnect() {
    if (!this.sessionId) return { error: 'No active session' };
    const result = await this._request('POST', '/api/mcp/disconnect', {
      sessionId: this.sessionId
    });
    if (result.success) this.sessionId = null;
    return result;
  }

  /** 列出所有工具 */
  async listTools() {
    return this._request('GET', '/api/mcp/tools');
  }

  /** 获取工具详情 */
  async getTool(name) {
    return this._request('GET', `/api/mcp/tools/${encodeURIComponent(name)}`);
  }

  /** 调用工具 */
  async invokeTool(name, params = {}) {
    return this._request('POST', `/api/mcp/tools/${encodeURIComponent(name)}/invoke`, params);
  }

  /** 服务统计 */
  async stats() {
    return this._request('GET', '/api/mcp/stats');
  }
}

async function main() {
  console.log('🚀 MCP Enterprise Node.js Client Demo');
  console.log(`   服务器: ${BASE_URL}`);
  console.log(`   认证模式: ${AUTH_MODE}`);
  console.log('='.repeat(50));

  const client = new McpEnterpriseClient(BASE_URL, API_KEY);

  try {
    // 1. 检查认证状态
    console.log('\n🔐 1. 认证信息...');
    const authInfo = await client.authInfo();
    console.log(`   认证模式: ${authInfo.authMode}`);
    console.log(`   支持的方式: ${authInfo.supportedAuthMethods?.map(m => m.type).join(', ')}`);

    // 2. 交换令牌（仅 API Key 模式）
    if (AUTH_MODE === 'api-key') {
      console.log('\n🔄 2. 交换 API Key → 会话令牌...');
      const exchange = await client.exchangeToken();
      console.log(`   成功: ${exchange.success}`);
    }

    // 3. 验证令牌
    console.log('\n✅ 3. 验证令牌...');
    const verify = await client.verifyToken();
    console.log(`   有效: ${verify.valid}`);

    // 4. 健康检查
    console.log('\n📡 4. 健康检查...');
    const health = await client.health();
    console.log(`   状态: ${health.status}`);
    console.log(`   工具数: ${health.toolCount}`);
    console.log(`   活跃会话: ${health.activeSessions}`);

    // 5. 连接服务
    console.log('\n🔗 5. 连接服务...');
    const conn = await client.connect('nodejs-v2');
    console.log(`   Session ID: ${conn.sessionId}`);
    console.log(`   服务版本: ${conn.serverVersion}`);

    // 6. 列出工具
    console.log('\n🔧 6. 可用工具列表...');
    const tools = await client.listTools();
    console.log(`   总工具数: ${tools.total}`);
    for (const tool of (tools.tools || [])) {
      console.log(`   - ${tool.name} [${tool.category || '通用'}]`);
    }

    // 7. 统计信息
    console.log('\n📊 7. 服务统计...');
    const stats = await client.stats();
    console.log(`   活跃会话: ${stats.sessions?.active}`);

    // 8. 断开连接
    console.log('\n👋 8. 断开连接...');
    const disc = await client.disconnect();
    console.log(`   断开成功: ${disc.success}`);

  } catch (err) {
    console.error('\n❌ 错误:', err.message);
    process.exit(1);
  }

  console.log('\n✅ Node.js 示例运行完成!');
}

// TypeScript 类型定义（方便 TypeScript 用户）
/**
 * @typedef {Object} HealthResponse
 * @property {string} status
 * @property {number} toolCount
 * @property {number} activeSessions
 *
 * @typedef {Object} ToolInfo
 * @property {string} name
 * @property {string} displayName
 * @property {string} category
 * @property {string} description
 */

main().catch(console.error);
