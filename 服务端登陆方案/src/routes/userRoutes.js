/**
 * 用户路由
 */

const express = require("express");
const router = express.Router();
const userController = require("../controllers/userController");
const { verifyToken } = require("../middleware/auth");
const { asyncHandler } = require("../middleware/error");

// 用户登录
router.post("/login", asyncHandler(userController.login));

// 用户注册
router.post("/register", asyncHandler(userController.register));

// 用户登出
router.post("/logout", verifyToken, asyncHandler(userController.logout));

// 验证会话
router.get(
  "/validate-session",
  verifyToken,
  asyncHandler(userController.validateSession),
);

// 业务心跳 (更新在线状态和时长)
router.post("/heartbeat", asyncHandler(userController.updateHeartbeat));

// 服务存活检测 (GET)
router.get("/heartbeat", asyncHandler(userController.heartbeat));

// 健康检查
router.get("/health", asyncHandler(userController.heartbeat));

// 修改密码
router.post(
  "/change-password",
  verifyToken,
  asyncHandler(userController.changePassword),
);

module.exports = router;
