/**
 * 通知管理路由
 */

const express = require('express');
const router = express.Router();
const noticeController = require('../controllers/noticeController');
const { verifyAdmin } = require('../middleware/auth');
const { asyncHandler } = require('../middleware/error');

// 获取通知列表 (公开或仅需登录，此处先改为公开获取，类似于组件更新日志)
router.get('/', asyncHandler(noticeController.getNotices));

// 下面的接口需要管理员权限
router.use(verifyAdmin);

// 创建通知
router.post('/', asyncHandler(noticeController.createNotice));

// 更新通知
router.put('/:noticeId', asyncHandler(noticeController.updateNotice));

// 删除通知
router.delete('/:noticeId', asyncHandler(noticeController.deleteNotice));

module.exports = router;
