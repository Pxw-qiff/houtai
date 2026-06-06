# 后端 API 文档

> **基础路径**: `/api`  
> **服务版本**: ai-anime-api  
> **更新日期**: 2026-01-18

---

## 目录

1. [用户模块 `/user`](#1-用户模块-user)
2. [管理员模块 `/admin`](#2-管理员模块-admin)
3. [通知模块 `/notices`](#3-通知模块-notices)
4. [更新日志模块 `/versions`](#4-更新日志模块-versions)
5. [系统模块 `/system`](#5-系统模块-system)
6. [文件模块 `/files`](#6-文件模块-files)
7. [工具模块 `/api`](#7-工具模块-api)

---

## 统一响应格式

### 成功响应
```json
{
  "success": true,
  "message": "操作成功提示",
  "data": { /* 业务数据 */ }
}
```

### 失败响应
```json
{
  "success": false,
  "error": "错误描述信息"
}
```

### 分页响应
```json
{
  "success": true,
  "data": [ /* 列表数据 */ ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 100,
    "pages": 5
  }
}
```

---

## 1. 用户模块 `/user`

### 1.1 用户登录
- **路径**: `POST /user/login`
- **权限**: 公开
- **请求参数**:
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | loginField | string | 是 | 登录凭据（用户名/手机号/邮箱） |
  | password | string | 是 | 密码 |
  | loginMode | string | 否 | 登录方式: `username`(默认), `phone`, `email` |

- **响应示例**:
```json
{
  "success": true,
  "message": "登录成功",
  "data": {
    "token": "eyJhbGciOiJI...",
    "user": {
      "user_uuid": "550e8400-e29b-41d4-a716-446655440000",
      "username": "testuser",
      "phone": "13800138000",
      "email": "test@example.com",
      "user_permission_level": 1,
      "admin_permissions": 0,
      "expire_time": "2026-02-01T00:00:00.000Z",
      "is_trial_user": 0,
      "max_machine_count": 1,
      "login_count": 10
    }
  }
}
```

### 1.2 用户注册
- **路径**: `POST /user/register`
- **权限**: 公开
- **请求参数**:
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | username | string | 是 | 用户名 |
  | password | string | 是 | 密码 |
  | phone | string | 否 | 手机号 |
  | email | string | 否 | 邮箱 |

- **响应示例**:
```json
{
  "success": true,
  "message": "注册成功",
  "data": {
    "userUuid": "550e8400-e29b-41d4-a716-446655440000",
    "username": "newuser",
    "trialEndTime": "2026-01-19T00:00:00.000Z"
  }
}
```

### 1.3 用户登出
- **路径**: `POST /user/logout`
- **权限**: 需要登录 (`verifyToken`)
- **请求参数**: 无
- **响应示例**:
```json
{
  "success": true,
  "message": "登出成功"
}
```

### 1.4 验证会话
- **路径**: `GET /user/validate-session`
- **权限**: 需要登录 (`verifyToken`)
- **请求参数**: 无
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "user": {
      "userUuid": "550e8400-e29b-41d4-a716-446655440000",
      "username": "testuser",
      "status": "active",
      "trialEndTime": "2026-01-19T00:00:00.000Z",
      "subscriptionEndTime": null
    }
  }
}
```

### 1.5 心跳检测
- **路径**: `GET /user/heartbeat` 或 `GET /user/health`
- **权限**: 公开
- **请求参数**: 无
- **响应示例**:
```json
{
  "success": true,
  "timestamp": "2026-01-18T08:00:00.000Z",
  "server": "ai-anime-api"
}
```

---

## 2. 管理员模块 `/admin`

> 所有接口需要管理员权限 (`verifyAdmin`)

### 2.1 获取用户列表
- **路径**: `GET /admin/users`
- **请求参数** (Query):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | page | number | 否 | 页码，默认1 |
  | limit | number | 否 | 每页条数，默认20 |
  | search | string | 否 | 搜索关键词(用户名/手机/邮箱) |
  | status | string | 否 | 状态筛选: `active`, `banned` |

- **响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "user_uuid": "550e8400-...",
      "username": "user1",
      "phone": "13800138000",
      "email": "user1@test.com",
      "is_banned": 0,
      "is_deleted": 0,
      "register_time": "2026-01-01T00:00:00.000Z",
      "last_login_time": "2026-01-18T00:00:00.000Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 20,
    "total": 100,
    "pages": 5
  }
}
```

### 2.2 创建用户
- **路径**: `POST /admin/users`
- **请求参数** (Body):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | username | string | 是 | 用户名 |
  | password | string | 是 | 密码 |
  | phone | string | 否 | 手机号 |
  | email | string | 否 | 邮箱 |
  | status | string | 否 | 状态: `active`(默认), `banned` |

- **响应示例**:
```json
{
  "success": true,
  "data": {
    "user_uuid": "550e8400-...",
    "username": "newuser",
    "phone": null,
    "email": null,
    "status": "active"
  }
}
```

### 2.3 批量创建用户
- **路径**: `POST /admin/users/batch`
- **请求参数** (Body):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | users | array | 是 | 用户数组，每项包含 username, password, phone, email, status |

- **响应示例**:
```json
{
  "success": true,
  "message": "成功创建 5 个用户",
  "data": [
    { "user_uuid": "...", "username": "user1" },
    { "user_uuid": "...", "username": "user2" }
  ]
}
```

### 2.4 根据UUID获取用户
- **路径**: `GET /admin/users/:userUuid`
- **路径参数**: `userUuid` - 用户UUID
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "user_uuid": "550e8400-...",
    "username": "testuser",
    "phone": "13800138000",
    "email": "test@example.com"
    // ... 其他用户字段
  }
}
```

### 2.5 根据用户名获取用户
- **路径**: `GET /admin/users/search/:username`
- **路径参数**: `username` - 用户名
- **响应示例**: 同 2.4

### 2.6 获取用户统计
- **路径**: `GET /admin/users/stats`
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "total_users": 1000,
    "active_users": 950,
    "banned_users": 20,
    "trial_users": 100,
    "subscribed_users": 500,
    "today_new_users": 10,
    "today_active_users": 200
  }
}
```

### 2.7 更新用户状态
- **路径**: `PUT /admin/users/:userUuid/status`
- **路径参数**: `userUuid` - 用户UUID
- **请求参数** (Body):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | status | string | 是 | 状态: `active`, `banned`, `deleted` |
  | reason | string | 否 | 封禁原因 |

- **响应示例**:
```json
{
  "success": true,
  "message": "用户状态已更新为banned"
}
```

### 2.8 删除用户
- **路径**: `DELETE /admin/users/:userUuid`
- **路径参数**: `userUuid` - 用户UUID
- **响应示例**:
```json
{
  "success": true,
  "message": "用户已删除"
}
```

### 2.9 批量更新用户
- **路径**: `PUT /admin/users/batch`
- **请求参数** (Body):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | updates | array | 是 | 更新数组，每项包含 userUuid, status |

- **响应示例**:
```json
{
  "success": true,
  "message": "成功更新 3 个用户",
  "updated": 3
}
```

### 2.10 批量删除用户
- **路径**: `DELETE /admin/users/batch`
- **请求参数** (Body):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | userUuids | array | 是 | 用户UUID数组 |

- **响应示例**:
```json
{
  "success": true,
  "message": "成功删除 3 个用户",
  "deleted": 3
}
```

### 2.11 获取机器聚合数据
- **路径**: `GET /admin/machines/aggregations`
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "total_machines": 500,
    "online_machines": 150,
    "offline_machines": 350
  }
}
```

### 2.12 获取通知列表 (Admin)
- **路径**: `GET /admin/notices`
- **请求参数** (Query):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | page | number | 否 | 页码，默认1 |
  | limit | number | 否 | 每页条数，默认20 |

- **响应示例**: 同通知模块 3.1

### 2.13-2.15 通知管理 (Admin)
同 [通知模块](#3-通知模块-notices)，需要管理员权限

---

## 3. 通知模块 `/notices`

### 3.1 获取通知列表
- **路径**: `GET /notices`
- **权限**: 公开
- **请求参数** (Query):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | page | number | 否 | 页码，默认1 |
  | limit | number | 否 | 每页条数，默认20 |

- **响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "notice_id": "660e8400-...",
      "title": "系统维护通知",
      "content": "系统将于今晚进行维护...",
      "type": "info",
      "priority": "normal",
      "status": "active",
      "created_at": "2026-01-18T00:00:00.000Z"
    }
  ],
  "pagination": { "page": 1, "limit": 20, "total": 10, "pages": 1 }
}
```

### 3.2 创建通知
- **路径**: `POST /notices`
- **权限**: 管理员
- **请求参数** (Body):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | title | string | 是 | 标题 |
  | content | string | 是 | 内容 |
  | type | string | 否 | 类型: `info`(默认), `warning`, `error` |
  | priority | string | 否 | 优先级: `normal`(默认), `high`, `urgent` |

- **响应示例**:
```json
{
  "success": true,
  "message": "通知创建成功",
  "data": { "notice_id": "660e8400-..." }
}
```

### 3.3 更新通知
- **路径**: `PUT /notices/:noticeId`
- **权限**: 管理员
- **路径参数**: `noticeId` - 通知ID
- **请求参数** (Body): 同 3.2
- **响应示例**:
```json
{
  "success": true,
  "message": "通知更新成功"
}
```

### 3.4 删除通知
- **路径**: `DELETE /notices/:noticeId`
- **权限**: 管理员
- **路径参数**: `noticeId` - 通知ID
- **响应示例**:
```json
{
  "success": true,
  "message": "通知删除成功"
}
```

---

## 4. 更新日志模块 `/versions`

### 4.1 获取更新日志列表
- **路径**: `GET /versions`
- **权限**: 公开
- **请求参数** (Query):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | page | number | 否 | 页码，默认1 |
  | limit | number | 否 | 每页条数，默认20 |
  | platform | string | 否 | 平台: `windows`, `mac`, `beta` |
  | version | string | 否 | 版本号筛选(模糊匹配) |
  | date_start | string | 否 | 开始日期 |
  | date_end | string | 否 | 结束日期 |

- **响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "version_id": "660e8400-...",
      "version": "1.2.0",
      "date": "2026.01.18",
      "platform": "windows",
      "items": [
        { "item_id": "...", "type": "feat", "text": "新增功能A" },
        { "item_id": "...", "type": "fix", "text": "修复问题B" }
      ]
    }
  ],
  "pagination": { "page": 1, "limit": 20, "total": 5, "pages": 1 }
}
```

### 4.2 获取更新日志详情
- **路径**: `GET /versions/:versionId`
- **权限**: 公开
- **路径参数**: `versionId` - 版本ID
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "version_id": "660e8400-...",
    "version": "1.2.0",
    "date": "2026.01.18",
    "platform": "windows",
    "items": [
      { "item_id": "...", "type": "feat", "text": "新增功能A" }
    ]
  }
}
```

