/**
 * 初始化默认管理员账户
 */

const bcrypt = require("bcryptjs");
const { v4: uuidv4 } = require("uuid");
const { getPool } = require("../config/database");

const DEFAULT_ADMIN = {
  user_uuid: "00000000-0000-0000-0000-000000000001",
  username: "admin",
  password: "admin123",
  email: "admin@example.com",
  phone: "",
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
      console.log(
        `[init] 管理员账户 '${DEFAULT_ADMIN.username}' 已存在，跳过初始化`,
      );
      return;
    }

    // 创建管理员账户
    const hashedPassword = await bcrypt.hash(DEFAULT_ADMIN.password, 10);

    await connection.execute(
      `INSERT INTO users (
        user_uuid, username, password, email, phone,
        is_banned, is_deleted, machine_code, max_machine_count,
        trial_duration_hours, trial_expiry_time, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, 0, 0, '', ?, 0, NULL, NOW(), NOW())`,
      [
        DEFAULT_ADMIN.user_uuid,
        DEFAULT_ADMIN.username,
        hashedPassword,
        DEFAULT_ADMIN.email,
        DEFAULT_ADMIN.phone,
        DEFAULT_ADMIN.max_machine_count,
      ],
    );

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