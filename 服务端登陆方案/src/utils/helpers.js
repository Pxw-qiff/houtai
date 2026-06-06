/**
 * 通用工具函数
 */

const { v4: uuidv4 } = require('uuid');

/**
 * 生成UUID
 */
const generateUUID = () => {
  return uuidv4();
};

/**
 * 生成随机密码
 */
const generatePassword = (length = 12) => {
  const charset = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*';
  let password = '';
  for (let i = 0; i < length; i++) {
    password += charset.charAt(Math.floor(Math.random() * charset.length));
  }
  return password;
};

/**
 * 生成机器码（模拟）
 */
const generateMachineCode = () => {
  const timestamp = Date.now().toString(16)
  const random = Math.random().toString(36).substring(2, 15)
  return (timestamp + random).toUpperCase()
};

/**
 * 格式化日期
 */
const formatDate = (date, format = 'YYYY-MM-DD HH:mm:ss') => {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const hours = String(d.getHours()).padStart(2, '0');
  const minutes = String(d.getMinutes()).padStart(2, '0');
  const seconds = String(d.getSeconds()).padStart(2, '0');
  
  return format
    .replace('YYYY', year)
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds);
};

/**
 * 延迟函数
 */
const delay = (ms) => {
  return new Promise(resolve => setTimeout(resolve, ms));
};

/**
 * 安全的JSON解析
 */
const safeJsonParse = (str, defaultValue = null) => {
  try {
    return JSON.parse(str);
  } catch (error) {
    return defaultValue;
  }
};

/**
 * 验证邮箱格式
 */
const isValidEmail = (email) => {
  const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  return emailRegex.test(email);
};

/**
 * 验证手机号格式
 */
const isValidPhone = (phone) => {
  const phoneRegex = /^1[3-9]\d{9}$/;
  return phoneRegex.test(phone);
};

/**
 * 清理对象中的空值
 */
const cleanObject = (obj) => {
  const cleaned = {};
  for (const [key, value] of Object.entries(obj)) {
    if (value !== null && value !== undefined && value !== '') {
      cleaned[key] = value;
    }
  }
  return cleaned;
};

/**
 * 分页参数处理
 */
const processPaginationParams = (query) => {
  const page = Math.max(1, parseInt(query.page) || 1);
  const limit = Math.min(100, Math.max(1, parseInt(query.limit) || 20));
  const offset = (page - 1) * limit;
  
  return { page, limit, offset };
};

module.exports = {
  generateUUID,
  generatePassword,
  generateMachineCode,
  formatDate,
  delay,
  safeJsonParse,
  isValidEmail,
  isValidPhone,
  cleanObject,
  processPaginationParams
};