### 4.3 创建更新日志
- **路径**: `POST /versions`
- **权限**: 管理员
- **请求参数** (Body):
  | 字段 | 类型 | 必填 | 说明 |
  |------|------|------|------|
  | version | string | 是 | 版本号 |
  | date | string | 是 | 日期 (格式: YYYY.MM.DD) |
  | platform | string | 否 | 平台: `windows`(默认), `mac`, `beta` |
  | items | array | 否 | 更新项数组，每项包含 type, text |

- **items 字段说明**:
  | 字段 | 类型 | 说明 |
  |------|------|------|
  | type | string | 类型: `feat`, `fix`, `perf`, `docs`, `style`, `refactor` |
  | text | string | 更新描述 |

- **响应示例**:
```json
{
  "success": true,
  "message": "更新日志创建成功",
  "data": { "version_id": "660e8400-..." }
}
```

### 4.4 更新更新日志
- **路径**: `PUT /versions/:versionId`
- **权限**: 管理员
- **路径参数**: `versionId` - 版本ID
- **请求参数** (Body): 同 4.3
- **响应示例**:
```json
{
  "success": true,
  "message": "更新成功"
}
```

### 4.5 删除更新日志
- **路径**: `DELETE /versions/:versionId`
- **权限**: 管理员
- **路径参数**: `versionId` - 版本ID
- **响应示例**:
```json
{
  "success": true,
  "message": "删除成功"
}
```

