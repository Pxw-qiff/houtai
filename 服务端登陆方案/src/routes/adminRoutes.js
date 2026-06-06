/**
 * 管理员路由
 */

const express = require('express');
const router = express.Router();
const adminController = require('../controllers/adminController');
const { verifyAdmin } = require('../middleware/auth');
const { asyncHandler } = require('../middleware/error');

// 用户管理
router.get('/users', verifyAdmin, asyncHandler(adminController.getUserList));
router.post('/users', verifyAdmin, asyncHandler(adminController.createUser));
router.post('/users/batch', verifyAdmin, asyncHandler(adminController.batchCreateUsers));
router.get('/users/search/:username', verifyAdmin, asyncHandler(adminController.getUserByUsername));
router.get('/users/:userUuid', verifyAdmin, asyncHandler(adminController.getUserByUuid));
router.get('/users/stats', verifyAdmin, asyncHandler(adminController.getUserStats));
router.put('/users/:userUuid', verifyAdmin, asyncHandler(adminController.updateUser));
router.put('/users/:userUuid/status', verifyAdmin, asyncHandler(adminController.updateUserStatus));
router.delete('/users/:userUuid', verifyAdmin, asyncHandler(adminController.deleteUser));
router.put('/users/batch', verifyAdmin, asyncHandler(adminController.batchUpdateUsers));
router.delete('/users/batch', verifyAdmin, asyncHandler(adminController.batchDeleteUsers));

// 机器管理
router.get('/machines/aggregations', verifyAdmin, asyncHandler(adminController.getMachineAggregations));

// 通知管理
router.get('/notices', verifyAdmin, asyncHandler(adminController.getNotices));
router.get('/notices/:noticeId', verifyAdmin, asyncHandler(adminController.getNoticeById));
router.post('/notices', verifyAdmin, asyncHandler(adminController.createNotice));
router.put('/notices/:noticeId', verifyAdmin, asyncHandler(adminController.updateNotice));
router.delete('/notices/:noticeId', verifyAdmin, asyncHandler(adminController.deleteNotice));

module.exports = router;
