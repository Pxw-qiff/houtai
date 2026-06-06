/**
 * 错误处理中间件
 */

const { ERROR_MESSAGES } = require('../config/constants');

/**
 * 统一错误处理函数
 */
const handleApiError = (res, error, customMessage = null) => {
  console.error('[API错误]', error);
  
  // 根据错误类型返回不同的状态码和消息
  if (error.code === 'ER_ACCESS_DENIED_ERROR') {
    return res.status(500).json({
      success: false,
      error: ERROR_MESSAGES.DATABASE_ERROR
    });
  }
  
  if (error.code === 'ER_BAD_DB_ERROR') {
    return res.status(500).json({
      success: false,
      error: ERROR_MESSAGES.DATABASE_ERROR
    });
  }
  
  if (error.name === 'ValidationError') {
    return res.status(400).json({
      success: false,
      error: ERROR_MESSAGES.VALIDATION_ERROR,
      details: error.message
    });
  }
  
  // 默认服务器错误
  return res.status(500).json({
    success: false,
    error: customMessage || ERROR_MESSAGES.SERVER_ERROR
  });
};

/**
 * 404 错误处理中间件
 */
const notFoundHandler = (req, res) => {
  res.status(404).json({
    success: false,
    error: 'API接口不存在',
    path: req.path,
    method: req.method
  });
};

/**
 * 全局错误处理中间件
 */
const globalErrorHandler = (err, req, res, next) => {
  console.error('[全局错误处理]', {
    error: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method,
    body: req.body,
    query: req.query
  });
  
  // 如果响应已经发送，交给默认错误处理器
  if (res.headersSent) {
    return next(err);
  }
  
  handleApiError(res, err);
};

/**
 * 异步路由错误捕获包装器
 */
const asyncHandler = (fn) => {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
};

/**
 * 请求日志中间件
 */
const requestLogger = (req, res, next) => {
  const start = Date.now();
  const { method, path, ip } = req;
  
  res.on('finish', () => {
    const duration = Date.now() - start;
    const { statusCode } = res;
    
    console.log(`[${new Date().toISOString()}] ${method} ${path} ${statusCode} ${duration}ms ${ip}`);
  });
  
  next();
};

/**
 * 参数验证中间件生成器
 */
const validateParams = (schema) => {
  return (req, res, next) => {
    const { error } = schema.validate(req.body);
    if (error) {
      return res.status(400).json({
        success: false,
        error: ERROR_MESSAGES.VALIDATION_ERROR,
        details: error.details.map(detail => detail.message)
      });
    }
    next();
  };
};

module.exports = {
  handleApiError,
  notFoundHandler,
  globalErrorHandler,
  asyncHandler,
  requestLogger,
  validateParams
};
