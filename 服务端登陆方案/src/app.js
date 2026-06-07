/**
 * AI Anime API 主应用文件
 * 模块化架构 - 统一API服务
 */

const express = require('express');
require('dotenv').config();

// 导入配置
const { API_PREFIXES, SERVER_CONFIG } = require('./config/constants');
const { createPool, testConnection, closePool, getPool } = require('./config/database');
const { initDatabaseSchema } = require('./utils/initDatabase');
const { initDefaultAdmin } = require('./utils/initAdmin');

// 导入中间件
const { requestLogger, notFoundHandler, globalErrorHandler } = require('./middleware/error');

// 导入路由
const userRoutes = require('./routes/userRoutes');
const adminRoutes = require('./routes/adminRoutes');
const noticeRoutes = require('./routes/noticeRoutes');
const versionRoutes = require('./routes/versionRoutes');
const systemRoutes = require('./routes/systemRoutes');
const fileRoutes = require('./routes/fileRoutes');
const apiRoutes = require('./routes/apiRoutes');

// 创建Express应用
const app = express();

// 服务器启动时间
const serverStartTime = Date.now();

// ==================== 基础中间件配置 ====================

// IP 白名单限制（只允许本地和 Docker/Nginx 内网访问）
const allowedIPs = ['127.0.0.1', '::1', 'localhost']

/**
 * 判断请求来源是否来自容器私有网络
 */
const isPrivateNetworkIP = (ip) => {
  return /^10\./.test(ip) || /^192\.168\./.test(ip) || /^172\.(1[6-9]|2\d|3[0-1])\./.test(ip)
}

app.use((req, res, next) => {
  const clientIP = req.ip || req.connection.remoteAddress || req.socket.remoteAddress
  const normalizedIP = clientIP.replace(/^::ffff:/, '')

  const isAllowed = allowedIPs.includes(clientIP) || allowedIPs.includes(normalizedIP) || isPrivateNetworkIP(normalizedIP)

  if (!isAllowed) {
    console.warn(`[安全] 拒绝来自 ${clientIP} 的请求`)
    return res.status(403).json({ success: false, error: 'Access denied' })
  }

  next()
})

// 请求解析
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

// 请求日志
app.use(requestLogger);

// ==================== 路由配置 ====================

// 用户登录相关路由
// 【修改说明 - 2026-02-27】
// 修改背景：前端 baseURL 为 /ai-anime/user/login，拼接 endpoint 后变成 /ai-anime/user/login/login
// 解决问题：将挂载路径从 /ai-anime/user 改为 /ai-anime/user/login，匹配前端实际请求路径
app.use(`${API_PREFIXES.USER_LOGIN}/login`, userRoutes);

// 管理功能路由 - 通知管理 (独立文件)
// 将挂载在 /ai-anime/admin/notices
app.use(`${API_PREFIXES.ADMIN}/notices`, noticeRoutes);

// 管理功能路由 - 更新日志管理 (独立文件)
// 将挂载在 /ai-anime/admin/updates
app.use(`${API_PREFIXES.ADMIN}/updates`, versionRoutes);

// 管理功能路由 - 其他用户管理
app.use(API_PREFIXES.ADMIN, adminRoutes);

// 系统监控路由
app.use(API_PREFIXES.SYSTEM, systemRoutes);

// 文件服务路由
app.use(API_PREFIXES.FILES, fileRoutes);

// 通用API工具路由
app.use(API_PREFIXES.API_GENERAL, apiRoutes);
// 【修改说明 - 2026-02-24】兼容前端通过 /ai-anime/api/utils/xxx 调用的路径
app.use(`${API_PREFIXES.API_GENERAL}/utils`, apiRoutes);

// 根路径健康检查
app.get('/', (req, res) => {
  res.json({
    service: 'ai-anime-api',
    version: '2.0.0',
    status: 'running',
    timestamp: new Date().toISOString(),
    uptime_seconds: Math.floor((Date.now() - serverStartTime) / 1000),
    endpoints: {
      user_login: API_PREFIXES.USER_LOGIN,
      admin: API_PREFIXES.ADMIN,
      system: API_PREFIXES.SYSTEM,
      files: API_PREFIXES.FILES,
      api_tools: API_PREFIXES.API_GENERAL
    }
  });
});

