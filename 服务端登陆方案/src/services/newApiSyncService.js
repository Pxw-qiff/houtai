const http = require("http");
const https = require("https");
const { URL } = require("url");
const { NEW_API_CONFIG } = require("../config/constants");

const SYNC_PATH = "/api/internal/chuamgwei/users/sync";

/**
 * 调用 new-api 内部用户同步接口
 */
const syncUserToNewApi = async (user) => {
  if (!NEW_API_CONFIG.BASE_URL || !NEW_API_CONFIG.INTERNAL_SECRET) {
    return {
      success: false,
      skipped: true,
      error: "new-api 同步配置未启用",
    };
  }

  const payload = {
    userUuid: user.user_uuid || user.userUuid,
    username: user.username || "",
    isBanned: Number(user.is_banned || user.isBanned || 0),
    isDeleted: Number(user.is_deleted || user.isDeleted || 0),
  };

  if (!payload.userUuid) {
    return {
      success: false,
      skipped: true,
      error: "用户UUID为空，无法同步 new-api",
    };
  }

  try {
    const response = await postJson(SYNC_PATH, payload);
    if (!response.success) {
      return {
        success: false,
        error: response.message || response.error || "new-api 同步失败",
        data: response.data,
      };
    }
    return {
      success: true,
      data: response.data,
    };
  } catch (error) {
    return {
      success: false,
      error: error.message,
    };
  }
};

/**
 * 批量调用 new-api 内部用户同步接口
 */
const syncUsersToNewApi = async (users) => {
  const results = [];
  for (const user of users) {
    const result = await syncUserToNewApi(user);
    results.push({
      userUuid: user.user_uuid || user.userUuid,
      username: user.username,
      ...result,
    });
  }
  return results;
};

/**
 * 查询当前 users 表快照并同步到 new-api
 */
const syncUserSnapshotToNewApi = async (connection, userUuid) => {
  const [users] = await connection.query(
    "SELECT user_uuid, username, is_banned, is_deleted FROM users WHERE user_uuid = ?",
    [userUuid],
  );

  if (users.length === 0) {
    return {
      success: false,
      skipped: true,
      error: "用户不存在，无法同步 new-api",
    };
  }

  return syncUserToNewApi(users[0]);
};

/**
 * 发送 JSON POST 请求
 */
const postJson = (path, payload) => {
  return new Promise((resolve, reject) => {
    const baseUrl = NEW_API_CONFIG.BASE_URL.replace(/\/$/, "");
    const target = new URL(`${baseUrl}${path}`);
    const body = JSON.stringify(payload);
    const transport = target.protocol === "https:" ? https : http;

    const request = transport.request(
      {
        method: "POST",
        hostname: target.hostname,
        port: target.port,
        path: `${target.pathname}${target.search}`,
        headers: {
          "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(body),
          "X-Chuamgwei-Internal-Secret": NEW_API_CONFIG.INTERNAL_SECRET,
        },
        timeout: NEW_API_CONFIG.SYNC_TIMEOUT_MS,
      },
      (response) => {
        let responseBody = "";
        response.setEncoding("utf8");
        response.on("data", (chunk) => {
          responseBody += chunk;
        });
        response.on("end", () => {
          let parsed;
          try {
            parsed = responseBody ? JSON.parse(responseBody) : {};
          } catch (error) {
            reject(new Error(`new-api 返回非 JSON 响应: ${response.statusCode}`));
            return;
          }

          if (response.statusCode < 200 || response.statusCode >= 300) {
            reject(new Error(parsed.message || parsed.error || `new-api HTTP ${response.statusCode}`));
            return;
          }

          resolve(parsed);
        });
      },
    );

    request.on("timeout", () => {
      request.destroy(new Error("new-api 同步请求超时"));
    });
    request.on("error", reject);
    request.write(body);
    request.end();
  });
};

module.exports = {
  syncUserToNewApi,
  syncUsersToNewApi,
  syncUserSnapshotToNewApi,
};