-- MCP Enterprise 初始化 SQL
-- 自动创建测试表和示例数据

CREATE TABLE IF NOT EXISTS mcp_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    api_key VARCHAR(64) NOT NULL,
    description VARCHAR(500),
    allowed_tools TEXT COMMENT 'JSON 数组格式',
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS mcp_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_name VARCHAR(100),
    application_id BIGINT,
    request_params TEXT,
    response_summary VARCHAR(500),
    execution_time_ms INT,
    status VARCHAR(20),
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tool_name (tool_name),
    INDEX idx_created_at (created_at)
);

-- 示例数据
INSERT INTO mcp_applications (name, api_key, description, allowed_tools, status)
VALUES ('demo-app', 'demo-api-key-123', 'MCP 演示应用', '["database_query","web_search","system_info"]', 'active')
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO mcp_applications (name, api_key, description, allowed_tools, status)
VALUES ('admin-console', 'admin-api-key-456', '管理控制台', '["system_info"]', 'active')
ON DUPLICATE KEY UPDATE name = name;