// ==================== 错误处理 ====================

// 404处理
app.use(notFoundHandler);

// 全局错误处理
app.use(globalErrorHandler);

// ==================== 服务器启动 ====================

/**
 * 启动服务器
 */
async function startServer() {
  try {
    // 初始化数据库连接池
    console.log('[启动] 初始化数据库连接池...');
    createPool();

    // 测试数据库连接
    const dbConnected = await testConnection();
    if (!dbConnected) {
      console.error('❌ 数据库连接失败，服务器将无法正常工作');
    } else {
      // 数据库连接成功后，先初始化表结构
      try {
        await initDatabaseSchema();
      } catch (error) {
        console.error('[启动] 数据库表结构初始化失败:', error.message);
        throw error; // 表结构初始化失败应中止启动
      }

      // 表结构就绪后，初始化默认管理员账户
      try {
        await initDefaultAdmin();
      } catch (error) {
        console.warn('[启动] 初始化管理员账户时出错（非致命）:', error.message);
      }
    }

    // 启动HTTP服务器
    const server = app.listen(SERVER_CONFIG.PORT, SERVER_CONFIG.HOST, () => {
      console.log('==========================================');
      console.log('✅ AI Anime API服务器已启动');
      console.log(`📍 监听地址: ${SERVER_CONFIG.HOST}:${SERVER_CONFIG.PORT}`);
      console.log(`🗄️  数据库: ${process.env.DB_NAME || 'chuamgwei_gateway'}`);
      console.log(`📊 架构: 模块化单体应用`);
      console.log('📋 API端点:');
      console.log(`   - 用户登录: ${API_PREFIXES.USER_LOGIN}`);
      console.log(`   - 管理功能: ${API_PREFIXES.ADMIN}`);
      console.log(`   - 系统监控: ${API_PREFIXES.SYSTEM}`);
      console.log(`   - 文件服务: ${API_PREFIXES.FILES}`);
      console.log(`   - 通用工具: ${API_PREFIXES.API_GENERAL}`);
      console.log('==========================================');
    });

    // 优雅关闭处理
    const gracefulShutdown = async (signal) => {
      console.log(`\n收到${signal}信号，正在关闭服务器...`);

      server.close(async () => {
        console.log('HTTP服务器已关闭');

        try {
          await closePool();
          console.log('数据库连接池已关闭');
        } catch (error) {
          console.error('关闭数据库连接池时出错:', error);
        }

        console.log('服务器已完全关闭');
        process.exit(0);
      });

      // 强制退出超时
      setTimeout(() => {
        console.error('强制退出服务器');
        process.exit(1);
      }, 10000);
    };

    // 启动定时清理离线用户任务（每5分钟执行一次）
    const CLEAN_OFFLINE_INTERVAL = 5 * 60 * 1000;
    const OFFLINE_THRESHOLD_MINUTES = 10;

    const cleanupOfflineUsers = async () => {
      try {
        const connection = await getPool().getConnection();
        try {
          const [result] = await connection.execute(
            `UPDATE users SET is_online = 0 
             WHERE is_online = 1 
             AND updated_at < DATE_SUB(NOW(), INTERVAL ? MINUTE)`,
            [OFFLINE_THRESHOLD_MINUTES]
          );
          if (result.affectedRows > 0) {
            console.log(`[定时清理] 清理了 ${result.affectedRows} 个离线用户`);
          }
        } finally {
          connection.release();
        }
      } catch (error) {
        console.error('[定时清理] 清理离线用户失败:', error);
      }
    };

    // 立即执行一次清理
    setTimeout(cleanupOfflineUsers, 1000);

    // 设置定时清理
    const cleanupTimer = setInterval(cleanupOfflineUsers, CLEAN_OFFLINE_INTERVAL);

    // 监听关闭信号
    process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
    process.on('SIGINT', () => gracefulShutdown('SIGINT'));

    return server;
  } catch (error) {
    console.error('❌ 服务器启动失败:', error);
    process.exit(1);
  }
}

// 如果直接运行此文件，则启动服务器
if (require.main === module) {
  startServer();
}

module.exports = { app, startServer };
