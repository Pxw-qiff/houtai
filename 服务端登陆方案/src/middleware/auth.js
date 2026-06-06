/**
 * 认证中间件
 */

const jwt = require('jsonwebtoken');
const { getPool } = require('../config/database');
const { JWT_CONFIG, ERROR_MESSAGES } = require('../config/constants');

/**
 * JWT令牌验证中间件
 */
const verifyToken = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        success: false,
        error: ERROR_MESSAGES.TOKEN_INVALID
      });
    }

    const token = authHeader.replace('Bearer ', '');
    const decoded = jwt.verify(token, JWT_CONFIG.SECRET);

    // 验证用户是否存在且状态正常
    const connection = await getPool().getConnection();
    try {
      const [users] = await connection.execute(
        'SELECT user_uuid, username, is_deleted, is_banned FROM users WHERE user_uuid = ?',
        [decoded.userUuid]
      );

      if (users.length === 0) {
        return res.status(401).json({
          success: false,
          error: ERROR_MESSAGES.USER_NOT_FOUND
        });
      }

      const user = users[0];
      if (user.is_deleted !== 0 || user.is_banned === 2) {
        return res.status(403).json({
          success: false,
          error: user.is_banned === 2 ? ERROR_MESSAGES.USER_BANNED : ERROR_MESSAGES.USER_DELETED
        });
      }

      req.user = user;
      next();
    } finally {
      connection.release();
    }
  } catch (error) {
    if (error.name === 'TokenExpiredError') {
      return res.status(401).json({
        success: false,
        error: ERROR_MESSAGES.TOKEN_EXPIRED
      });
    }

    console.error('[认证中间件错误]', error);
    return res.status(401).json({
      success: false,
      error: ERROR_MESSAGES.TOKEN_INVALID
    });
  }
};

/**
 * 管理员权限验证中间件
 */
const verifyAdmin = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        success: false,
        error: '需要管理员权限'
      });
    }

    const token = authHeader.replace('Bearer ', '');
    const decoded = jwt.verify(token, JWT_CONFIG.SECRET);

    const connection = await getPool().getConnection();
    try {
      const [users] = await connection.execute(
        'SELECT user_uuid, username, admin_permissions FROM users WHERE user_uuid = ? AND is_deleted = 0 AND is_banned != 2',
        [decoded.userUuid]
      );

      if (users.length === 0 || users[0].admin_permissions < 2) {
        return res.status(403).json({
          success: false,
          error: '管理员权限不足'
        });
      }

      req.admin = users[0];
      next();
    } finally {
      connection.release();
    }
  } catch (error) {
    console.error('[管理员认证失败]', error);
    res.status(401).json({
      success: false,
      error: '认证失败'
    });
  }
};

/**
 * 可选的认证中间件（不强制要求登录）
 */
const optionalAuth = async (req, res, next) => {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      req.user = null;
      return next();
    }

    const token = authHeader.replace('Bearer ', '');
    const decoded = jwt.verify(token, JWT_CONFIG.SECRET);

    const connection = await getPool().getConnection();
    try {
      const [users] = await connection.execute(
        'SELECT user_uuid, username, is_deleted, is_banned FROM users WHERE user_uuid = ?',
        [decoded.userUuid]
      );

      req.user = users.length > 0 && users[0].is_deleted === 0 && users[0].is_banned !== 2 ? users[0] : null;
      next();
    } finally {
      connection.release();
    }
  } catch (error) {
    req.user = null;
    next();
  }
};

module.exports = {
  verifyToken,
  verifyAdmin,
  optionalAuth
};
