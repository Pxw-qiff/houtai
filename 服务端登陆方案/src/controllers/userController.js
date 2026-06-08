/**
 * 用户控制器
 */

const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const { v4: uuidv4 } = require("uuid");
const { getPool } = require("../config/database");
const { handleApiError } = require("../middleware/error");
const { syncUserToNewApi } = require("../services/newApiSyncService");
const { ensureCreditAccount } = require("../utils/creditAccount");
const {
  JWT_CONFIG,
  ERROR_MESSAGES,
  SUCCESS_MESSAGES,
  LOGIN_FIELD_MAP,
  DEFAULT_TRIAL_DURATION_HOURS,
} = require("../config/constants");

/**
 * 用户登录
 */
const login = async (req, res) => {
  try {
    const {
      loginField,
      password,
      loginMode = "username",
      machineCode,
    } = req.body;

    if (!loginField || !password) {
      return res.status(400).json({
        success: false,
        error: ERROR_MESSAGES.MISSING_PARAMS,
      });
    }

    if (!LOGIN_FIELD_MAP[loginMode]) {
      return res.status(400).json({
        success: false,
        error: ERROR_MESSAGES.INVALID_LOGIN_MODE,
      });
    }

    const connection = await getPool().getConnection();

    try {
      // 查询用户 - 使用 SELECT * 避免字段不存在问题
      const [users] = await connection.execute(
        `SELECT * FROM users WHERE ${LOGIN_FIELD_MAP[loginMode]} = ?`,
        [loginField],
      );

      if (users.length === 0) {
        return res.status(401).json({
          success: false,
          error: ERROR_MESSAGES.USER_NOT_FOUND,
        });
      }

      const user = users[0];

      // 验证密码 - 使用 password 字段
      const isValidPassword = await bcrypt.compare(password, user.password);
      if (!isValidPassword) {
        return res.status(401).json({
          success: false,
          error: ERROR_MESSAGES.INVALID_PASSWORD,
        });
      }

      // 检查用户状态 - 使用 is_banned 字段
      if (user.is_banned > 0) {
        return res.status(403).json({
          success: false,
          error: ERROR_MESSAGES.USER_BANNED,
        });
      }

      // 检查是否已删除 - 使用 is_deleted 字段
      if (user.is_deleted > 0) {
        return res.status(403).json({
          success: false,
          error: ERROR_MESSAGES.USER_DELETED,
        });
      }

      // 【强制校验机器码】
      if (!machineCode) {
        return res.status(400).json({
          success: false,
          error: ERROR_MESSAGES.MISSING_MACHINE_CODE,
        });
      }

      // 【机器码校验逻辑】
      // 1. 获取用户现有机器码列表
      const currentMachineCodes = user.machine_code
        ? user.machine_code.split("|")
        : [];
      const maxMachineCount = user.max_machine_count || 1; // 默认允许1台设备

      // 2. 检查当前机器码是否已存在
      const isDeviceRegistered = currentMachineCodes.includes(machineCode);

      if (!isDeviceRegistered) {
        // 3. 如果是新设备，检查是否达到上限
        // 注意：过滤空字符串，确保计算准确
        const validMachineCount = currentMachineCodes.filter(
          (code) => code && code.trim() !== "",
        ).length;

        if (validMachineCount >= maxMachineCount) {
          return res.status(403).json({
            success: false,
            error: ERROR_MESSAGES.DEVICE_LIMIT_EXCEEDED,
          });
        }

        // 4. 未达上限，追加新机器码
        const newMachineCodes = [...currentMachineCodes, machineCode].join("|");

        await connection.execute(
          "UPDATE users SET machine_code = ?, updated_at = NOW() WHERE user_uuid = ?",
          [newMachineCodes, user.user_uuid],
        );
      }

      // 生成JWT令牌
      const token = jwt.sign(
        { userUuid: user.user_uuid, username: user.username },
        JWT_CONFIG.SECRET,
        { expiresIn: JWT_CONFIG.EXPIRES_IN },
      );

      // 更新登录信息：先设置为离线，再设置为在线，避免遗留状态
      await connection.execute(
        "UPDATE users SET is_online = 0, last_login_time = NOW(), updated_at = NOW() WHERE user_uuid = ?",
        [user.user_uuid],
      );

      // 然后设置为在线
      await connection.execute(
        "UPDATE users SET is_online = 1, updated_at = NOW() WHERE user_uuid = ?",
        [user.user_uuid],
      );

      const newApiSyncResult = await syncUserToNewApi({
        user_uuid: user.user_uuid,
        username: user.username,
        is_banned: 0,
        is_deleted: 0,
      });
      const newApiSync = {
        success: newApiSyncResult.success === true,
      };
      let newApiKey = null;

      if (newApiSyncResult.skipped) {
        newApiSync.skipped = true;
      }
      if (newApiSyncResult.error) {
        newApiSync.error = newApiSyncResult.error;
      }
      if (newApiSyncResult.success && newApiSyncResult.data) {
        newApiKey = newApiSyncResult.data.key || null;
        newApiSync.userId = newApiSyncResult.data.userId;
        newApiSync.tokenId = newApiSyncResult.data.tokenId;
        newApiSync.tokenName = newApiSyncResult.data.tokenName;
        newApiSync.maskedKey = newApiSyncResult.data.maskedKey;
        newApiSync.tokenCreated = newApiSyncResult.data.tokenCreated;
      } else if (!newApiSyncResult.skipped) {
        console.error("[new-api同步失败] 用户登录后同步失败", {
          userUuid: user.user_uuid,
          error: newApiSyncResult.error,
        });
      }

      // 返回完整的用户信息（与旧版一致）
      res.json({
        success: true,
        message: SUCCESS_MESSAGES.LOGIN_SUCCESS,
        data: {
          token,
          newApiKey,
          newApiSync,
          user: {
            user_uuid: user.user_uuid,
            username: user.username,
            phone: user.phone,
            email: user.email,
            user_permission_level: user.user_permission_level,
            admin_permissions: user.admin_permissions,
            user_channel_description: user.user_channel_description,
            expire_time: user.expire_time,
            is_trial_user: user.is_trial_user,
            trial_activated_at: user.trial_activated_at,
            trial_duration_hours: user.trial_duration_hours,
            max_machine_count: user.max_machine_count,
            login_count: user.login_count,
          },
        },
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, "登录失败");
  }
};

/**
 * 用户注册
 */
const register = async (req, res) => {
  try {
    const { username, password, phone, email } = req.body;

    if (!username || !password) {
      return res.status(400).json({
        success: false,
        error: "用户名和密码不能为空",
      });
    }

    const connection = await getPool().getConnection();

    try {
      // 检查用户名是否已存在（包含软删除用户）
      const [existingUsers] = await connection.execute(
        "SELECT * FROM users WHERE username = ? OR phone = ? OR email = ?",
        [username, phone || "", email || ""],
      );

      if (existingUsers.length > 0) {
        const existingUser = existingUsers[0];

        // 如果用户已存在且未删除，则报错
        if (existingUser.is_deleted === 0) {
          return res.status(400).json({
            success: false,
            error: "用户名、手机号或邮箱已存在",
          });
        }

        // 【复活逻辑】如果用户已软删除，则执行复活
        // 1. 生成新密码哈希
        const passwordHash = await bcrypt.hash(password, 12);
        // 2. 重新计算试用期
        const trialEndTime = new Date(
          Date.now() + DEFAULT_TRIAL_DURATION_HOURS * 60 * 60 * 1000,
        );

        // 3. 更新用户信息：重置删除状态、更新密码、重置机器码等
        await connection.execute(
          `UPDATE users SET
            is_deleted = 0,
            is_banned = 0,
            password = ?,
            trial_end_time = ?,
            machine_code = NULL,
            is_online = 0,
            updated_at = NOW()
           WHERE user_uuid = ?`,
          [passwordHash, trialEndTime, existingUser.user_uuid],
        );

        await ensureCreditAccount(connection, existingUser.user_uuid);

        const newApiSync = await syncUserToNewApi({
          user_uuid: existingUser.user_uuid,
          username: existingUser.username,
          is_banned: 0,
          is_deleted: 0,
        });
        if (!newApiSync.success) {
          console.error("[new-api同步失败] 用户复活后同步失败", {
            userUuid: existingUser.user_uuid,
            error: newApiSync.error,
          });
        }

        return res.json({
          success: true,
          message: SUCCESS_MESSAGES.REGISTER_SUCCESS,
          data: {
            userUuid: existingUser.user_uuid,
            username: existingUser.username,
            trialEndTime,
          },
        });
      }

      // 生成用户UUID和密码哈希
      const userUuid = uuidv4();
      const passwordHash = await bcrypt.hash(password, 12);
      const trialEndTime = new Date(
        Date.now() + DEFAULT_TRIAL_DURATION_HOURS * 60 * 60 * 1000,
      );

      // 插入新用户
      // 注意：这里需要再次确认用户名未被占用（虽然上面查过了，但为了保险起见）
      // 使用 INSERT IGNORE 或者 try-catch 处理唯一索引冲突
      try {
        await connection.execute(
          `INSERT INTO users (user_uuid, username, password, phone, email, is_banned,
           trial_end_time, created_at) VALUES (?, ?, ?, ?, ?, 0, ?, NOW())`,
          [
            userUuid,
            username,
            passwordHash,
            phone || null,
            email || null,
            trialEndTime,
          ],
        );
        await ensureCreditAccount(connection, userUuid);
      } catch (insertError) {
        if (insertError.code === "ER_DUP_ENTRY") {
          return res.status(400).json({
            success: false,
            error: "用户名、手机号或邮箱已存在",
          });
        }
        throw insertError;
      }

      const newApiSync = await syncUserToNewApi({
        user_uuid: userUuid,
        username,
        is_banned: 0,
        is_deleted: 0,
      });
      if (!newApiSync.success) {
        console.error("[new-api同步失败] 用户注册后同步失败", {
          userUuid,
          error: newApiSync.error,
        });
      }

      res.json({
        success: true,
        message: SUCCESS_MESSAGES.REGISTER_SUCCESS,
        data: {
          userUuid,
          username,
          trialEndTime,
        },
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, "注册失败");
  }
};

/**
 * 用户登出
 */
const logout = async (req, res) => {
  try {
    const user = req.user;

    if (user?.user_uuid) {
      const connection = await getPool().getConnection();
      try {
        await connection.execute(
          "UPDATE users SET is_online = 0, updated_at = NOW() WHERE user_uuid = ?",
          [user.user_uuid],
        );
      } finally {
        connection.release();
      }
    }

    res.json({
      success: true,
      message: SUCCESS_MESSAGES.LOGOUT_SUCCESS,
    });
  } catch (error) {
    handleApiError(res, error, "登出失败");
  }
};

/**
 * 验证会话
 */
const validateSession = async (req, res) => {
  try {
    const user = req.user; // 来自认证中间件

    const connection = await getPool().getConnection();

    try {
      const [users] = await connection.execute(
        "SELECT trial_end_time, subscription_end_time FROM users WHERE user_uuid = ?",
        [user.user_uuid],
      );

      if (users.length === 0) {
        return res.status(401).json({
          success: false,
          error: ERROR_MESSAGES.USER_NOT_FOUND,
        });
      }

      res.json({
        success: true,
        data: {
          user: {
            userUuid: user.user_uuid,
            username: user.username,
            status: user.status,
            trialEndTime: users[0].trial_end_time,
            subscriptionEndTime: users[0].subscription_end_time,
          },
        },
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, "会话验证失败");
  }
};

/**
 * 心跳检测
 */
const heartbeat = async (req, res) => {
  try {
    res.json({
      success: true,
      timestamp: new Date().toISOString(),
      server: "ai-anime-api",
    });
  } catch (error) {
    handleApiError(res, error, "心跳检测失败");
  }
};

/**
 * 业务心跳更新
 * 用于维持在线状态和统计使用时长
 */
const updateHeartbeat = async (req, res) => {
  try {
    const { userUuid, machineCode, addUsageDuration } = req.body;

    if (!userUuid) {
      return res.status(400).json({
        success: false,
        error: ERROR_MESSAGES.MISSING_PARAMS,
      });
    }

    const connection = await getPool().getConnection();

    try {
      // 1. 验证用户是否存在
      const [users] = await connection.execute(
        "SELECT user_uuid FROM users WHERE user_uuid = ?",
        [userUuid],
      );

      if (users.length === 0) {
        return res.status(404).json({
          success: false,
          error: ERROR_MESSAGES.USER_NOT_FOUND,
        });
      }

      // 2. 构建更新语句
      // 总是更新在线状态
      let updateQuery = "UPDATE users SET is_online = 1, updated_at = NOW()";
      const queryParams = [];

      // 如果需要增加使用时长 (每3分钟调用一次，增加3分钟)
      if (addUsageDuration) {
        updateQuery +=
          ", total_usage_duration = total_usage_duration + 3, today_login_duration_minutes = today_login_duration_minutes + 3";
      }

      // 如果提供了机器码，也可以选择性更新（这里暂不覆盖，避免复杂性）
      // if (machineCode) { ... }

      updateQuery += " WHERE user_uuid = ?";
      queryParams.push(userUuid);

      await connection.execute(updateQuery, queryParams);

      res.json({
        success: true,
        timestamp: new Date().toISOString(),
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, "心跳更新失败");
  }
};

/**
 * 修改密码
 */
const changePassword = async (req, res) => {
  try {
    const { oldPassword, newPassword } = req.body;
    const user = req.user; // 来自 verifyToken 中间件

    // 参数验证
    if (!oldPassword || !newPassword) {
      return res.status(400).json({
        success: false,
        error: ERROR_MESSAGES.MISSING_PARAMS,
      });
    }

    const connection = await getPool().getConnection();

    try {
      // 查询用户当前密码
      const [users] = await connection.execute(
        "SELECT password FROM users WHERE user_uuid = ?",
        [user.user_uuid],
      );

      if (users.length === 0) {
        return res.status(404).json({
          success: false,
          error: ERROR_MESSAGES.USER_NOT_FOUND,
        });
      }

      // 验证旧密码
      const isValidPassword = await bcrypt.compare(
        oldPassword,
        users[0].password,
      );
      if (!isValidPassword) {
        return res.status(401).json({
          success: false,
          error: ERROR_MESSAGES.INVALID_PASSWORD,
        });
      }

      // 加密新密码（使用与注册相同的盐值12）
      const newPasswordHash = await bcrypt.hash(newPassword, 12);

      // 更新密码
      await connection.execute(
        "UPDATE users SET password = ?, updated_at = NOW() WHERE user_uuid = ?",
        [newPasswordHash, user.user_uuid],
      );

      res.json({
        success: true,
        message: SUCCESS_MESSAGES.UPDATE_SUCCESS,
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, "修改密码失败");
  }
};

module.exports = {
  login,
  register,
  logout,
  validateSession,
  heartbeat,
  updateHeartbeat,
  changePassword,
};
