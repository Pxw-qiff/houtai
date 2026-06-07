/**
 * 积分账户初始化辅助模块
 */

/**
 * 确保指定用户存在积分账户
 */
async function ensureCreditAccount(connection, userUuid) {
  await connection.execute(
    `INSERT INTO credit_account (user_uuid)
     VALUES (?)
     ON DUPLICATE KEY UPDATE user_uuid = user_uuid`,
    [userUuid],
  );
}

/**
 * 给历史有效用户补齐缺失的积分账户
 */
async function backfillCreditAccounts(connection) {
  const [result] = await connection.execute(
    `INSERT INTO credit_account (user_uuid)
     SELECT u.user_uuid
     FROM users u
     LEFT JOIN credit_account ca ON ca.user_uuid = u.user_uuid
     WHERE ca.user_uuid IS NULL
       AND u.is_deleted = 0`,
  );

  return result.affectedRows || 0;
}

module.exports = {
  ensureCreditAccount,
  backfillCreditAccounts,
};