---

## 5. 系统模块 `/system`

### 5.1 系统健康检查
- **路径**: `GET /system/health`
- **权限**: 公开
- **响应示例**:
```json
{
  "status": "ok",
  "timestamp": "2026-01-18T08:00:00.000Z",
  "service": "ai-anime-api",
  "uptime_seconds": 86400,
  "database": "ok",
  "database_connections": 10,
  "total_users": 1000
}
```

### 5.2 系统状态监控
- **路径**: `GET /system/status`
- **权限**: 管理员
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "timestamp": "2026-01-18T08:00:00.000Z",
    "uptime_seconds": 86400,
    "database": {
      "Threads_connected": "10",
      "Threads_running": "2",
      "Queries": "1000000",
      "Uptime": "864000"
    },
    "user_activity": {
      "active_24h": 200,
      "active_7d": 500,
      "active_30d": 800
    },
    "memory_usage": { /* Node.js memoryUsage */ },
    "cpu_usage": { /* Node.js cpuUsage */ }
  }
}
```

### 5.3 数据库健康状态
- **路径**: `GET /system/database/health`
- **权限**: 管理员
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "status": "healthy",
    "timestamp": "2026-01-18T08:00:00.000Z",
    "variables": {
      "max_connections": "151",
      "innodb_buffer_pool_size": "134217728"
    },
    "status": {
      "Threads_connected": "10",
      "Threads_running": "2"
    }
  }
}
```

