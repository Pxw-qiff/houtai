/**
 * 数据库表结构初始化模块
 * 在应用启动时确保 users 表存在且字段完整
 */

const { getPool } = require('../config/database');
const { backfillCreditAccounts } = require('./creditAccount');

/**
 * 创建 users 表的 SQL
 */
const CREATE_USERS_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS users (
  user_uuid VARCHAR(36) NOT NULL,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  phone VARCHAR(32) DEFAULT NULL,
  email VARCHAR(128) DEFAULT NULL,
  user_permission_level INT NOT NULL DEFAULT 1,
  admin_permissions INT NOT NULL DEFAULT 1,
  user_description VARCHAR(255) DEFAULT NULL,
  user_channel_description VARCHAR(255) DEFAULT NULL,
  is_trial_user TINYINT NOT NULL DEFAULT 1,
  is_online TINYINT NOT NULL DEFAULT 0,
  is_banned TINYINT NOT NULL DEFAULT 0,
  ban_reason VARCHAR(255) DEFAULT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  machine_code TEXT,
  max_machine_count INT NOT NULL DEFAULT 1,
  login_count INT NOT NULL DEFAULT 0,
  trial_duration_hours INT NOT NULL DEFAULT 24,
  trial_duration_minutes INT DEFAULT NULL,
  trial_activated_at DATETIME DEFAULT NULL,
  trial_expiry_time DATETIME DEFAULT NULL,
  trial_end_time DATETIME DEFAULT NULL,
  subscription_end_time DATETIME DEFAULT NULL,
  expire_time DATETIME DEFAULT NULL,
  register_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_time DATETIME DEFAULT NULL,
  total_usage_duration INT NOT NULL DEFAULT 0,
  today_login_duration_minutes INT NOT NULL DEFAULT 0,
  created_by VARCHAR(64) DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_uuid),
  UNIQUE KEY uk_users_username (username),
  KEY idx_users_status (is_deleted, is_banned),
  KEY idx_users_register_time (register_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
`;

/**
 * 需要确保存在的字段定义
 * 格式：字段名 => ALTER TABLE ADD COLUMN 语句
 */
const REQUIRED_COLUMNS = {
  user_uuid: "user_uuid VARCHAR(36) NOT NULL",
  username: "username VARCHAR(64) NOT NULL",
  password: "password VARCHAR(255) NOT NULL",
  phone: "phone VARCHAR(32) DEFAULT NULL",
  email: "email VARCHAR(128) DEFAULT NULL",
  user_permission_level: "user_permission_level INT NOT NULL DEFAULT 1",
  admin_permissions: "admin_permissions INT NOT NULL DEFAULT 1",
  user_description: "user_description VARCHAR(255) DEFAULT NULL",
  user_channel_description: "user_channel_description VARCHAR(255) DEFAULT NULL",
  is_trial_user: "is_trial_user TINYINT NOT NULL DEFAULT 1",
  is_online: "is_online TINYINT NOT NULL DEFAULT 0",
  is_banned: "is_banned TINYINT NOT NULL DEFAULT 0",
  ban_reason: "ban_reason VARCHAR(255) DEFAULT NULL",
  is_deleted: "is_deleted TINYINT NOT NULL DEFAULT 0",
  machine_code: "machine_code TEXT",
  max_machine_count: "max_machine_count INT NOT NULL DEFAULT 1",
  login_count: "login_count INT NOT NULL DEFAULT 0",
  trial_duration_hours: "trial_duration_hours INT NOT NULL DEFAULT 24",
  trial_duration_minutes: "trial_duration_minutes INT DEFAULT NULL",
  trial_activated_at: "trial_activated_at DATETIME DEFAULT NULL",
  trial_expiry_time: "trial_expiry_time DATETIME DEFAULT NULL",
  trial_end_time: "trial_end_time DATETIME DEFAULT NULL",
  subscription_end_time: "subscription_end_time DATETIME DEFAULT NULL",
  expire_time: "expire_time DATETIME DEFAULT NULL",
  register_time: "register_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
  last_login_time: "last_login_time DATETIME DEFAULT NULL",
  total_usage_duration: "total_usage_duration INT NOT NULL DEFAULT 0",
  today_login_duration_minutes: "today_login_duration_minutes INT NOT NULL DEFAULT 0",
  created_by: "created_by VARCHAR(64) DEFAULT NULL",
  created_at: "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP",
  updated_at: "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
};

/**
 * 检查字段是否存在
 */
async function columnExists(connection, columnName) {
  const [rows] = await connection.execute(
    `SELECT COUNT(*) as count 
     FROM information_schema.columns 
     WHERE table_schema = DATABASE() 
     AND table_name = 'users' 
     AND column_name = ?`,
    [columnName]
  );
  return rows[0].count > 0;
}

/**
 * 确保 users 表字段完整
 */
async function ensureUsersTableColumns(connection) {
  console.log('[db-init] 检查 users 表字段完整性...');
  
  let addedCount = 0;
  
  for (const [columnName, columnDef] of Object.entries(REQUIRED_COLUMNS)) {
    const exists = await columnExists(connection, columnName);
    
    if (!exists) {
      console.log(`[db-init] 添加缺失字段: ${columnName}`);
      await connection.execute(`ALTER TABLE users ADD COLUMN ${columnDef}`);
      addedCount++;
    }
  }
  
  if (addedCount > 0) {
    console.log(`[db-init] 成功补齐 ${addedCount} 个缺失字段`);
  } else {
    console.log('[db-init] users 表字段完整，无需修复');
  }
}

/**
 * 创建 credit_account 积分账户表的 SQL
 */
const CREATE_CREDIT_ACCOUNT_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS credit_account (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_uuid VARCHAR(36) NOT NULL COMMENT '用户UUID',
  total_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '总积分',
  available_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '可用积分',
  frozen_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '冻结积分',
  total_recharge_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '累计充值积分',
  total_consume_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '累计消费积分',
  total_refund_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '累计退款积分',
  status INT NOT NULL DEFAULT 1 COMMENT '账户状态：1-正常，2-冻结',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_credit_account_user (user_uuid),
  KEY idx_credit_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分账户表';
`;

/**
 * 创建 credit_flow 积分流水表的 SQL
 */
const CREATE_CREDIT_FLOW_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS credit_flow (
  id BIGINT NOT NULL AUTO_INCREMENT,
  flow_no VARCHAR(64) NOT NULL COMMENT '唯一流水号',
  user_uuid VARCHAR(36) NOT NULL COMMENT '用户UUID',
  account_id BIGINT NOT NULL COMMENT '积分账户ID',
  before_available_points DECIMAL(20, 6) NOT NULL COMMENT '变动前可用积分',
  change_available_points DECIMAL(20, 6) NOT NULL COMMENT '可用积分变动量',
  after_available_points DECIMAL(20, 6) NOT NULL COMMENT '变动后可用积分',
  before_frozen_points DECIMAL(20, 6) NOT NULL COMMENT '变动前冻结积分',
  change_frozen_points DECIMAL(20, 6) NOT NULL COMMENT '冻结积分变动量',
  after_frozen_points DECIMAL(20, 6) NOT NULL COMMENT '变动后冻结积分',
  biz_type VARCHAR(32) NOT NULL COMMENT '业务类型',
  biz_order_no VARCHAR(64) DEFAULT NULL COMMENT '关联业务单号',
  operator_uuid VARCHAR(36) DEFAULT NULL COMMENT '操作人UUID',
  operator_name VARCHAR(64) DEFAULT NULL COMMENT '操作人展示名称',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_credit_flow_no (flow_no),
  KEY idx_credit_flow_user (user_uuid),
  KEY idx_credit_flow_account (account_id),
  KEY idx_credit_flow_biz (biz_type, biz_order_no),
  KEY idx_credit_flow_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表（只增不改不删）';
`;

/**
 * 创建 credit_consume_record 用户端消费记录读模型表的 SQL
 */
const CREATE_CREDIT_CONSUME_RECORD_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS credit_consume_record (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_uuid VARCHAR(36) NOT NULL COMMENT '用户UUID',
  biz_type VARCHAR(64) NOT NULL COMMENT '业务类型',
  biz_order_no VARCHAR(128) NOT NULL COMMENT '业务单号',
  title VARCHAR(255) DEFAULT NULL COMMENT '展示标题',
  status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '结算状态',
  status_text VARCHAR(64) DEFAULT NULL COMMENT '状态文案',
  pre_deduct_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '预扣积分',
  actual_cost_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '实际消费积分',
  refund_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '返还积分',
  extra_deduct_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '结算补扣积分',
  frozen_points DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '当前冻结积分',
  balance_before DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '业务开始前可用积分',
  balance_after DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '当前阶段后可用积分',
  started_at DATETIME DEFAULT NULL COMMENT '开始时间',
  finished_at DATETIME DEFAULT NULL COMMENT '完成时间',
  latest_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近更新时间',
  remark VARCHAR(500) DEFAULT NULL COMMENT '展示备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_consume_record_biz (user_uuid, biz_type, biz_order_no),
  KEY idx_consume_record_user_latest (user_uuid, latest_at, id),
  KEY idx_consume_record_biz (biz_type, biz_order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户端消费记录读模型表';
`;

/**
 * 创建 recharge_orders 充值订单表的 SQL
 */
const CREATE_RECHARGE_ORDERS_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS recharge_orders (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_no VARCHAR(64) NOT NULL COMMENT '唯一充值订单号',
  trade_no VARCHAR(128) DEFAULT NULL COMMENT '第三方支付平台交易流水号',
  user_uuid VARCHAR(36) NOT NULL COMMENT '用户UUID',
  amount DECIMAL(10, 2) NOT NULL COMMENT '充值金额（元）',
  points DECIMAL(20, 6) NOT NULL COMMENT '到账积分',
  charge_ratio DECIMAL(10, 2) NOT NULL COMMENT '充值比例',
  pay_type VARCHAR(16) NOT NULL COMMENT '支付方式',
  status VARCHAR(16) NOT NULL COMMENT '订单状态',
  expire_time DATETIME DEFAULT NULL COMMENT '支付过期时间',
  pay_time DATETIME DEFAULT NULL COMMENT '支付成功时间',
  credited_time DATETIME DEFAULT NULL COMMENT '积分入账时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_recharge_order_no (order_no),
  KEY idx_recharge_user (user_uuid),
  KEY idx_recharge_status (status),
  KEY idx_recharge_trade_no (trade_no),
  KEY idx_recharge_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值订单表';
`;

/**
 * 创建 sys_task 异步任务表的 SQL
 */
const CREATE_SYS_TASK_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS sys_task (
  id BIGINT NOT NULL AUTO_INCREMENT,
  task_id VARCHAR(64) NOT NULL COMMENT '对外暴露的任务ID',
  upstream_task_id VARCHAR(128) DEFAULT NULL COMMENT '上游服务商真实任务ID',
  user_uuid VARCHAR(36) NOT NULL COMMENT '用户UUID',
  platform VARCHAR(32) NOT NULL COMMENT '平台类型',
  action VARCHAR(32) NOT NULL COMMENT '任务动作',
  status VARCHAR(16) NOT NULL COMMENT '任务状态',
  quota DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '预扣积分',
  result_url TEXT COMMENT '生成结果URL',
  fail_reason VARCHAR(255) DEFAULT NULL COMMENT '失败原因',
  created_at BIGINT NOT NULL COMMENT '创建时间戳',
  updated_at BIGINT NOT NULL COMMENT '更新时间戳',
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_id (task_id),
  KEY idx_task_user (user_uuid),
  KEY idx_task_status (status),
  KEY idx_task_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='生图与生视频异步任务流水表';
`;

/**
 * 创建 sys_config 系统配置表的 SQL
 */
const CREATE_SYS_CONFIG_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS sys_config (
  id BIGINT NOT NULL AUTO_INCREMENT,
  config_key VARCHAR(64) NOT NULL COMMENT '配置键',
  config_value TEXT NOT NULL COMMENT '配置值',
  remark VARCHAR(255) DEFAULT NULL COMMENT '配置备注说明',
  update_time BIGINT NOT NULL COMMENT '更新时间戳',
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='全局系统业务配置表';
`;

/**
 * 创建 sys_audit_log 审计日志表的 SQL
 */
const CREATE_SYS_AUDIT_LOG_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS sys_audit_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  operator_uuid VARCHAR(36) NOT NULL COMMENT '操作人用户UUID',
  operator_name VARCHAR(64) NOT NULL COMMENT '操作人用户名',
  action VARCHAR(64) NOT NULL COMMENT '操作动作',
  target VARCHAR(255) DEFAULT NULL COMMENT '操作目标',
  ip VARCHAR(64) DEFAULT NULL COMMENT '操作人IP',
  params TEXT COMMENT '操作请求参数',
  result VARCHAR(16) NOT NULL COMMENT '操作结果',
  create_time BIGINT NOT NULL COMMENT '创建时间戳',
  PRIMARY KEY (id),
  KEY idx_audit_operator (operator_uuid),
  KEY idx_audit_action (action),
  KEY idx_audit_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统管理员操作审计日志表';
`;

/**
 * 创建 balance_flow 旧积分流水表的 SQL（保留兼容）
 */
const CREATE_BALANCE_FLOW_TABLE_SQL = `
CREATE TABLE IF NOT EXISTS balance_flow (
  id BIGINT NOT NULL AUTO_INCREMENT,
  flow_no VARCHAR(64) NOT NULL COMMENT '唯一流水号',
  user_uuid VARCHAR(36) NOT NULL COMMENT '用户UUID',
  before_quota BIGINT NOT NULL COMMENT '变动前额度',
  change_quota BIGINT NOT NULL COMMENT '变动额度（正数为增加，负数为减少）',
  after_quota BIGINT NOT NULL COMMENT '变动后额度',
  biz_type VARCHAR(32) NOT NULL COMMENT '业务类型',
  biz_order_no VARCHAR(64) DEFAULT NULL COMMENT '关联业务单号',
  operator VARCHAR(64) DEFAULT NULL COMMENT '操作人',
  remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
  create_time BIGINT NOT NULL COMMENT '创建时间戳',
  PRIMARY KEY (id),
  UNIQUE KEY uk_balance_flow_no (flow_no),
  KEY idx_balance_flow_user (user_uuid),
  KEY idx_balance_flow_created (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户积分变动流水表（后续将迁移到credit_flow）';
`;

const POINT_DECIMAL_COLUMNS = [
  {
    table: 'credit_account',
    columns: [
      { name: 'total_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '总积分'" },
      { name: 'available_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '可用积分'" },
      { name: 'frozen_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '冻结积分'" },
      { name: 'total_recharge_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '累计充值积分'" },
      { name: 'total_consume_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '累计消费积分'" },
      { name: 'total_refund_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '累计退款积分'" }
    ]
  },
  {
    table: 'credit_flow',
    columns: [
      { name: 'before_available_points', definition: "DECIMAL(20, 6) NOT NULL COMMENT '变动前可用积分'" },
      { name: 'change_available_points', definition: "DECIMAL(20, 6) NOT NULL COMMENT '可用积分变动量'" },
      { name: 'after_available_points', definition: "DECIMAL(20, 6) NOT NULL COMMENT '变动后可用积分'" },
      { name: 'before_frozen_points', definition: "DECIMAL(20, 6) NOT NULL COMMENT '变动前冻结积分'" },
      { name: 'change_frozen_points', definition: "DECIMAL(20, 6) NOT NULL COMMENT '冻结积分变动量'" },
      { name: 'after_frozen_points', definition: "DECIMAL(20, 6) NOT NULL COMMENT '变动后冻结积分'" }
    ]
  },
  {
    table: 'credit_consume_record',
    columns: [
      { name: 'pre_deduct_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '预扣积分'" },
      { name: 'actual_cost_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '实际消费积分'" },
      { name: 'refund_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '返还积分'" },
      { name: 'extra_deduct_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '结算补扣积分'" },
      { name: 'frozen_points', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '当前冻结积分'" },
      { name: 'balance_before', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '业务开始前可用积分'" },
      { name: 'balance_after', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '当前阶段后可用积分'" }
    ]
  },
  {
    table: 'recharge_orders',
    columns: [
      { name: 'points', definition: "DECIMAL(20, 6) NOT NULL COMMENT '到账积分'" }
    ]
  },
  {
    table: 'sys_task',
    columns: [
      { name: 'quota', definition: "DECIMAL(20, 6) NOT NULL DEFAULT 0.000000 COMMENT '预扣积分'" }
    ]
  }
];

/**
 * 确保积分相关字段支持 6 位小数
 */
async function ensurePointDecimalPrecision(connection) {
  console.log('[db-init] 检查积分字段小数精度...');

  let changedCount = 0;

  for (const { table, columns } of POINT_DECIMAL_COLUMNS) {
    for (const column of columns) {
      const [rows] = await connection.execute(
        `SELECT NUMERIC_PRECISION AS numericPrecision, NUMERIC_SCALE AS numericScale
         FROM information_schema.columns
         WHERE table_schema = DATABASE()
           AND table_name = ?
           AND column_name = ?`,
        [table, column.name]
      );

      if (rows.length === 0) {
        continue;
      }

      const numericPrecision = Number(rows[0].numericPrecision || 0);
      const numericScale = Number(rows[0].numericScale || 0);
      if (numericPrecision >= 20 && numericScale >= 6) {
        continue;
      }

      await connection.execute(`ALTER TABLE \`${table}\` MODIFY COLUMN \`${column.name}\` ${column.definition}`);
      changedCount++;
      console.log(`[db-init] 已调整 ${table}.${column.name} 为 DECIMAL(20, 6)`);
    }
  }

  if (changedCount === 0) {
    console.log('[db-init] 积分字段小数精度完整，无需调整');
  }
}

/**
 * 初始化系统配置默认数据
 */
async function initSystemConfig(connection) {
  console.log('[db-init] 检查系统配置默认数据...');
  
  const defaultConfigs = [
    { config_key: 'recharge_ratio', config_value: '100', remark: '充值比例：1元=100积分' },
    { config_key: 'min_recharge_amount', config_value: '1', remark: '最小充值金额（元）' },
    { config_key: 'max_recharge_amount', config_value: '10000', remark: '最大充值金额（元）' }
  ];
  
  for (const config of defaultConfigs) {
    const [rows] = await connection.execute(
      'SELECT COUNT(*) as count FROM sys_config WHERE config_key = ?',
      [config.config_key]
    );
    
    if (rows[0].count === 0) {
      await connection.execute(
        'INSERT INTO sys_config (config_key, config_value, remark, update_time) VALUES (?, ?, ?, ?)',
        [config.config_key, config.config_value, config.remark, Date.now()]
      );
      console.log(`[db-init] 插入默认配置: ${config.config_key}`);
    }
  }
}

/**
 * 初始化数据库表结构
 * 在应用启动时调用，确保所有表存在且字段完整
 */
async function initDatabaseSchema() {
  const connection = await getPool().getConnection();
  
  try {
    console.log('[db-init] 开始初始化数据库表结构...');
    
    // 1. 创建 users 表（如果不存在）
    await connection.execute(CREATE_USERS_TABLE_SQL);
    console.log('[db-init] users 表已就绪');
    
    // 2. 确保所有必需字段存在
    await ensureUsersTableColumns(connection);
    
    // 3. 创建 Java 后端需要的表
    await connection.execute(CREATE_CREDIT_ACCOUNT_TABLE_SQL);
    console.log('[db-init] credit_account 表已就绪');
    
    await connection.execute(CREATE_CREDIT_FLOW_TABLE_SQL);
    console.log('[db-init] credit_flow 表已就绪');

    await connection.execute(CREATE_CREDIT_CONSUME_RECORD_TABLE_SQL);
    console.log('[db-init] credit_consume_record 表已就绪');
    
    await connection.execute(CREATE_RECHARGE_ORDERS_TABLE_SQL);
    console.log('[db-init] recharge_orders 表已就绪');
    
    await connection.execute(CREATE_SYS_TASK_TABLE_SQL);
    console.log('[db-init] sys_task 表已就绪');
    
    await connection.execute(CREATE_SYS_CONFIG_TABLE_SQL);
    console.log('[db-init] sys_config 表已就绪');
    
    await connection.execute(CREATE_SYS_AUDIT_LOG_TABLE_SQL);
    console.log('[db-init] sys_audit_log 表已就绪');
    
    await connection.execute(CREATE_BALANCE_FLOW_TABLE_SQL);
    console.log('[db-init] balance_flow 表已就绪');

    // 4. 确保积分字段支持 6 位小数
    await ensurePointDecimalPrecision(connection);
    
    // 5. 初始化系统配置默认数据
    await initSystemConfig(connection);

    // 6. 给历史有效用户补齐积分账户
    const backfilledCount = await backfillCreditAccounts(connection);
    if (backfilledCount > 0) {
      console.log(`[db-init] 已补齐 ${backfilledCount} 个历史用户积分账户`);
    } else {
      console.log('[db-init] 历史用户积分账户完整，无需补齐');
    }
    
    console.log('[db-init] 数据库表结构初始化完成');
  } catch (error) {
    console.error('[db-init] 数据库表结构初始化失败:', error.message);
    throw error;
  } finally {
    connection.release();
  }
}

module.exports = { initDatabaseSchema };