/**
 * 系统监控路由
 */

const express = require('express');
const router = express.Router();
const systemController = require('../controllers/systemController');
const { verifyAdmin } = require('../middleware/auth');
const { asyncHandler } = require('../middleware/error');

// 系统健康检查（公开接口）
router.get('/health', asyncHandler(systemController.healthCheck));

// 系统状态监控（需要管理员权限）
router.get('/status', verifyAdmin, asyncHandler(systemController.getSystemStatus));

// 数据库健康状态（需要管理员权限）
router.get('/database/health', verifyAdmin, asyncHandler(systemController.getDatabaseHealth));

// 系统统计信息（需要管理员权限）
router.get('/stats', verifyAdmin, asyncHandler(systemController.getSystemStats));

module.exports = router;
