/**
 * 通用API工具路由
 */

const express = require('express');
const router = express.Router();
const { generateUUID, generatePassword, generateMachineCode } = require('../utils/helpers');
const { asyncHandler } = require('../middleware/error');

// 生成UUID
router.get('/uuid', asyncHandler(async (req, res) => {
  res.json({
    success: true,
    data: {
      uuid: generateUUID()
    }
  });
}));

// 生成随机密码（路径参数方式，保留原有路由）
router.get('/password/:length?', asyncHandler(async (req, res) => {
  const length = Math.min(32, Math.max(6, parseInt(req.params.length) || 12));

  res.json({
    success: true,
    data: {
      password: generatePassword(length),
      length: length
    }
  });
}));

// 生成随机密码（query 参数方式，供前端 /api/utils/generate-password?length=12 调用）
// 【修改说明 - 2026-02-24】
// 修改背景：前端调用 /api/utils/generate-password?length=12，但后端只有 /password/:length? 路由，导致 404
// 解决问题：新增此路由兼容前端的 query 参数调用方式，两种路由均可正常使用
router.get('/generate-password', asyncHandler(async (req, res) => {
  const length = Math.min(32, Math.max(6, parseInt(req.query.length) || 12));

  res.json({
    success: true,
    data: {
      password: generatePassword(length),
      length: length
    }
  });
}));

// 生成机器码
router.get('/machine-code', asyncHandler(async (req, res) => {
  const machineCode = generateMachineCode();

  res.json({
    success: true,
    data: {
      machineCode: machineCode
    }
  });
}));

// 服务器时间
router.get('/time', asyncHandler(async (req, res) => {
  res.json({
    success: true,
    data: {
      timestamp: new Date().toISOString(),
      unix: Math.floor(Date.now() / 1000)
    }
  });
}));

module.exports = router;
