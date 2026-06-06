/**
 * 应用常量配置
 */

// API路由前缀
const API_PREFIXES = {
  USER_LOGIN: process.env.API_PREFIX || "/ai-anime/user",
  ADMIN: "/ai-anime/admin",
  SYSTEM: "/ai-anime/system",
  FILES: "/ai-anime/files",
  API_GENERAL: "/ai-anime/api",
};

// 登录字段映射
const LOGIN_FIELD_MAP = {
  username: "username",
  phone: "phone",
  email: "email",
};

// 默认试用时长（小时）
const DEFAULT_TRIAL_DURATION_HOURS = 48;

// 错误消息
const ERROR_MESSAGES = {
  MISSING_PARAMS: "请输入完整的登录信息",
  INVALID_LOGIN_MODE: "登录方式无效",
  USER_NOT_FOUND: "用户不存在",
  INVALID_PASSWORD: "密码错误",
  USER_BANNED: "账户已被封禁",
  USER_DELETED: "账户已被删除",
  TRIAL_EXPIRED: "试用期已过期",
  SUBSCRIPTION_EXPIRED: "订阅已过期",
  TOKEN_INVALID: "登录状态已失效",
  TOKEN_EXPIRED: "登录已过期",
  PERMISSION_DENIED: "权限不足",
  SERVER_ERROR: "服务器内部错误",
  DATABASE_ERROR: "数据库连接失败",
  VALIDATION_ERROR: "数据验证失败",
  DEVICE_LIMIT_EXCEEDED: "设备数量已达上限",
  MISSING_MACHINE_CODE: "请提供设备标识",
};

// 成功消息
const SUCCESS_MESSAGES = {
  LOGIN_SUCCESS: "登录成功",
  LOGOUT_SUCCESS: "退出登录成功",
  REGISTER_SUCCESS: "注册成功",
  UPDATE_SUCCESS: "更新成功",
  DELETE_SUCCESS: "删除成功",
  OPERATION_SUCCESS: "操作成功",
};

// JWT配置
const JWT_CONFIG = {
  SECRET: process.env.JWT_SECRET || "change-me",
  EXPIRES_IN: "7d",
  ALGORITHM: "HS256",
};

// 数据库配置：生产环境必须通过环境变量指向共享 users 表所在库
const DB_CONFIG = {
  HOST: process.env.DB_HOST || "127.0.0.1",
  PORT: process.env.DB_PORT || 3306,
  USER: process.env.DB_USER || "root",
  PASSWORD: process.env.DB_PASSWORD || "",
  DATABASE: process.env.DB_NAME || "chuamgwei",
  CONNECTION_LIMIT: 150,
  CHARSET: "utf8mb4",
  TIMEZONE: "+08:00",
};

// 服务器配置
const SERVER_CONFIG = {
  PORT: process.env.PORT || 3100,
  HOST: process.env.HOST || "0.0.0.0",
};

// new-api 服务间同步配置
const NEW_API_CONFIG = {
  BASE_URL: process.env.NEW_API_BASE_URL || "",
  INTERNAL_SECRET: process.env.NEW_API_INTERNAL_SECRET || "",
  SYNC_TIMEOUT_MS: parseInt(process.env.NEW_API_SYNC_TIMEOUT_MS || "5000", 10),
};

module.exports = {
  API_PREFIXES,
  LOGIN_FIELD_MAP,
  DEFAULT_TRIAL_DURATION_HOURS,
  ERROR_MESSAGES,
  SUCCESS_MESSAGES,
  JWT_CONFIG,
  DB_CONFIG,
  SERVER_CONFIG,
  NEW_API_CONFIG,
};
