/**
 * 初始化默认管理员账户
 */

const bcrypt = require("bcryptjs");
const { getPool } = require("../config/database");
const { ensureCreditAccount } = require("./creditAccount");

const DEFAULT_ADMIN = {
  user_uuid: "00000000-0000-0000-0000-000000000001",
  username: "admin",
  password: "admin123",
  email: "admin@example.com",
  phone: "",
  user_permission_level: 10,
  admin_permissions: 3,
  max_machine_count: 5,
};

/**
 * 初始化默认管理员账户
 */
async function initDefaultAdmin() {
  const connection = await getPool().getConnection();

  try {
    // 检查管理员是否已存在
    const [users] = await connection.execute(
      "SELECT user_uuid FROM users WHERE username = ?",
      [DEFAULT_ADMIN.username],
    );

    if (users.length > 0) {
      await connection.execute(
        `UPDATE users
         SET user_permission_level = GREATEST(COALESCE(user_permission_level, 1), ?),
             admin_permissions = GREATEST(COALESCE(admin_permissions, 1), ?),
             is_banned = 0,
             is_deleted = 0,
             updated_at = NOW()
         WHERE username = ?`,
        [
          DEFAULT_ADMIN.user_permission_level,
          DEFAULT_ADMIN.admin_permissions,
          DEFAULT_ADMIN.username,
        ],
      );
      await ensureCreditAccount(connection, users[0].user_uuid);
      console.log(
        `[init] 管理员账户 '${DEFAULT_ADMIN.username}' 已存在，已确认管理员权限`,
      );
      return;
    }

    // 创建管理员账户
    const hashedPassword = await bcrypt.hash(DEFAULT_ADMIN.password, 10);

    await connection.execute(
      `INSERT INTO users (
        user_uuid, username, password, email, phone,
        user_permission_level, admin_permissions,
        is_banned, is_deleted, machine_code, max_machine_count,
        trial_duration_hours, trial_expiry_time, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, '', ?, 0, NULL, NOW(), NOW())`,
      [
        DEFAULT_ADMIN.user_uuid,
        DEFAULT_ADMIN.username,
        hashedPassword,
        DEFAULT_ADMIN.email,
        DEFAULT_ADMIN.phone,
        DEFAULT_ADMIN.user_permission_level,
        DEFAULT_ADMIN.admin_permissions,
        DEFAULT_ADMIN.max_machine_count,
      ],
    );

    await ensureCreditAccount(connection, DEFAULT_ADMIN.user_uuid);

    console.log(
      `[init] 成功创建默认管理员账户 '${DEFAULT_ADMIN.username}' / '${DEFAULT_ADMIN.password}'`,
    );
  } catch (error) {
    console.error("[init] 初始化管理员账户失败:", error.message);
    throw error;
  } finally {
    connection.release();
  }
}

module.exports = { initDefaultAdmin };