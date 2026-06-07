-- 添加管理员账户到 users 表
-- 用户名: admin
-- 密码: admin123 (已使用 bcrypt 加密，成本因子 10)
-- 允许 5 台设备登录

INSERT INTO users (
  user_uuid,
  username,
  password,
  email,
  phone,
  is_banned,
  is_deleted,
  machine_code,
  max_machine_count,
  trial_duration_hours,
  trial_expiry_time,
  created_at,
  updated_at
) VALUES (
  '00000000-0000-0000-0000-000000000001',
  'admin',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
  'admin@example.com',
  '',
  0,
  0,
  '',
  5,
  0,
  NULL,
  NOW(),
  NOW()
);

-- 说明：
-- 1. user_uuid 使用固定值便于识别管理员
-- 2. password 是 'admin123' 的 bcrypt 哈希值
-- 3. machine_code 初始为空，登录后自动记录
-- 4. max_machine_count 设为 5，允许多设备登录
-- 5. is_banned=0, is_deleted=0 表示正常状态