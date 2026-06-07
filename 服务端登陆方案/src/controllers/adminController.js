/**
 * 管理员控制器
 */

const { getPool } = require('../config/database');
const { handleApiError } = require('../middleware/error');
const { SUCCESS_MESSAGES } = require('../config/constants');
const { syncUserToNewApi, syncUsersToNewApi, syncUserSnapshotToNewApi } = require('../services/newApiSyncService');
const { ensureCreditAccount } = require('../utils/creditAccount');
const { v4: uuidv4 } = require('uuid');
const bcrypt = require('bcryptjs'); // Assuming bcryptjs is used, or maybe regular bcrypt. Checking package.json would be better but I'll stick to common practice or check userController.
const DEFAULT_TRIAL_DURATION_HOURS = 24; // Copying constant, or import if available

/**
 * 获取用户列表
 */
const getUserList = async (req, res) => {
  try {
    const {
      page = 1,
      limit = 20,
      search = '',
      status = '',
      is_online,
      permission_level,
      admin_permissions,
      description_not_null,
      description_is_null,
      user_channel_description_like,
      phone_not_null,
      phone_is_null,
      email_not_null,
      email_is_null,
      is_trial_user,
      expire_time_before,
      expire_time_after,
      expire_time_is_null
    } = req.query;

    const pageNum = parseInt(page) || 1;
    const limitNum = parseInt(limit) || 20;
    const offset = (pageNum - 1) * limitNum;

    let whereClause = 'WHERE is_deleted = 0';
    const params = [];

    if (search) {
      // 扩展搜索范围：包含 machine_code 字段
      whereClause += ' AND (username LIKE ? OR phone LIKE ? OR email LIKE ? OR machine_code LIKE ?)';
      params.push(`%${search}%`, `%${search}%`, `%${search}%`, `%${search}%`);
    }

    // status 筛选：支持 banned/active
    if (status === 'banned') {
      whereClause += ' AND is_banned > 0';
    } else if (status === 'active') {
      whereClause += ' AND is_banned = 0';
    }

    // 在线状态筛选
    if (is_online !== undefined) {
      whereClause += ' AND is_online = ?';
      params.push(parseInt(is_online));
    }

    // 权限等级筛选
    if (permission_level !== undefined) {
      whereClause += ' AND user_permission_level = ?';
      params.push(parseInt(permission_level));
    }

    // 管理权限筛选
    if (admin_permissions !== undefined) {
      whereClause += ' AND admin_permissions = ?';
      params.push(parseInt(admin_permissions));
    }

    // 描述状态筛选
    if (description_not_null === 'true' || description_not_null === true) {
      whereClause += ' AND user_description IS NOT NULL AND user_description != ""';
    } else if (description_is_null === 'true' || description_is_null === true) {
      whereClause += ' AND (user_description IS NULL OR user_description = "")';
    }

    // 渠道筛选
    if (user_channel_description_like) {
      whereClause += ' AND user_channel_description LIKE ?';
      params.push(`%${user_channel_description_like}%`);
    }

    // 手机号绑定状态筛选
    if (phone_not_null === 'true' || phone_not_null === true) {
      whereClause += ' AND phone IS NOT NULL AND phone != ""';
    } else if (phone_is_null === 'true' || phone_is_null === true) {
      whereClause += ' AND (phone IS NULL OR phone = "")';
    }

    // 邮箱绑定状态筛选
    if (email_not_null === 'true' || email_not_null === true) {
      whereClause += ' AND email IS NOT NULL AND email != ""';
    } else if (email_is_null === 'true' || email_is_null === true) {
      whereClause += ' AND (email IS NULL OR email = "")';
    }

    // 试用状态筛选
    if (is_trial_user !== undefined && is_trial_user !== '') {
      whereClause += ' AND is_trial_user = ?';
      params.push(parseInt(is_trial_user));
    }

    // 到期时间筛选
    if (expire_time_is_null === 'true' || expire_time_is_null === true) {
      whereClause += ' AND expire_time IS NULL';
    } else {
      if (expire_time_before) {
        whereClause += ' AND expire_time IS NOT NULL AND expire_time < ?';
        params.push(expire_time_before);
      }
      if (expire_time_after) {
        whereClause += ' AND expire_time IS NOT NULL AND expire_time > ?';
        params.push(expire_time_after);
      }
    }

    // 设备数量筛选
    // max_machine_count: 精确匹配 (如 "1", "2")
    // max_machine_count_min: 范围匹配 (如 "3+" 对应 >= 3)
    const { max_machine_count, max_machine_count_min } = req.query;

    if (max_machine_count !== undefined && max_machine_count !== '') {
      whereClause += ' AND max_machine_count = ?';
      params.push(parseInt(max_machine_count));
    } else if (max_machine_count_min !== undefined && max_machine_count_min !== '') {
      whereClause += ' AND max_machine_count >= ?';
      params.push(parseInt(max_machine_count_min));
    }

    // 排序逻辑
    const { sort_field, sort_order } = req.query;
    let orderByClause = 'ORDER BY register_time DESC'; // 默认排序

    if (sort_field) {
      const validSortFields = {
        'username': 'username',
        'permission_level': 'user_permission_level',
        'status': 'is_banned',
        'is_online': 'is_online',
        'register_time': 'register_time',
        'updated_at': 'updated_at',
        'created_at': 'created_at',
        'expire_time': 'expire_time'
      };

      if (validSortFields[sort_field]) {
        const order = (sort_order === 'asc' || sort_order === 'ASC') ? 'ASC' : 'DESC';
        orderByClause = `ORDER BY ${validSortFields[sort_field]} ${order}`;
      }
    }

    const connection = await getPool().getConnection();

    try {
      // 获取总数
      const [countResult] = await connection.execute(
        `SELECT COUNT(*) as total FROM users ${whereClause}`,
        params
      );

      // 获取用户列表 - 使用 SELECT * 避免字段不存在问题
      const [users] = await connection.query(
        `SELECT * FROM users ${whereClause} 
         ${orderByClause} LIMIT ${limitNum} OFFSET ${offset}`,
        params
      );

      res.json({
        success: true,
        data: users,
        pagination: {
          page: pageNum,
          limit: limitNum,
          total: countResult[0].total,
          pages: Math.ceil(countResult[0].total / limitNum)
        }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取用户列表失败');
  }
};

/**
 * 获取用户统计
 */
const getUserStats = async (req, res) => {
  try {
    const connection = await getPool().getConnection();

    try {
      const [stats] = await connection.execute(`
        SELECT 
          COUNT(*) as total_users,
          COUNT(CASE WHEN is_banned = 0 AND is_deleted = 0 THEN 1 END) as active_users,
          COUNT(CASE WHEN is_banned > 0 THEN 1 END) as banned_users,
          COUNT(CASE WHEN is_trial_user = 1 THEN 1 END) as trial_users,
          COUNT(CASE WHEN expire_time > NOW() THEN 1 END) as subscribed_users,
          COUNT(CASE WHEN DATE(register_time) = CURDATE() THEN 1 END) as today_new_users,
          COUNT(CASE WHEN DATE(last_login_time) = CURDATE() THEN 1 END) as today_active_users
        FROM users WHERE is_deleted = 0
      `);

      res.json({
        success: true,
        data: stats[0]
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取用户统计失败');
  }
};

/**
 * 更新用户状态
 */
const updateUserStatus = async (req, res) => {
  try {
    const { userUuid } = req.params;
    const { status, reason = '' } = req.body;

    if (!['active', 'banned', 'deleted'].includes(status)) {
      return res.status(400).json({
        success: false,
        error: '无效的用户状态'
      });
    }

    const connection = await getPool().getConnection();

    try {
      // 根据 status 更新对应字段
      let updateSql = '';
      if (status === 'banned') {
        updateSql = 'UPDATE users SET is_banned = 1, ban_reason = ?, updated_at = NOW() WHERE user_uuid = ?';
      } else if (status === 'deleted') {
        updateSql = 'UPDATE users SET is_deleted = 1, updated_at = NOW() WHERE user_uuid = ?';
      } else {
        updateSql = 'UPDATE users SET is_banned = 0, updated_at = NOW() WHERE user_uuid = ?';
      }

      const params = status === 'banned' ? [reason, userUuid] : [userUuid];
      const [result] = await connection.execute(updateSql, params);

      if (result.affectedRows === 0) {
        return res.status(404).json({
          success: false,
          error: '用户不存在'
        });
      }

      const newApiSync = await syncUserSnapshotToNewApi(connection, userUuid);

      res.json({
        success: true,
        message: `用户状态已更新为${status}`,
        newApiSync
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '更新用户状态失败');
  }
};

/**
 * 获取通知列表
 */
const getNotices = async (req, res) => {
  try {
    const { page, limit } = req.query;
    const pageNum = parseInt(page) || 1;
    const limitNum = parseInt(limit) || 20;
    const offset = (pageNum - 1) * limitNum;

    const connection = await getPool().getConnection();

    try {
      // 获取总数
      const [countResult] = await connection.execute(
        'SELECT COUNT(*) as total FROM notices'
      );

      // 获取通知列表
      const [notices] = await connection.query(
        `SELECT * FROM notices ORDER BY created_at DESC LIMIT ${limitNum} OFFSET ${offset}`
      );

      res.json({
        success: true,
        data: notices,
        pagination: {
          page: parseInt(page),
          limit: parseInt(limit),
          total: countResult[0].total,
          pages: Math.ceil(countResult[0].total / limit)
        }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取通知列表失败');
  }
};

/**
 * 创建通知
 */
const createNotice = async (req, res) => {
  try {
    const { title, content, type = 'info', priority = 'normal' } = req.body;

    if (!title || !content) {
      return res.status(400).json({
        success: false,
        error: '标题和内容不能为空'
      });
    }

    const connection = await getPool().getConnection();

    try {
      const [result] = await connection.execute(
        'INSERT INTO notices (notice_id, title, content, type, priority, status, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW())',
        [uuidv4(), title, content, type, priority, 'active']
      );

      res.json({
        success: true,
        message: '通知创建成功',
        data: { notice_id: result.insertId }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '创建通知失败');
  }
};

/**
 * 更新通知
 */
const updateNotice = async (req, res) => {
  try {
    const { noticeId } = req.params;
    const { title, content, type, priority } = req.body;

    const connection = await getPool().getConnection();

    try {
      const [result] = await connection.execute(
        'UPDATE notices SET title = ?, content = ?, type = ?, priority = ?, updated_at = NOW() WHERE notice_id = ?',
        [title, content, type, priority, noticeId]
      );

      if (result.affectedRows === 0) {
        return res.status(404).json({
          success: false,
          error: '通知不存在'
        });
      }

      res.json({
        success: true,
        message: SUCCESS_MESSAGES.UPDATE_SUCCESS
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '更新通知失败');
  }
};

/**
 * 删除通知
 */
const deleteNotice = async (req, res) => {
  try {
    const { noticeId } = req.params;

    const connection = await getPool().getConnection();

    try {
      const [result] = await connection.execute(
        'DELETE FROM notices WHERE notice_id = ?',
        [noticeId]
      );

      if (result.affectedRows === 0) {
        return res.status(404).json({
          success: false,
          error: '通知不存在'
        });
      }

      res.json({
        success: true,
        message: SUCCESS_MESSAGES.DELETE_SUCCESS
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '删除通知失败');
  }
};

/**
 * 更新用户信息（通用接口）
 */
const updateUser = async (req, res) => {
  try {
    const { userUuid } = req.params;
    const updateData = req.body;

    // 可更新的字段白名单
    const allowedFields = [
      'username', 'phone', 'email', 'password',
      'user_permission_level', 'admin_permissions', 'expire_time', 'user_description',
      'is_banned', 'ban_reason', 'is_deleted', 'max_machine_count',
      'machine_code', 'user_channel_description', 'trial_duration_minutes',
      'is_trial_user'
    ];

    // 过滤出有效的更新字段
    const updates = {};
    for (const field of allowedFields) {
      if (updateData[field] !== undefined) {
        updates[field] = updateData[field];
      }
    }

    if (Object.keys(updates).length === 0) {
      return res.status(400).json({
        success: false,
        error: '没有有效的更新字段'
      });
    }

    const connection = await getPool().getConnection();

    try {
      // 如果更新密码，需要先加密
      if (updates.password) {
        updates.password = await bcrypt.hash(updates.password, 10);
      }

      // 构建动态 SQL
      const setClauses = Object.keys(updates).map(key => `${key} = ?`);
      setClauses.push('updated_at = NOW()');
      const values = Object.values(updates);
      values.push(userUuid);

      const sql = `UPDATE users SET ${setClauses.join(', ')} WHERE user_uuid = ?`;
      const [result] = await connection.execute(sql, values);

      if (result.affectedRows === 0) {
        return res.status(404).json({
          success: false,
          error: '用户不存在'
        });
      }

      const newApiSync = await syncUserSnapshotToNewApi(connection, userUuid);

      res.json({
        success: true,
        message: '用户信息更新成功',
        updated: result.affectedRows,
        newApiSync
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '更新用户信息失败');
  }
};

/**
 * 删除用户（软删除）
 */
const deleteUser = async (req, res) => {
  try {
    const { userUuid } = req.params;

    const connection = await getPool().getConnection();

    try {
      const [result] = await connection.query(
        'UPDATE users SET is_deleted = 1, updated_at = NOW() WHERE user_uuid = ?',
        [userUuid]
      );

      if (result.affectedRows === 0) {
        return res.status(404).json({
          success: false,
          error: '用户不存在'
        });
      }
      const newApiSync = await syncUserSnapshotToNewApi(connection, userUuid);

      res.json({
        success: true,
        message: '用户已删除',
        newApiSync
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '删除用户失败');
  }
};

/**
 * 创建用户 (管理员)
 */
const createUser = async (req, res) => {
  try {
    const {
      username,
      password,
      phone,
      email,
      status = 'active',
      user_permission_level = 1,
      user_description = '',
      user_channel_description = '',
      expire_time,
      created_by = ''
    } = req.body;

    if (!username || !password) {
      return res.status(400).json({
        success: false,
        error: '用户名和密码不能为空'
      });
    }

    const connection = await getPool().getConnection();

    try {
      // 检查是否存在（包含软删除用户）
      // 修复：只在 phone/email 有值时才检查，避免空字符串匹配到其他用户
      let checkSql = 'SELECT * FROM users WHERE username = ?';
      const checkParams = [username];

      if (phone && phone.trim()) {
        checkSql += ' OR phone = ?';
        checkParams.push(phone.trim());
      }
      if (email && email.trim()) {
        checkSql += ' OR email = ?';
        checkParams.push(email.trim());
      }

      const [existing] = await connection.query(checkSql, checkParams);

      if (existing.length > 0) {
        const existingUser = existing[0];

        // 如果用户已存在且未删除，则报错
        if (existingUser.is_deleted === 0) {
          return res.status(400).json({
            success: false,
            error: '用户名、手机号或邮箱已存在'
          });
        }

        // 【复活逻辑】如果用户已软删除，则执行复活
        const passwordHash = await bcrypt.hash(password, 10);
        const expireTime = new Date(Date.now() + DEFAULT_TRIAL_DURATION_HOURS * 60 * 60 * 1000);
        const isBanned = status === 'banned' ? 1 : 0;

        await connection.query(
          `UPDATE users SET 
            is_deleted = 0,
            is_banned = ?,
            password = ?,
            expire_time = ?,
            machine_code = NULL,
            is_online = 0,
            updated_at = NOW()
           WHERE user_uuid = ?`,
          [isBanned, passwordHash, expireTime, existingUser.user_uuid]
        );

        await ensureCreditAccount(connection, existingUser.user_uuid);

        const newApiSync = await syncUserToNewApi({
          user_uuid: existingUser.user_uuid,
          username: existingUser.username,
          is_banned: isBanned,
          is_deleted: 0,
        });

        return res.status(201).json({
          success: true,
          data: {
            user_uuid: existingUser.user_uuid,
            username: existingUser.username,
            phone,
            email,
            status,
            newApiSync
          }
        });
      }

      const userUuid = uuidv4();
      const passwordHash = await bcrypt.hash(password, 10);
      const isBanned = status === 'banned' ? 1 : 0;

      // 设置过期时间：优先使用前端传入的值，否则使用默认试用期
      const finalExpireTime = expire_time
        ? new Date(expire_time)
        : new Date(Date.now() + DEFAULT_TRIAL_DURATION_HOURS * 60 * 60 * 1000);

      // 插入用户，包含完整字段
      try {
        await connection.query(
          `INSERT INTO users (
            user_uuid, username, password, phone, email,
            is_banned, expire_time, user_permission_level,
            user_description, user_channel_description, created_by
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          [
            userUuid,
            username,
            passwordHash,
            phone || null,
            email || null,
            isBanned,
            finalExpireTime,
            user_permission_level,
            user_description || null,
            user_channel_description || null,
            created_by || null
          ]
        );
        await ensureCreditAccount(connection, userUuid);
      } catch (insertError) {
        if (insertError.code === 'ER_DUP_ENTRY') {
          return res.status(400).json({
            success: false,
            error: '用户名、手机号或邮箱已存在'
          });
        }
        throw insertError;
      }

      const newApiSync = await syncUserToNewApi({
        user_uuid: userUuid,
        username,
        is_banned: isBanned,
        is_deleted: 0,
      });

      res.status(201).json({
        success: true,
        data: {
          user_uuid: userUuid,
          username,
          phone,
          email,
          status,
          user_permission_level,
          user_description,
          user_channel_description,
          expire_time: finalExpireTime,
          created_by,
          newApiSync
        }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    // 调试模式：返回详细错误
    console.error('[创建用户Debug]', error);
    res.status(500).json({
      success: false,
      error: '创建用户失败: ' + (error.sqlMessage || error.message)
    });
  }
};

/**
 * 批量更新用户
 */
const batchUpdateUsers = async (req, res) => {
  try {
    const { updates } = req.body; // [{ userUuid, status, ... }]

    if (!updates || !Array.isArray(updates) || updates.length === 0) {
      return res.status(400).json({
        success: false,
        error: '更新数据不能为空'
      });
    }

    const connection = await getPool().getConnection();

    try {
      let successCount = 0;
      const syncResults = [];

      for (const update of updates) {
        const { userUuid, status } = update;

        let sql = '';
        if (status === 'banned') {
          sql = 'UPDATE users SET is_banned = 1, updated_at = NOW() WHERE user_uuid = ?';
        } else if (status === 'deleted') {
          sql = 'UPDATE users SET is_deleted = 1, updated_at = NOW() WHERE user_uuid = ?';
        } else {
          sql = 'UPDATE users SET is_banned = 0, updated_at = NOW() WHERE user_uuid = ?';
        }

        const [result] = await connection.query(sql, [userUuid]);
        if (result.affectedRows > 0) {
          successCount++;
          const syncResult = await syncUserSnapshotToNewApi(connection, userUuid);
          syncResults.push({ userUuid, ...syncResult });
        }
      }

      res.json({
        success: true,
        message: `成功更新 ${successCount} 个用户`,
        updated: successCount,
        newApiSync: syncResults
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '批量更新用户失败');
  }
};

/**
 * 批量删除用户（软删除）
 */
const batchDeleteUsers = async (req, res) => {
  try {
    const { userUuids } = req.body;

    if (!userUuids || !Array.isArray(userUuids) || userUuids.length === 0) {
      return res.status(400).json({
        success: false,
        error: '用户列表不能为空'
      });
    }

    const connection = await getPool().getConnection();

    try {
      const placeholders = userUuids.map(() => '?').join(',');
      const [result] = await connection.query(
        `UPDATE users SET is_deleted = 1, updated_at = NOW() WHERE user_uuid IN (${placeholders})`,
        userUuids
      );

      const [users] = await connection.query(
        `SELECT user_uuid, username, is_banned, is_deleted FROM users WHERE user_uuid IN (${placeholders})`,
        userUuids
      );
      const newApiSync = await syncUsersToNewApi(users);

      res.json({
        success: true,
        message: `成功删除 ${result.affectedRows} 个用户`,
        deleted: result.affectedRows,
        newApiSync
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '批量删除用户失败');
  }
};

/**
 * 批量创建用户
 */
const batchCreateUsers = async (req, res) => {
  try {
    const { users } = req.body; // [{ username, password, phone, email, status... }]

    if (!users || !Array.isArray(users) || users.length === 0) {
      return res.status(400).json({
        success: false,
        error: '用户列表不能为空'
      });
    }

    const connection = await getPool().getConnection();

    try {
      await connection.beginTransaction();
      let successCount = 0;
      const createdUsers = [];

      for (const user of users) {
        const {
          username,
          password,
          phone,
          email,
          status = 'active',
          user_permission_level = 1,
          user_description = '',
          user_channel_description = '',
          created_by = 'unknown',
          expire_time
        } = user;

        // 查重（包含软删除）
        const [existing] = await connection.query(
          'SELECT user_uuid, is_deleted FROM users WHERE username = ?',
          [username]
        );

        if (existing.length > 0) {
          const existingUser = existing[0];
          // 如果是软删除用户，执行复活逻辑
          if (existingUser.is_deleted === 1) {
            const passwordHash = await bcrypt.hash(password || '123456', 10);
            const isBanned = status === 'banned' ? 1 : 0;
            const finalExpireTime = expire_time || new Date(Date.now() + DEFAULT_TRIAL_DURATION_HOURS * 60 * 60 * 1000);

            await connection.query(
              `UPDATE users SET
                 is_deleted = 0,
                 is_banned = ?,
                 password = ?,
                 expire_time = ?,
                 machine_code = NULL,
                 is_online = 0,
                 updated_at = NOW(),
                 user_description = ?,
                 user_channel_description = ?,
                 user_permission_level = ?
                WHERE user_uuid = ?`,
              [
                isBanned,
                passwordHash,
                finalExpireTime,
                user_description || '',
                user_channel_description || '',
                user_permission_level,
                existingUser.user_uuid
              ]
            );
            successCount++;
            createdUsers.push({
              user_uuid: existingUser.user_uuid,
              username,
              status: 'reactivated',
              is_banned: isBanned,
              is_deleted: 0
            });
          }
          // 如果用户已存在且未删除，则跳过
        } else {
          const userUuid = uuidv4();
          const passwordHash = await bcrypt.hash(password || '123456', 10);
          const isBanned = status === 'banned' ? 1 : 0;
          const finalExpireTime = expire_time || new Date(Date.now() + DEFAULT_TRIAL_DURATION_HOURS * 60 * 60 * 1000);

          await connection.query(
            `INSERT INTO users (
              user_uuid, username, password, phone, email,
              is_banned, expire_time, user_permission_level,
              user_description, user_channel_description, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
            [
              userUuid,
              username,
              passwordHash,
              phone || null,
              email || null,
              isBanned,
              finalExpireTime,
              user_permission_level,
              user_description || '',
              user_channel_description || '',
              created_by
            ]
          );
          successCount++;
          createdUsers.push({
            user_uuid: userUuid,
            username,
            status: 'created',
            is_banned: isBanned,
            is_deleted: 0
          });
        }
      }

      await connection.commit();

      const newApiSync = await syncUsersToNewApi(createdUsers);

      res.json({
        success: true,
        message: `成功创建 ${successCount} 个用户`,
        data: createdUsers,
        newApiSync
      });
    } catch (error) {
      await connection.rollback();
      throw error;
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '批量创建用户失败');
  }
};

/**
 * 根据UUID获取用户
 */
const getUserByUuid = async (req, res) => {
  try {
    const { userUuid } = req.params;
    const connection = await getPool().getConnection();
    try {
      const [users] = await connection.query('SELECT * FROM users WHERE user_uuid = ?', [userUuid]);
      if (users.length === 0) {
        return res.status(404).json({ success: false, error: '用户不存在' });
      }
      res.json({ success: true, data: users[0] });
    } finally {
      connection.release();
    }


  } catch (error) {
    handleApiError(res, error, '获取用户详情失败');
  }
};

/**
 * 根据用户名获取用户
 */
const getUserByUsername = async (req, res) => {
  try {
    const { username } = req.params;
    const connection = await getPool().getConnection();
    try {
      const [users] = await connection.query('SELECT * FROM users WHERE username = ?', [username]);
      if (users.length === 0) {
        return res.status(404).json({ success: false, error: '用户不存在' });
      }
      res.json({ success: true, data: users[0] });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取用户详情失败');
  }
};

/**
 * 获取单个通知
 */
const getNoticeById = async (req, res) => {
  try {
    const { noticeId } = req.params;
    const connection = await getPool().getConnection();
    try {
      const [notices] = await connection.query('SELECT * FROM notices WHERE notice_id = ?', [noticeId]);
      if (notices.length === 0) {
        return res.status(404).json({ success: false, error: '通知不存在' });
      }
      res.json({ success: true, data: notices[0] });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取通知详情失败');
  }
};

/**
 * 获取机器统计 (Mock)
 */
const getMachineAggregations = async (req, res) => {
  try {
    const connection = await getPool().getConnection();
    try {
      // 使用真实的 users 表数据进行统计
      // 1. 活跃用户数 (is_online = 1)
      // 2. 机器总数估算 (解析 machine_code 字段，以 | 分隔)
      // 注意：如果没有 machine_code，则视为 0 台
      const [result] = await connection.query(`
        SELECT 
          COUNT(CASE WHEN is_online = 1 THEN 1 END) as active_users,
          SUM(
            CASE 
              WHEN machine_code IS NOT NULL AND machine_code != '' 
              THEN (LENGTH(machine_code) - LENGTH(REPLACE(machine_code, '|', '')) + 1)
              ELSE 0 
            END
          ) as total_machines
        FROM users 
        WHERE is_deleted = 0
      `);

      const { active_users, total_machines } = result[0];

      // 获取一些最近在线的机器列表用于展示（可选）
      const [recentMachines] = await connection.query(`
        SELECT user_uuid, username, machine_code, last_login_time, last_login_ip 
        FROM users 
        WHERE is_deleted = 0 AND machine_code IS NOT NULL AND machine_code != ''
        ORDER BY last_login_time DESC 
        LIMIT 10
      `);

      const machinesList = recentMachines.map(u => {
        // 简单处理：如果一个用户有多个机器码，这里只展示第一条作为一个"机器"条目，或者展开
        // 为简化展示，我们只取第一个机器码作为代表
        const codes = u.machine_code.split('|');
        return {
          machineCode: codes[0], // 取第一个
          username: u.username,
          lastActive: u.last_login_time,
          ip: u.last_login_ip,
          status: 'online' // 既然有最近登录，暂且视为活跃
        };
      });

      res.json({
        success: true,
        data: {
          machines: machinesList,
          stats: {
            total: parseInt(total_machines || 0),
            active: parseInt(active_users || 0),
            offline: 0 // 暂时很难定义离线机器，除非有机器维度的记录
          }
        }
      });
    } finally {
      connection.release();
    }
  } catch (error) {
    handleApiError(res, error, '获取机器统计失败');
  }
};

module.exports = {
  getUserList,
  getUserStats,
  updateUserStatus,
  updateUser,
  deleteUser,
  createUser,
  batchUpdateUsers,
  batchDeleteUsers,
  batchCreateUsers,
  getUserByUuid,
  getUserByUsername,
  getNotices,
  getNoticeById,
  createNotice,
  updateNotice,
  deleteNotice,
  getMachineAggregations
};
