-- 测试数据库连接和表结构
USE nl2sql_db;

-- 检查csv_files表是否存在
SHOW TABLES LIKE 'csv_files';

-- 检查表结构
DESCRIBE csv_files;

-- 查看表中的数据
SELECT * FROM csv_files LIMIT 5;

-- 检查特定文件
SELECT * FROM csv_files WHERE id = 7;

