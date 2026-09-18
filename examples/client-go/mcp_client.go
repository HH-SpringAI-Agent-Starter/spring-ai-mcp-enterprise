// MCP Enterprise Server — Go 客户端示例
//
// 演示 Go 应用如何通过 REST API 调用 MCP Enterprise Server。
// 零第三方依赖，仅使用标准库 net/http + encoding/json。
//
// 使用方式：
//   go run mcp_client.go
//   MCP_API_KEY=my-key go run mcp_client.go
//
// 依赖：Go 1.21+（go.mod 见本目录，也可直接 go run 单文件模式）

package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"time"
)

// McpEnterpriseClient MCP Enterprise Server 客户端
type McpEnterpriseClient struct {
	BaseURL   string
	APIKey    string
	SessionID string
	client    *http.Client
}

// NewMcpEnterpriseClient 创建客户端
func NewMcpEnterpriseClient(baseURL, apiKey string) *McpEnterpriseClient {
	if baseURL == "" {
		baseURL = "http://localhost:8081"
	}
	if apiKey == "" {
		apiKey = os.Getenv("MCP_API_KEY")
	}
	if apiKey == "" {
		apiKey = "default-admin-key"
	}
	return &McpEnterpriseClient{
		BaseURL: baseURL,
		APIKey:  apiKey,
		client:  &http.Client{Timeout: 30 * time.Second},
	}
}

// request 通用 HTTP 请求（JSON in / JSON out）
func (c *McpEnterpriseClient) request(method, path string, body map[string]any) (map[string]any, error) {
	var reader io.Reader
	if body != nil {
		data, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		reader = bytes.NewReader(data)
	}

	req, err := http.NewRequest(method, c.BaseURL+path, reader)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-API-Key", c.APIKey)

	resp, err := c.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	raw, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	var result map[string]any
	if err := json.Unmarshal(raw, &result); err != nil {
		return map[string]any{
			"error":  fmt.Sprintf("HTTP %d: %s", resp.StatusCode, string(raw)),
			"status": resp.StatusCode,
		}, nil
	}
	return result, nil
}

func (c *McpEnterpriseClient) health() (map[string]any, error) {
	return c.request(http.MethodGet, "/api/mcp/health", nil)
}

func (c *McpEnterpriseClient) connect(clientName string) (map[string]any, error) {
	result, err := c.request(http.MethodPost, "/api/mcp/connect",
		map[string]any{"clientName": clientName})
	if err == nil {
		if sid, ok := result["sessionId"].(string); ok {
			c.SessionID = sid
		}
	}
	return result, err
}

func (c *McpEnterpriseClient) disconnect() (map[string]any, error) {
	if c.SessionID == "" {
		return map[string]any{"error": "No active session"}, nil
	}
	result, err := c.request(http.MethodPost, "/api/mcp/disconnect",
		map[string]any{"sessionId": c.SessionID})
	if err == nil {
		c.SessionID = ""
	}
	return result, err
}

func (c *McpEnterpriseClient) listTools() (map[string]any, error) {
	return c.request(http.MethodGet, "/api/mcp/tools", nil)
}

func (c *McpEnterpriseClient) getTool(name string) (map[string]any, error) {
	return c.request(http.MethodGet, "/api/mcp/tools/"+name, nil)
}

func (c *McpEnterpriseClient) invokeTool(name string, params map[string]any) (map[string]any, error) {
	return c.request(http.MethodPost, "/api/mcp/tools/"+name+"/invoke", params)
}

func (c *McpEnterpriseClient) stats() (map[string]any, error) {
	return c.request(http.MethodGet, "/api/mcp/stats", nil)
}

func main() {
	fmt.Println("🚀 MCP Enterprise Go Client Demo")
	fmt.Println("=============================================")

	client := NewMcpEnterpriseClient("", "")
	defer client.disconnect()

	// 1. 健康检查
	fmt.Println("\n📡 1. 健康检查...")
	h, err := client.health()
	if err != nil {
		fmt.Printf("   ✗ 请求失败: %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("   状态: %v\n", h["status"])
	fmt.Printf("   工具数: %v\n", h["toolCount"])
	fmt.Printf("   活跃会话: %v\n", h["activeSessions"])

	// 2. 连接服务
	fmt.Println("\n🔗 2. 连接服务...")
	conn, err := client.connect("go-demo")
	if err != nil {
		fmt.Printf("   ✗ %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("   Session ID: %v\n", conn["sessionId"])
	fmt.Printf("   服务版本: %v\n", conn["serverVersion"])

	// 3. 列出工具
	fmt.Println("\n🔧 3. 可用工具列表...")
	tools, err := client.listTools()
	if err != nil {
		fmt.Printf("   ✗ %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("   总工具数: %v\n", tools["total"])
	if list, ok := tools["tools"].([]any); ok {
		for _, t := range list {
			tool, _ := t.(map[string]any)
			fmt.Printf("   - %v (%v) [%v]\n", tool["name"], tool["displayName"], tool["category"])
		}
	}

	// 4. 服务统计
	fmt.Println("\n📊 4. 服务统计...")
	st, err := client.stats()
	if err != nil {
		fmt.Printf("   ✗ %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("   工具总数: %v\n", st["tools"])
	fmt.Printf("   活跃会话: %v\n", st["sessions"])

	// 5. 断开连接
	fmt.Println("\n👋 5. 断开连接...")
	disc, err := client.disconnect()
	if err != nil {
		fmt.Printf("   ✗ %v\n", err)
		os.Exit(1)
	}
	fmt.Printf("   断开成功: %v\n", disc["success"])

	fmt.Println("\n✅ Go 示例运行完成!")
}