### 5.4 系统统计信息
- **路径**: `GET /system/stats`
- **权限**: 管理员
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "timestamp": "2026-01-18T08:00:00.000Z",
    "uptime_seconds": 86400,
    "user_statistics": {
      "total_users": 1000,
      "active_users": 950,
      "new_users_7d": 50,
      "new_users_30d": 150
    },
    "login_statistics": {
      "logins_today": 100,
      "logins_7d": 500,
      "logins_30d": 1500
    },
    "system_info": {
      "node_version": "v18.17.0",
      "platform": "linux"
    }
  }
}
```

---

## 6. 文件模块 `/files`

### 6.1 更新包下载
- **路径**: `GET /files/updates/:platform/:version`
- **权限**: 公开
- **路径参数**:
  | 参数 | 说明 |
  |------|------|
  | platform | 平台: `admin`, `main`, `audio` |
  | version | 版本号 |

- **响应**: 302 重定向到下载地址

### 6.2 文件上传
- **路径**: `POST /files/upload`
- **权限**: 管理员
- **状态**: 待实现
- **响应示例**:
```json
{
  "success": true,
  "message": "文件上传功能待实现"
}
```

### 6.3 文件下载
- **路径**: `GET /files/download/:fileId`
- **权限**: 可选认证
- **路径参数**: `fileId` - 文件ID
- **状态**: 待实现

### 6.4 获取文件信息
- **路径**: `GET /files/info/:fileId`
- **权限**: 可选认证
- **路径参数**: `fileId` - 文件ID
- **状态**: 待实现

---

## 7. 工具模块 `/api`

### 7.1 生成UUID
- **路径**: `GET /api/uuid`
- **权限**: 公开
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "uuid": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

### 7.2 生成随机密码
- **路径**: `GET /api/password/:length?`
- **权限**: 公开
- **路径参数**: `length` - 密码长度 (6-32，默认12)
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "password": "aB3$fG7#kL9@",
    "length": 12
  }
}
```

### 7.3 获取服务器时间
- **路径**: `GET /api/time`
- **权限**: 公开
- **响应示例**:
```json
{
  "success": true,
  "data": {
    "timestamp": "2026-01-18T08:00:00.000Z",
    "unix": 1768723200
  }
}
```

---

## 错误码说明

| HTTP 状态码 | 说明 |
|-------------|------|
| 200 | 请求成功 |
| 201 | 创建成功 |
| 400 | 请求参数错误 |
| 401 | 未授权/登录失败 |
| 403 | 权限不足/账户被禁用 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
