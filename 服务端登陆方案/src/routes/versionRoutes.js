/**
 * 更新日志管理路由
 */

const express = require('express');
const router = express.Router();
const versionController = require('../controllers/versionController');
const { verifyAdmin } = require('../middleware/auth');
const { asyncHandler } = require('../middleware/error');

// 公开接口：获取列表和详情
router.get('/', asyncHandler(versionController.getVersions));
router.get('/:versionId', asyncHandler(versionController.getVersionDetail));

// 需要管理员权限的操作
router.use(verifyAdmin);
router.post('/', asyncHandler(versionController.createVersion));
router.put('/:versionId', asyncHandler(versionController.updateVersion));
router.delete('/:versionId', asyncHandler(versionController.deleteVersion));

module.exports = router;
