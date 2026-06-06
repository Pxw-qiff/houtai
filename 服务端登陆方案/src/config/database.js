/**
 * 数据库连接配置和连接池管理
 */

const mysql = require("mysql2/promise");
const { DB_CONFIG } = require("./constants");

// 数据库连接池
let pool = null;

/**
 * 创建数据库连接池
 */
function createPool() {
  if (pool) {
    return pool;
  }

  pool = mysql.createPool({
    host: DB_CONFIG.HOST,
    port: DB_CONFIG.PORT,
    user: DB_CONFIG.USER,
    password: DB_CONFIG.PASSWORD,
    database: DB_CONFIG.DATABASE,
    charset: DB_CONFIG.CHARSET,
    timezone: DB_CONFIG.TIMEZONE,
    connectionLimit: DB_CONFIG.CONNECTION_LIMIT,

    // 保持连接活跃
    enableKeepAlive: true,
    keepAliveInitialDelay: 0,

    // 连接管理
    queueLimit: 0, // 无限制排队

    // MySQL2 连接池配置（仅支持这些选项）
    waitForConnections: true, // 连接池满时是否等待

    // 调试模式（生产环境建议关闭）
    debug: process.env.NODE_ENV === "development",
  });

  // 连接池事件监听
  pool.on("connection", (connection) => {
    console.log(`[数据库] 新连接建立: ${connection.threadId}`);
  });

  pool.on("error", (err) => {
    console.error("[数据库] 连接池错误:", err);
    if (err.code === "PROTOCOL_CONNECTION_LOST") {
      console.log("[数据库] 连接丢失，尝试重新连接...");
    }
  });

  return pool;
}

/**
 * 获取数据库连接池
 */
function getPool() {
  if (!pool) {
    return createPool();
  }
  return pool;
}

/**
 * 测试数据库连接
 */
async function testConnection() {
  try {
    const connection = await getPool().getConnection();
    await connection.ping();
    connection.release();
    console.log("✅ 数据库连接测试成功");
    return true;
  } catch (error) {
    console.error("❌ 数据库连接失败:", error.message);
    return false;
  }
}

/**
 * 关闭数据库连接池
 */
async function closePool() {
  if (pool) {
    await pool.end();
    pool = null;
    console.log("[数据库] 连接池已关闭");
  }
}

/**
 * 执行SQL查询（带错误处理）
 */
async function executeQuery(sql, params = []) {
  const connection = await getPool().getConnection();
  try {
    const [results] = await connection.execute(sql, params);
    return results;
  } finally {
    connection.release();
  }
}

/**
 * 执行事务
 */
async function executeTransaction(queries) {
  const connection = await getPool().getConnection();
  try {
    await connection.beginTransaction();

    const results = [];
    for (const { sql, params } of queries) {
      const [result] = await connection.execute(sql, params);
      results.push(result);
    }

    await connection.commit();
    return results;
  } catch (error) {
    await connection.rollback();
    throw error;
  } finally {
    connection.release();
  }
}

module.exports = {
  createPool,
  getPool,
  testConnection,
  closePool,
  executeQuery,
  executeTransaction,
};
