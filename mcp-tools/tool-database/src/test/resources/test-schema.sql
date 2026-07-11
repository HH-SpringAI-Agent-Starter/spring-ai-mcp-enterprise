-- 测试用数据库 Schema + 数据
-- H2 嵌入式数据库，用于 DatabaseQueryExecutor 单元测试

CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(100),
    status VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS orders (
    id INT PRIMARY KEY,
    user_id INT,
    product VARCHAR(200),
    amount DECIMAL(10,2),
    status VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 清理旧数据并插入（每次重建数据库数据一致）
DELETE FROM orders;
DELETE FROM users;

INSERT INTO users VALUES (1, 'Alice', 'alice@test.com', 'active');
INSERT INTO users VALUES (2, 'Bob', 'bob@test.com', 'active');
INSERT INTO users VALUES (3, 'Charlie', 'charlie@test.com', 'inactive');

INSERT INTO orders VALUES (1, 1, 'Product A', 100.00, 'completed', '2026-01-01');
INSERT INTO orders VALUES (2, 1, 'Product B', 200.00, 'pending', '2026-02-01');
INSERT INTO orders VALUES (3, 2, 'Product C', 150.00, 'completed', '2026-03-01');
