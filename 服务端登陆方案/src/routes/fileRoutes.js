/**
 * 文件服务路由
 */

const express = require('express');
const router = express.Router();
const fileController = require('../controllers/fileController');
const { verifyAdmin, optionalAuth } = require('../middleware/auth');
const { asyncHandler } = require('../middleware/error');

// 更新包下载（公开接口）
router.get('/updates/:platform/:version', asyncHandler(fileController.downloadUpdate));

// 文件上传（需要管理员权限）
router.post('/upload', verifyAdmin, asyncHandler(fileController.uploadFile));

// 文件下载（可选认证）
router.get('/download/:fileId', optionalAuth, asyncHandler(fileController.downloadFile));

// 获取文件信息（可选认证）
router.get('/info/:fileId', optionalAuth, asyncHandler(fileController.getFileInfo));

module.exports = router;
