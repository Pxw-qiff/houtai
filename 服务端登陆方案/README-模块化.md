# AI Anime API 模块化架构

## 项目结构

```
src/
├── config/                 # 配置模块
│   ├── constants.js        # 常量定义
│   └── database.js         # 数据库配置
├── middleware/             # 中间件模块
│   ├── auth.js            # 认证中间件
│   └── error.js           # 错误处理中间件
├── controllers/            # 控制器模块
│   ├── userController.js   # 用户控制器
│   ├── adminController.js  # 管理控制器
│   ├── systemController.js # 系统监控控制器
│   └── fileController.js   # 文件服务控制器
├── routes/                 # 路由模块
│   ├── userRoutes.js       # 用户路由
│   ├── adminRoutes.js      # 管理路由
│   ├── systemRoutes.js     # 系统路由
│   ├── fileRoutes.js       # 文件路由
│   └── apiRoutes.js        # 通用API路由
├── utils/                  # 工具模块
│   └── helpers.js          # 通用工具函数
├── app.js                  # 主应用文件
└── package.json            # 依赖配置
```

## API端点

### 用户登录模块 (`/ai-anime/user/login`)
- `POST /login` - 用户登录
- `POST /register` - 用户注册
- `POST /logout` - 用户登出
- `GET /validate-session` - 验证会话
- `GET /heartbeat` - 心跳检测
- `GET /health` - 健康检查

### 管理功能模块 (`/ai-anime/admin`)
- `GET /users` - 获取用户列表
- `GET /users/stats` - 获取用户统计
- `PUT /users/:userUuid/status` - 更新用户状态
- `GET /notices` - 获取通知列表
- `POST /notices` - 创建通知
- `PUT /notices/:noticeId` - 更新通知
- `DELETE /notices/:noticeId` - 删除通知

### 系统监控模块 (`/ai-anime/system`)
- `GET /health` - 系统健康检查
- `GET /status` - 系统状态监控
- `GET /database/health` - 数据库健康状态
- `GET /stats` - 系统统计信息

### 文件服务模块 (`/ai-anime/files`)
- `GET /updates/:platform/:version` - 更新包下载
- `POST /upload` - 文件上传
- `GET /download/:fileId` - 文件下载
- `GET /info/:fileId` - 获取文件信息

### 通用工具模块 (`/ai-anime/api`)
- `GET /uuid` - 生成UUID
- `GET /password/:length?` - 生成随机密码
- `GET /time` - 获取服务器时间

## 部署说明

### 准备工作

**需要上传的文件：**
- `src/` 目录（完整上传）
- `配置信息（部署时改名为.env）.txt`（配置文件）

**服务器要求：**
- Node.js >= 16.0.0
- npm 或 yarn
- MySQL 数据库访问权限

---

### 完整部署流程

#### 步骤1：上传文件到服务器

```bash
# 方式1：使用 scp 上传
scp -r src/ root@<SERVER_HOST>:/data/ai-anime-api/
scp 配置信息（部署时改名为.env）.txt root@<SERVER_HOST>:/data/ai-anime-api/src/.env

# 方式2：使用 rsync 上传（推荐，支持增量同步）
rsync -avz --progress src/ root@<SERVER_HOST>:/data/ai-anime-api/src/
```

#### 步骤2：SSH登录服务器

```bash
ssh root@<SERVER_HOST>
cd /data/ai-anime-api/src
```

#### 步骤3：配置环境变量

```bash
# 如果还没有上传 .env 文件，手动创建
cat > .env << 'EOF'
# 服务器配置
PORT=3100
HOST=0.0.0.0

# 数据库配置
DB_HOST=<MYSQL_HOST>
DB_PORT=3306
DB_USER=chuamgwei
DB_PASSWORD=你的数据库密码
DB_NAME=chuamgwei_gateway

# JWT配置
JWT_SECRET=你的JWT密钥
JWT_EXPIRES_IN=7d

# new-api 用户同步配置
# NEW_API_BASE_URL 指向 new-api 后端服务地址，例如 http://127.0.0.1:3000
NEW_API_BASE_URL=http://127.0.0.1:3000
# NEW_API_INTERNAL_SECRET 必须与 new-api 的 CHUAMGWEI_USER_SYNC_SECRET 完全一致
NEW_API_INTERNAL_SECRET=你的new-api用户同步密钥
# 可按网络情况调整，单位毫秒
NEW_API_SYNC_TIMEOUT_MS=5000
EOF
```

#### 步骤4：安装依赖

```bash
npm install
```

#### 步骤5：测试数据库连接

```bash
# 启动服务测试
node app.js

# 如果看到以下输出表示成功：
# ✅ AI Anime API服务器已启动
# 📍 监听地址: 0.0.0.0:3100
# 🗄️  数据库: chuamgwei_gateway
```

#### 步骤6：后台运行服务

```bash
# 方式1：使用 nohup（简单）
nohup node app.js > app.log 2>&1 &

# 方式2：使用 pm2（推荐，支持自动重启）
npm install -g pm2
pm2 start app.js --name ai-anime-api
pm2 save
pm2 startup  # 设置开机自启
```

#### 步骤7：验证服务

```bash
# 本地测试
curl http://localhost:3100
curl http://localhost:3100/ai-anime/user/login/health

# 外部测试（需要Nginx代理）
curl http://<SERVER_HOST>/ai-anime/user/login/health
```

---

### PM2 常用命令

```bash
pm2 list              # 查看所有进程
pm2 logs ai-anime-api # 查看日志
pm2 restart ai-anime-api  # 重启服务
pm2 stop ai-anime-api     # 停止服务
pm2 delete ai-anime-api   # 删除服务
```

---

### Nginx 配置参考

确保 Nginx 已配置反向代理：

```nginx
location /ai-anime/ {
    proxy_pass http://172.17.0.1:3100/ai-anime/;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection 'upgrade';
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_cache_bypass $http_upgrade;
}
```

---

### 更新部署

当代码有更新时：

```bash
# 1. 上传新代码
rsync -avz --progress src/ root@<SERVER_HOST>:/data/ai-anime-api/src/

# 2. SSH登录服务器
ssh root@<SERVER_HOST>

# 3. 重启服务
cd /data/ai-anime-api/src
pm2 restart ai-anime-api

# 4. 查看日志确认正常
pm2 logs ai-anime-api --lines 50
```

---

### 故障排查

```bash
# 查看服务状态
pm2 status

# 查看错误日志
pm2 logs ai-anime-api --err --lines 100

# 查看端口占用
netstat -tlnp | grep 3100

# 测试数据库连接
mysql -h <MYSQL_HOST> -u chuamgwei -p

# 查看Nginx错误日志
docker logs nginx-updates --tail 50
```

## 模块化优势

1. **代码组织清晰** - 按功能模块分离，易于维护
2. **职责分离** - 控制器、服务、路由各司其职
3. **可扩展性强** - 新功能只需添加对应模块
4. **测试友好** - 每个模块可独立测试
5. **团队协作** - 不同开发者可负责不同模块

## 迁移说明

从原始的 `ai-anime-login-api.js` 迁移到模块化架构：

1. **保持API兼容性** - 所有原有接口保持不变
2. **功能增强** - 新增管理、监控、文件服务功能
3. **性能优化** - 更好的错误处理和日志记录
4. **维护性提升** - 代码结构更清晰，便于后续开发

## 注意事项

- 确保数据库连接配置正确
- 管理员功能需要用户表中的 `is_admin` 字段
- 文件服务功能需要根据实际需求实现存储逻辑
- 建议在生产环境中配置适当的日志轮转
