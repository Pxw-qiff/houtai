/**
 * 系统监控控制器
 */

const { getPool } = require('../config/database');
const { handleApiError } = require('../middleware/error');

// 服务器启动时间
const serverStartTime = Date.now();

/**
 * 系统健康检查
 */
const healthCheck = async (req, res) => {
  try {
    const connection = await getPool().getConnection();
    
    try {
      await connection.ping();
      
      const [dbStats] = await connection.execute('SHOW STATUS LIKE "Threads_connected"');
      const [userCount] = await connection.execute('SELECT COUNT(*) as count FROM users');
      
      res.json({
        status: 'ok',
        timestamp: new Date().toISOString(),
        service: 'ai-anime-api',
        uptime_seconds: Math.floor((Date.now() - serverStartTime) / 1000),
        database: 'ok',
        database_connections: dbStats[0]?.Value || 0,
        total_users: userCount[0].count
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    console.error('[健康检查失败]', error);
    res.status(500).json({
      status: 'error',
      timestamp: new Date().toISOString(),
      service: 'ai-anime-api',
      error: error.message
    });
  }
};

/**
 * 系统状态监控
 */
const getSystemStatus = async (req, res) => {
  try {
    const connection = await getPool().getConnection();
    
    try {
      // 获取数据库状态
      const [dbStatus] = await connection.execute(`
        SHOW STATUS WHERE Variable_name IN (
          'Threads_connected', 'Threads_running', 'Queries', 'Uptime',
          'Innodb_buffer_pool_pages_total', 'Innodb_buffer_pool_pages_free'
        )
      `);
      
      // 获取用户活动统计
      const [userActivity] = await connection.execute(`
        SELECT 
          COUNT(CASE WHEN last_login >= DATE_SUB(NOW(), INTERVAL 1 DAY) THEN 1 END) as active_24h,
          COUNT(CASE WHEN last_login >= DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 END) as active_7d,
          COUNT(CASE WHEN last_login >= DATE_SUB(NOW(), INTERVAL 30 DAY) THEN 1 END) as active_30d
        FROM users WHERE status = 'active'
      `);
      
      const dbStatusObj = {};
      dbStatus.forEach(row => {
        dbStatusObj[row.Variable_name] = row.Value;
      });
      
      res.json({
        success: true,
        data: {
          timestamp: new Date().toISOString(),
          uptime_seconds: Math.floor((Date.now() - serverStartTime) / 1000),
          database: dbStatusObj,
          user_activity: userActivity[0],
          memory_usage: process.memoryUsage(),
          cpu_usage: process.cpuUsage()
        }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取系统状态失败');
  }
};

/**
 * 获取数据库健康状态
 */
const getDatabaseHealth = async (req, res) => {
  try {
    const connection = await getPool().getConnection();
    
    try {
      await connection.ping();
      
      const [variables] = await connection.execute(`
        SHOW VARIABLES WHERE Variable_name IN (
          'max_connections', 'innodb_buffer_pool_size', 'query_cache_size'
        )
      `);
      
      const [status] = await connection.execute(`
        SHOW STATUS WHERE Variable_name IN (
          'Threads_connected', 'Threads_running', 'Aborted_connects', 'Uptime'
        )
      `);
      
      const variablesObj = {};
      variables.forEach(row => {
        variablesObj[row.Variable_name] = row.Value;
      });
      
      const statusObj = {};
      status.forEach(row => {
        statusObj[row.Variable_name] = row.Value;
      });
      
      res.json({
        success: true,
        data: {
          status: 'healthy',
          timestamp: new Date().toISOString(),
          variables: variablesObj,
          status: statusObj
        }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取数据库健康状态失败');
  }
};

/**
 * 获取系统统计信息
 */
const getSystemStats = async (req, res) => {
  try {
    const connection = await getPool().getConnection();
    
    try {
      // 获取各种统计数据
      const [userStats] = await connection.execute(`
        SELECT 
          COUNT(*) as total_users,
          COUNT(CASE WHEN status = 'active' THEN 1 END) as active_users,
          COUNT(CASE WHEN DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) THEN 1 END) as new_users_7d,
          COUNT(CASE WHEN DATE(created_at) >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) THEN 1 END) as new_users_30d
        FROM users
      `);
      
      const [loginStats] = await connection.execute(`
        SELECT 
          COUNT(CASE WHEN DATE(last_login) = CURDATE() THEN 1 END) as logins_today,
          COUNT(CASE WHEN DATE(last_login) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) THEN 1 END) as logins_7d,
          COUNT(CASE WHEN DATE(last_login) >= DATE_SUB(CURDATE(), INTERVAL 30 DAY) THEN 1 END) as logins_30d
        FROM users WHERE last_login IS NOT NULL
      `);
      
      res.json({
        success: true,
        data: {
          timestamp: new Date().toISOString(),
          uptime_seconds: Math.floor((Date.now() - serverStartTime) / 1000),
          user_statistics: userStats[0],
          login_statistics: loginStats[0],
          system_info: {
            node_version: process.version,
            platform: process.platform,
            memory_usage: process.memoryUsage(),
            cpu_usage: process.cpuUsage()
          }
        }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取系统统计信息失败');
  }
};

module.exports = {
  healthCheck,
  getSystemStatus,
  getDatabaseHealth,
  getSystemStats
};
