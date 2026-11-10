# 通用文档格式智能校验系统

基于 **Vue 3 + Spring Boot 3.x + MySQL 8.0 + Apache POI + Redis** 的前后端分离文档格式校验系统。用户上传「标准模板」和「待校验文档」后，系统异步解析 DOCX 的字符、段落、编号、表格、图片、页面设置、页眉页脚等格式元数据，调用 DSH 大模型进行语义级格式比对，并将差异持久化；前端提供差异筛选、WPS 风格原版预览和锚点高亮。

> 当前仓库中的后端已通过 `mvn package`，前端已通过 `npm run build`，并使用示例 DOCX 完成注册、登录、个人中心、模板上传解析、异步任务、差异查询、模板下载的本地端到端冒烟。

---

## 1. 功能概览

- **账户体系**：登录 / 注册、个人中心、资料维护、修改密码、个人统计与最近任务。
- **模板库**：公共模板 / 个人模板、模板上传、预解析、元数据缓存、下载、逻辑删除。
- **文档解析**：基于 Apache POI 提取 DOCX 全量格式元数据，输出结构化 JSON。
- **智能比对**：DSH OpenAI 兼容 HTTP 客户端；内置固定系统提示词、动态用户提示词、超时重试、JSON 结构校验。
- **异步任务**：Spring `@Async` + Redis 可靠队列，支持排队、进度、成功/失败、历史查询、异常恢复。
- **差异定位**：统一锚点 `body.p[n]`、`body.table[n].r[r].c[c]`、`header[i].p[j]`、`footer[i].p[j]`，前端据此精确高亮。
- **结果展示**：按严重等级 / 差异类型 / 关键词筛选；`docx-preview` 渲染原始 DOCX 的 WPS 风格版面，并在页眉、正文、表格、页脚上叠加差异高亮与滚动定位。
- **自定义检索**：通过外部 YAML 配置关键词 / 正则规则，支持必须出现、禁止出现、正文/表格/页眉/页脚范围与严重等级，保存后自动热加载。
- **模板生成规则**：可从模板的目录 / 章节结构一键生成 YAML 检索规则，自动生成“必需章节”正则与占位符检查，默认关闭、按需启用。
- **演示模式**：`dsh.mock=true` 时无需任何模型密钥即可完整演示。
- **扩展能力**：策略模式扩展校验维度，模板方法固定主流程；存储层预留 MinIO 实现位。

---

## 2. 技术栈

| 层次 | 技术 |
|---|---|
| 前端 | Vue 3.5、Vite 5、Element Plus 2.8、Pinia 2、Axios、Vue Router 4、docx-preview 0.4 |
| 预览 | docx-preview 原版版式渲染（页眉/页脚/表格/图片/分页），DOM 差异锚点叠加 |
| 后端 | Spring Boot 3.3.5、Java 17、MyBatis-Plus 3.5.7、Spring Async、Actuator |
| 文档解析 | Apache POI 5.2.5（XWPF / OOXML） |
| 数据库 | MySQL 8.0（utf8mb4） |
| 缓存与队列 | Redis 6.2+（推荐 7.x） |
| 认证 | BCrypt + Redis Token（Bearer） |
| 构建 | Maven 3.9+、npm 9+ |

---

## 3. 系统架构

```text
┌──────────────┐      multipart       ┌──────────────────────────────┐
│ Vue 3 前端    │ ───────────────────> │ Spring Boot 后端              │
│ 模板/提交/结果 │ <─────────────────── │ Controller → Service → Mapper │
└──────────────┘   JSON / 进度轮询     └──────────────┬───────────────┘
                                                      │
                      ┌───────────────────────────────┼──────────────────────────┐
                      │                               │                          │
                ┌─────▼─────┐                  ┌──────▼──────┐            ┌──────▼──────┐
                │ MySQL 8.0 │                  │ Redis        │            │ 本地磁盘      │
                │ 模板/任务/ │                  │ 队列/进度/   │            │ DOCX 文件    │
                │ 差异明细   │                  │ Token        │            │ (预留 MinIO) │
                └───────────┘                  └──────┬──────┘            └─────────────┘
                                                      │
                                              ┌───────▼────────┐
                                              │ 异步校验 Worker │
                                              │ 模板方法 + 策略  │
                                              └───────┬────────┘
                                                      │ HTTP
                                              ┌───────▼────────┐
                                              │ DSH 大模型      │
                                              │ /v1/chat/...    │
                                              └────────────────┘
```

### 校验主流程

1. 模板上传时同步用 POI 解析，元数据写入 `doc_template.metadata_json`，同时记录解析状态。
2. 待校验文档上传后创建 `check_task`（`QUEUED`），文件落本地磁盘，任务 ID 进入 Redis 队列。
3. `TaskDispatcher` 定时从 `doccheck:queue` 取任务，`CheckTaskWorker` 在 `checkExecutor` 线程池异步执行。
4. `AbstractCheckPipeline.execute()` 以模板方法编排固定步骤：
   - 加载任务与文件；
   - 解析目标文档（或复用模板缓存）；
   - 执行本地确定性策略（字符、段落、编号、表格、图片、页面、页眉页脚）与 YAML 文本检索规则；
   - 调用 DSH 做语义级全量比对；
   - 合并、归一化、去重、排序；
   - 事务写入 `check_diff`，更新任务统计与状态；
   - 全程双写 Redis 进度与数据库状态。
5. 前端轮询任务进度，成功后用 `docx-preview` 渲染任务原始 DOCX，再按 `anchorPath` 在渲染 DOM 上叠加差异高亮。

---

## 4. 目录结构

```text
文档检测项目/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/doccheck/
│       │   ├── DocCheckApplication.java
│       │   ├── check/                    # 校验模板方法 + 策略
│       │   │   ├── AbstractCheckPipeline.java
│       │   │   ├── DefaultCheckPipeline.java
│       │   │   ├── FormatCheckStrategy.java
│       │   │   ├── CheckContext.java
│       │   │   ├── MetaIndex.java
│       │   │   └── strategy/             # 7 个格式维度 + YAML 检索策略
│       │   ├── common/                   # 统一响应/异常/用户上下文
│       │   ├── config/                   # 存储、DSH、校验、认证、线程池
│       │   ├── controller/               # Auth/Health/Template/Task
│       │   ├── domain/
│       │   │   ├── entity/               # 4 张表实体
│       │   │   ├── dto/                  # 请求/响应 VO
│       │   │   ├── enums/                # 任务状态/严重等级/模板范围
│       │   │   └── model/FormatDiff.java
│       │   ├── dsh/                      # DSH 客户端、提示词、Mock
│       │   ├── mapper/                   # MyBatis-Plus Mapper
│       │   ├── parser/                   # POI 解析器及元数据模型
│       │   ├── security/                 # 登录拦截器、初始化账号
│       │   └── service/                  # 模板/任务/队列/存储
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── db/schema.sql
│           └── prompts/format-check-system.txt
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/                          # auth/template/task/request
│       ├── components/                   # 上传/差异标签/进度/文档预览
│       ├── layouts/AppLayout.vue
│       ├── router/index.js
│       ├── stores/auth.js
│       ├── styles/main.css
│       ├── utils/format.js
│       └── views/
│           ├── LoginView.vue             # 登录 / 注册双模式
│           ├── ProfileView.vue           # 个人中心
│           ├── TemplateManageView.vue
│           ├── CheckSubmitView.vue
│           ├── TaskListView.vue
│           └── TaskDetailView.vue
├── docker-compose.yml                     # MySQL 8.0 + Redis 7
├── docker/mysql-init/                     # 首次建库后创建 doccheck 账号
├── .gitignore
└── README.md
```

---

## 5. 环境要求

| 软件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 或 21 | 编译目标为 Java 17 |
| Maven | 3.9+ | 构建后端 |
| Node.js | 18.18+ / 20+ | Vite 5 要求 |
| npm | 9+ | 安装前端依赖 |
| MySQL | 8.0 | 使用 `utf8mb4_0900_ai_ci` |
| Redis | 6.2+，推荐 7.x | 队列、进度、登录 Token |
| Docker | 可选 | 一键启动 MySQL/Redis |

默认端口：后端 `8080`，前端开发服务器 `5173`，MySQL `3306`，Redis `6379`。

---

## 6. 快速启动

### 6.1 启动 MySQL 与 Redis（推荐 Docker）

项目根目录已提供 `docker-compose.yml`：

```bash
docker compose up -d
docker compose ps
```

> `docker/mysql-init/01-doccheck-user.sql` 只会在 MySQL 数据卷首次初始化时执行。若之前已启动过旧版本，执行 `docker compose down -v`（会清空数据）后重新启动，或手工创建 `doccheck` 账号。

默认配置：

- MySQL：`127.0.0.1:3306`，数据库 `doc_format_check`，应用账号 `doccheck / doccheck123`；root 密码仅由容器初始化使用
- Redis：`127.0.0.1:6379`，无密码，AOF 开启

> 如果本机已有 MySQL/Redis（例如 phpStudy），可跳过 Docker。先执行 6.1.1 的建库/建账号 SQL，再按第 7 节配置连接。

#### 6.1.1 使用本机已有 MySQL（phpStudy / 手动安装）

以 root 登录 MySQL，执行一次：

```sql
CREATE DATABASE IF NOT EXISTS doc_format_check
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'doccheck'@'localhost' IDENTIFIED BY 'doccheck123';
CREATE USER IF NOT EXISTS 'doccheck'@'127.0.0.1' IDENTIFIED BY 'doccheck123';
GRANT ALL PRIVILEGES ON doc_format_check.* TO 'doccheck'@'localhost';
GRANT ALL PRIVILEGES ON doc_format_check.* TO 'doccheck'@'127.0.0.1';
FLUSH PRIVILEGES;
```

项目默认使用 `doccheck / doccheck123`，不会使用 root 连接。若端口不是 3306，设置 `MYSQL_PORT`。

### 6.2 启动后端

```bash
cd backend
mvn spring-boot:run
```

或先打包再运行：

```bash
cd backend
mvn clean package -DskipTests
java -jar target/doc-check-server-1.0.0.jar
```

首次启动会：

- 连接已创建的 `doc_format_check` 数据库（Docker 会自动创建，或按 6.1.1 手工创建）；
- 执行 `src/main/resources/db/schema.sql` 建表；
- 创建演示管理员 `admin / admin123`（仅当用户不存在时）。

健康检查：

```bash
curl http://127.0.0.1:8080/api/health
# {"code":0,"message":"success","data":{"status":"UP",...}}
```

### 6.3 启动前端

```bash
cd frontend
npm install
npm run dev
```

访问 `http://127.0.0.1:5173`，可使用默认管理员账号登录，也可在登录页切换到「注册」创建普通用户：

```text
管理员用户名：admin
管理员密码：admin123
```

登录后右上角用户菜单或侧边栏「个人中心」可维护显示名、邮箱与密码，并查看个人模板/任务/差异统计。

Vite 已配置 `/api` 代理到 `http://127.0.0.1:8080`，开发时无需额外处理跨域。

### 6.4 生产构建

前端：

```bash
cd frontend
npm run build
# 产物：frontend/dist
```

后端：

```bash
cd backend
mvn clean package -DskipTests
# 产物：backend/target/doc-check-server-1.0.0.jar
```

Nginx 参考配置（前端静态资源 + API 反向代理）：

```nginx
server {
    listen 80;
    server_name your-domain.com;

    root /opt/doc-check/frontend/dist;
    index index.html;
    client_max_body_size 55m;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 180s;
    }
}
```

> 前端使用 History 路由，Nginx 必须配置 `try_files ... /index.html`，否则刷新 `/tasks/1` 会 404。

---

## 7. 配置说明

主配置：`backend/src/main/resources/application.yml`；开发覆盖：`application-dev.yml`。默认 profile 为 `prod`（INFO 日志），本地开发用 `SPRING_PROFILES_ACTIVE=dev` 开启 DEBUG 与开发数据库覆盖。所有关键项均支持环境变量。

### 7.1 数据库与 Redis

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `MYSQL_HOST` | `127.0.0.1` | MySQL 主机 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DB` | `doc_format_check` | 数据库名 |
| `MYSQL_USER` | `doccheck` | 用户名 |
| `MYSQL_PASSWORD` | `doccheck123` | 密码（生产环境务必通过环境变量覆盖） |
| `REDIS_HOST` | `127.0.0.1` | Redis 主机 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `REDIS_DB` | `0` | Redis 库号 |

示例（PowerShell）：

```powershell
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PASSWORD = "your-password"
$env:REDIS_HOST = "127.0.0.1"
java -jar target/doc-check-server-1.0.0.jar
```

### 7.2 文件存储

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `STORAGE_TYPE` | `local` | `local`；`minio` 为预留扩展 |
| `STORAGE_ROOT` | `./data/uploads` | 本地文件根目录 |

存储路径形如：

```text
data/uploads/template/2026/11/07/{uuid}.docx
data/uploads/target/2026/11/07/{uuid}.docx
```

文件名为 UUID，数据库保存相对路径与 SHA-256；下载时根据数据库路径读取，避免路径穿越。

### 7.3 DSH 大模型

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `DSH_MOCK` | `true` | `true` 使用本地演示比对；`false` 调用真实 HTTP |
| `DSH_BASE_URL` | `https://api.dsh.example.com` | DSH 服务地址 |
| `DSH_API_KEY` | 空 | Bearer Token |
| `DSH_MODEL` | `dsh-chat` | 模型名 |
| `DSH_CHAT_PATH` | `/v1/chat/completions` | OpenAI 兼容路径 |

接入真实 DSH：

```bash
DSH_MOCK=false \
DSH_BASE_URL=https://your-dsh-host \
DSH_API_KEY=sk-xxxx \
DSH_MODEL=your-model \
java -jar target/doc-check-server-1.0.0.jar
```

请求结构（OpenAI 兼容）：

```json
{
  "model": "your-model",
  "temperature": 0.1,
  "max_tokens": 8192,
  "response_format": { "type": "json_object" },
  "messages": [
    { "role": "system", "content": "固定系统提示词..." },
    { "role": "user", "content": "模板元数据 + 目标元数据 + 自定义规则..." }
  ]
}
```

- 系统提示词固定从 `backend/src/main/resources/prompts/format-check-system.txt` 读取，不在代码中改写。
- `HttpDshClient` 支持连接超时、读取超时、429/5xx 重试、错误收敛、Markdown 代码围栏剥离、`diff_list` 结构校验。
- 若服务端不支持 `response_format`，将 `dsh.json-mode` 设为 `false`。

### 7.4 认证

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | 运行 profile；本地开发设为 `dev` |
| `AUTH_TRUST_PROXY` | `false` | 是否信任 X-Forwarded-For / X-Real-IP；仅在可信反向代理后开启 |
| `DEMO_USERNAME` | `admin` | 首次启动创建的管理员账号 |
| `DEMO_PASSWORD` | `admin123` | 首次启动创建的管理员密码 |

- 密码使用 BCrypt 存储。
- 登录成功后生成随机 Token，写入 Redis `auth:token:{token}`，默认 8 小时过期。
- 登录限流：同一 IP 10 次/分钟、同一账号 5 次/分钟；注册 IP 5 次/小时；超限返回 HTTP 429。
- 登录、注册、修改密码写入 `auth_audit` 审计表，不记录密码和 Token。
- 前端通过 `Authorization: Bearer {token}` 访问受保护接口。
- 生产环境务必修改默认密码，并通过 HTTPS 部署。

### 7.5 校验限制

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `validation.local-rules-enabled` | `true` | 是否合并本地确定性规则 |
| `validation.max-diffs` | `500` | 单任务最大差异数 |
| `validation.max-paragraphs` | `800` | 发送给 DSH 的最大段落数 |
| `validation.max-runs-per-paragraph` | `30` | 每段最大字符 run 数 |
| `validation.max-table-rows` | `60` | 表格最大行数 |
| `validation.search-rules-file` | `./config/search-rules.yml` | 外部 YAML 检索规则文件；不存在时回退 classpath 示例 |
| `validation.search-rules-watch-ms` | `15000` | 外部规则文件热加载轮询间隔 |
| `spring.servlet.multipart.max-file-size` | `50MB` | 单文件上限 |
| `validation.upload.max-uncompressed-mb` | `200` | DOCX 解压后总大小上限 |
| `validation.upload.max-entry-mb` | `80` | 单个 ZIP entry 解压上限 |
| `validation.upload.max-entries` | `5000` | ZIP entry 数量上限 |
| `validation.upload.max-compression-ratio` | `120` | 总解压大小 / 压缩包大小上限 |
| `check.queue.max-retries` | `2` | 可重试任务的最大重试次数 |
| `check.watchdog.timeout-minutes` | `15` | RUNNING 任务超时阈值 |
| `check.watchdog.interval-ms` | `30000` | 超时扫描间隔 |

---

## 8. API 说明

统一响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": "2026-11-07 19:00:00"
}
```

`code=0` 表示成功；业务异常使用对应 HTTP 状态码并返回非 0 `code`。

### 8.1 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，JSON `{username,password}` |
| POST | `/api/auth/register` | 注册，JSON `{username,displayName,email,password}`；成功后直接返回 Token |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/auth/profile` | 个人中心：资料、统计、最近 5 条任务 |
| PUT | `/api/auth/profile` | 更新资料，JSON `{displayName,email}` |
| PUT | `/api/auth/password` | 修改密码，JSON `{oldPassword,newPassword}` |
| POST | `/api/auth/logout` | 退出并删除 Redis Token |
| GET | `/api/health` | 健康检查，无需登录 |

登录 / 注册响应：

```json
{
  "token": "32 位随机串",
  "expiresIn": 28800,
  "user": { "id": 1, "username": "admin", "displayName": "演示管理员", "email": null, "role": "ADMIN" }
}
```

个人中心响应中的 `stats` 包含 `templateCount`、`taskCount`、`successTaskCount`、`diffCount`、`highCount`，`recentTasks` 为最近 5 条任务摘要。

### 8.2 模板

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/templates?page=1&size=10&scope=ALL&keyword=` | 分页查询；普通用户仅公共 + 本人 |
| POST | `/api/templates` | multipart：`file`、`name`、`description`、`category`、`scope` |
| GET | `/api/templates/{id}/metadata` | 获取预解析元数据 JSON |
| GET | `/api/templates/{id}/document` | inline 读取原始 DOCX，用于模板库 WPS 预览，不增加下载次数 |
| GET | `/api/templates/{id}/rules.yaml` | 根据模板目录 / 章节生成检索规则 YAML 并下载 |
| GET | `/api/templates/{id}/download` | 下载原始 DOCX |
| DELETE | `/api/templates/{id}` | 逻辑删除，仅本人或管理员 |

`scope` 取值：`PUBLIC`（仅管理员可创建）、`PERSONAL`。

### 8.3 校验任务

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/tasks` | multipart：`file`、`templateId`、`customRules`（可选） |
| GET | `/api/tasks?page=1&size=10&status=` | 任务历史 |
| GET | `/api/tasks/{id}` | 任务详情，含 `document`（其中 `comments` 为 Word 批注）、`diffs` |
| GET | `/api/tasks/{id}?light=true` | 轻量任务详情，不返回 `document` 和 `diffs` |
| GET | `/api/tasks/{id}/metadata` | 懒加载任务文档元数据（自动升级到 v6） |
| GET | `/api/tasks/{id}/diffs?page=1&size=200&severity=&type=&keyword=` | 差异服务端分页/筛选，`size` 上限 200 |
| GET | `/api/tasks/{id}/diff-types` | 当前任务的差异类型列表 |
| GET | `/api/tasks/{id}/progress` | 轻量进度查询 |
| GET | `/api/tasks/{id}/document` | 读取任务原始 DOCX，用于 WPS 风格预览（本人或管理员） |
| GET | `/api/tasks/{id}/images/{index}` | 读取任务 DOCX 内第 `index` 张图片（受权限保护） |

模板图片读取：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/templates/{id}/images/{index}` | 读取模板 DOCX 内第 `index` 张图片（公共模板或本人模板） |

图片不以内联 base64 写入任务详情。`document.images[index]` 提供 `containerType`、`partName`、`contentType`、`widthPt/heightPt`、`wrapType`、`behindText` 以及容器序号；前端凭这些信息请求二进制接口并渲染。`partName` 是 DOCX 包内路径（如 `/word/media/image1.png`），不暴露服务器磁盘路径。

创建任务响应：

```json
{ "taskId": 3, "taskNo": "DC202611071922470487", "status": "QUEUED", "progress": 0 }
```

任务状态：`QUEUED` → `RUNNING` → `SUCCESS` / `FAILED` / `DEAD`。可重试异常会回到 `QUEUED` 并增加 `retry_count`；重试耗尽进入 `DEAD` 并写入死信队列。

差异字段：

```json
{
  "diffNo": "D001",
  "seq": 1,
  "location": "body.p[0]",
  "anchorPath": "body.p[0]",
  "diffType": "字体",
  "severity": "high",
  "elementType": "text",
  "templateFormat": "黑体",
  "actualFormat": "微软雅黑",
  "contentSnippet": "项目验收报告",
  "suggestion": "将中文字体调整为模板要求的「黑体」"
}
```

### 8.4 锚点规范

| 元素 | 锚点格式 |
|---|---|
| 正文段落 | `body.p[全局段落序号]` |
| 正文表格单元格 | `body.table[全局表格序号].r[行号].c[列号]` |
| 页眉段落 | `header[页眉序号].p[段内序号]` |
| 页脚段落 | `footer[页脚序号].p[段内序号]` |

序号均从 `0` 开始；前端 `DocumentPreview.vue` 使用同一套锚点进行高亮与滚动定位。

图片沿用所在容器的锚点：正文图片定位到 `body.p[n]`，页眉/页脚图片定位到 `header[i].p[j]` / `footer[i].p[j]`，表格单元格图片定位到 `body.table[t].r[r].c[c]`。前端按 `containerType + paragraphIndex/headerIndex/footerIndex/tableIndex` 过滤图片，避免把页眉图片渲染进正文。

### 8.5 检索规则管理

仅管理员可调用；普通用户的校验任务会自动应用当前生效规则。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/rules/search` | 查看当前规则来源、启用状态、规则列表与解析错误 |
| POST | `/api/rules/search/reload` | 手动重新读取 YAML；失败时返回 400 并保留旧规则 |

返回示例：

```json
{
  "source": "file:D:\\文档检测项目\\backend\\config\\search-rules.yml",
  "version": 1,
  "enabled": true,
  "loadedAt": "2026-11-08T03:11:57",
  "rules": [
    {
      "id": "no-placeholder",
      "name": "未替换占位符",
      "pattern": "(X{3,}|TODO|待补充)",
      "match": "regex",
      "mode": "forbidden",
      "scope": "all",
      "severity": "middle",
      "enabled": true
    }
  ],
  "errors": []
}
```

---

## 9. 数据库设计

建表脚本：`backend/src/main/resources/db/schema.sql`。
已有数据库升级：`backend/src/main/resources/db/upgrade-20261111.sql`（新增 `check_task.retry_count` 与 `auth_audit`）。应用启动时 `DatabaseCompatibilityInitializer` 也会幂等检查并补充 `retry_count`；生产环境仍建议显式执行升级脚本。

| 表 | 作用 | 关键字段 |
|---|---|---|
| `sys_user` | 用户 | `username` 唯一、`password_hash`、`display_name`、`email`、`role`、`status` |
| `doc_template` | 模板库 | `scope`、`owner_id`、`parse_status`、`metadata_json`、`file_hash`、`download_count` |
| `check_task` | 校验任务 | `task_no`、`status`、`retry_count`、`progress`、`stage`、四类差异计数、`document_metadata`、`result_json` |
| `check_diff` | 差异明细 | `task_id`、`diff_no`、`seq`、`anchor_path`、`severity`、`diff_type`、`template_format`、`actual_format`、`suggestion` |
| `auth_audit` | 认证审计 | `username`、`ip`、`user_agent`、`action_type`、`success`、`created_at` |

主要索引：

- `uk_user_username(username)`
- `idx_template_scope_owner(scope, owner_id, deleted)`
- `idx_task_user_created(user_id, created_at)`
- `idx_task_status_updated(status, updated_at)`
- `idx_diff_task_seq(task_id, seq)`
- `idx_diff_task_severity(task_id, severity)`
- `idx_diff_task_anchor(task_id, anchor_path)`
- `idx_auth_audit_user_time(username, created_at)`
- `idx_auth_audit_action_time(action_type, created_at)`
- `idx_auth_audit_ip_time(ip, created_at)`

所有业务表使用 `utf8mb4` / `utf8mb4_0900_ai_ci`，并带字段注释；模板与任务使用 `deleted` 逻辑删除。

---

## 10. 核心设计说明

### 10.1 模板方法模式

`backend/src/main/java/com/doccheck/check/AbstractCheckPipeline.java` 固定校验骨架：

```text
加载任务 → 解析目标文档 → 本地策略前置校验 → DSH 全量比对
→ 合并/去重/排序/统计 → 事务持久化 → 更新任务状态与进度
```

`DefaultCheckPipeline` 只负责组装依赖；新增流程步骤时继承 `AbstractCheckPipeline`，不需要改 Controller 或任务队列。

### 10.2 策略模式

`FormatCheckStrategy` 定义统一接口：

```java
String name();
String dimension();
String promptGuidance();
List<FormatDiff> precheck(CheckContext context);
```

当前实现 7 个格式维度 + 1 个 YAML 文本检索维度 + 1 个模板批注维度：

- `CharacterFormatStrategy`：字体、字号、字重、颜色、下划线、字符间距，以及中文引号 `w:hint` 导致的“宋体/Times New Roman”字体差异；
- `ParagraphFormatStrategy`：对齐、缩进、行距、段前段后、段前分页等；
- `NumberingFormatStrategy`：多级编号、编号格式、起始值；
- `TableFormatStrategy`：表格整体对齐、布局、边框、表头跨页重复，以及表头/正文区域的底纹、加粗、垂直对齐等格式模式；单元格文字对齐受 Word/WPS 表格样式影响，不作为差异；行数、列数、列名属于内容规模，不作为格式硬约束；
- `ImageFormatStrategy`：图片尺寸、环绕方式、衬于文字下方/浮于文字上方，以及图片所在容器（正文段落、页眉页脚、表格单元格）；图片数量由内容决定，不单独判错；不比较图片内容、文件名等业务信息。
- `PageLayoutStrategy`：纸张、方向、页边距、分栏；
- `HeaderFooterStrategy`：页眉页脚内容、距离、奇偶页差异。
- `YamlSearchStrategy`：读取外部 YAML，按关键词/正则在正文、表格、页眉、页脚中做必须出现/禁止出现检索。
- `TemplateCommentStrategy`：读取模板 Word 批注，解析字数、关键词数量等数值约束，并按章节标题匹配目标文档同名章节后检查。

每个策略通过 `promptGuidance()` 把领域规则注入 DSH 用户提示词；能在本地确定性判断的规则通过 `precheck()` 先产出，再与 DSH 结果合并去重。

表格策略采用“格式模式”而非“逐格对齐”：不比较表格行数、列数、列名和数据条数，也不按列位置强行配对单元格，更不把单元格文字对齐作为差异。Word/WPS 会结合表格样式、单元格宽度和段落缩进重新排版，XML 中的 `w:jc` 未必等于最终视觉效果；只要边框、表头重复、底纹、字重、垂直对齐等版式一致，就应判定为合格。模板中的表格行通常是示例（如参与者词汇表只列 3 行），实际文档可以按业务增减参与者。列数不同时只比较区域级格式模式，避免把语义不同的示例表误判为结构错误。

#### 数量不是格式

模板是格式示例，不是内容模板。以下数量差异不作为格式错误：

- 小标题、段落、章节数量不同；
- 表格行数、列数、表格数量不同；
- 图片数量不同；
- 页眉/页脚数量随分节数变化。

系统按章节标题、元素类型和语义相似度配对，只比较配对元素的字体、字号、缩进、间距、边框、底纹、环绕方式等格式属性。页眉页脚按 `default/first/even` 类型配对，不按数组下标硬配。

例外：模板批注明确要求固定数量（如“关键词 3-8 个”），或用户在 YAML 中显式配置 `required`/`forbidden` 检索规则时，数量才参与判定。

#### 段落对齐与结果联动

`ParagraphAligner` 先按章节标题匹配模板与目标文档的同名章节，再在章节内按“标题 / 正文 / 关键词行”角色配对，避免模板摘要正文与目标 `ABSTRACT` 标题因全文下标相同而被误配。目录行（含制表符）不参与配对。字符、段落、编号策略与 Mock DSH 均复用该对齐结果。

差异卡片中的 `字重` 不再直接显示 `true/false`，而是显示“加粗 / 未加粗 / 未设置”。WPS 预览中的差异徽标可点击：点击后会自动切换到对应差异、滚动左侧列表，并在预览中高亮定位。

### 10.3 DSH 客户端

- 接口：`backend/src/main/java/com/doccheck/dsh/DshClient.java`
- 演示实现：`MockDshClient`（`dsh.mock=true`）
- 真实实现：`HttpDshClient`（`dsh.mock=false`）
- 提示词组装：`DshPromptBuilder`
- 固定系统提示词：`backend/src/main/resources/prompts/format-check-system.txt`

真实模式只依赖 OpenAI 兼容的 `/v1/chat/completions` 协议，可接入任何兼容网关。

### 10.4 异步任务与 Redis 队列

- 队列：`doccheck:queue`
- 处理中队列：`doccheck:queue:processing`
- 取出方式：`RPOPLPUSH`，处理成功后 `LREM` 确认，失败任务保留在 processing 供排查/恢复
- 进度 Hash：`doccheck:task:progress:{taskId}`，TTL 2 天
- 线程池：`checkExecutor`，核心 2、最大 4、队列 100、`CallerRunsPolicy`
- 启动时 `TaskDispatcher` 执行 `recoverProcessing()`，把异常退出时残留的 processing 任务放回队列

### 10.5 存储扩展 MinIO

`StorageService` 是存储抽象：

```java
StoredFile store(MultipartFile file, String category);
Path resolve(String relativePath);
void delete(String relativePath);
```

本地实现 `LocalStorageService` 使用 `@ConditionalOnProperty(storage.type=local)`。接入 MinIO 时：

1. 新建 `MinioStorageService implements StorageService`；
2. 加 `@ConditionalOnProperty(storage.type=minio)`；
3. 在 `StorageProperties` 中补充 endpoint/accessKey/secretKey/bucket；
4. 数据库仍保存对象 Key，下载时返回预签名 URL 或流。

业务层只依赖 `StorageService`，不需要改模板/任务服务。

### 10.6 前端预览：WPS 风格原版渲染

任务详情默认使用 `docx-preview` 渲染任务原始 DOCX，而不是用元数据重绘：

1. 前端请求 `GET /api/tasks/{id}/document` 获取原始文件 Blob；
2. `renderAsync(blob, body, styleHost, options)` 生成带页眉、页脚、表格、图片和分页的真实版式；
3. 按 `anchorPath` 在渲染后的 DOM 上叠加 `.wps-diff` 高亮、数量徽标和 active 状态；
4. 左侧差异列表点击后滚动到对应元素，支持“WPS 版式 / 结构标注”切换、70%–150% 缩放和自适应宽度。

`backend/TaskService.document()` 先做任务归属校验，再由 `TaskController` 以 `inline` 方式返回 DOCX。预览文件与解析、校验使用同一份磁盘文件，保证看到的就是被校验的文档。

WPS 渲染会包含解析器跳过的目录域（TOC）段落，因此 `body.p[n]` 不能直接按数组下标命中。`DocumentPreview.vue` 的 `buildWpsParagraphIndexMap()` 会把元数据段落按文本顺序映射到渲染段落，`resolveWpsTarget()` 优先使用完整文本匹配，再使用映射/序号回退；目录、编号标题等场景不会把高亮落到相邻正文。

部分论文会在第一章使用独立的 `numId`，后续章节共用另一个 `numId`，`docx-preview` 会把它们当成两套计数器，导致第一章的二级标题显示为 `0.1`。预览样式对这种情况做了计数器同步，使第一章与后续章节都按 `1.1 / 2.1` 连续显示。

`docx-preview` 是纯前端排版引擎，字体替换、复杂浮动对象等极端版式与 WPS 仍可能有细微差异；常规论文的页眉页脚、分页、表格、图片、编号已按原 DOCX 渲染。

“结构标注”模式保留原元数据自绘视图，作为 WPS 渲染失败或需要查看解析字段时的 fallback。

模板库复用同一个 `DocumentPreview` 组件：卡片上的“预览”按钮打开大尺寸弹窗，先请求 `/api/templates/{id}/metadata` 获取统计与结构数据，再通过 `/api/templates/{id}/document` 渲染原版 DOCX。预览接口只校验模板可见性、不累加 `download_count`，因此管理员浏览公共模板不会被记为下载。

### 10.7 图片识别与安全预览

`DocxParser` 会扫描四类图片容器：

- 正文段落：`body.p[n]`；
- 页眉：`header[i].p[j]`；
- 页脚：`footer[i].p[j]`；
- 表格单元格：`body.table[t].r[r].c[c]`。

`ImageMeta` 保存图片序号、容器信息、宽高（pt）、环绕方式、是否衬于文字下方、MIME、包内 `partName` 和替代文本。图片二进制不进入 `document_metadata` 或 `metadata_json`，避免任务详情 JSON 过大。

“结构标注”模式通过 `GET /api/tasks/{id}/images/{index}` 按需获取图片，用 `URL.createObjectURL` 渲染，并在组件卸载时回收 URL；“WPS 版式”模式由 `docx-preview` 直接读取 DOCX 包内图片。后端读取前会校验任务归属（或模板 PUBLIC/本人权限），只允许读取该 DOCX 包内 `word/media/` 下的资源，单张上限 20MB。

图片功能上线前生成的历史任务没有 `containerType` / `partName`。打开任务详情时，如果 `metadataVersion < 2`，后端会自动重新解析一次 DOCX、回写元数据；图片接口本身也对旧元数据提供按图片名称/段落序号重新定位的兜底。因此历史任务无需重新校验即可正常显示图片；前端图片请求失败时只降级为不渲染，不会连续弹出全局错误提示。

图片格式差异沿用所在容器的 anchor：正文图片高亮段落，页眉/页脚图片高亮对应页眉页脚行，单元格图片高亮单元格。图片内容、文件名和图片数量不参与格式判定；只比较尺寸、环绕方式和图文层级。

### 10.8 YAML 自定义检索规则

系统支持在不改代码的情况下增加文本检索规则。默认读取后端启动目录下的 `config/search-rules.yml`；文件不存在时回退 classpath 示例 `backend/src/main/resources/rules/search-rules.yml`。规则文件修改后约 15 秒自动热加载，也可以调用管理员接口手动 reload。

```yaml
version: 1
enabled: true
rules:
  - id: no-placeholder
    name: 未替换占位符
    pattern: '(X{3,}|TODO|待补充)'
    match: regex          # contains | regex
    mode: forbidden       # required | forbidden
    scope: all            # body | table | header | footer | all
    severity: middle      # high | middle | low
    message: 文档中仍存在未替换的占位符
    suggestion: 将占位符替换为实际内容后再提交
    caseSensitive: false
    maxHits: 10
    enabled: true
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| `id` | 否 | 规则唯一标识，缺省自动生成 |
| `name` | 否 | 展示名称，会出现在差异位置说明中 |
| `pattern` | 是 | 关键词或正则表达式 |
| `match` | 否 | `contains`（默认）或 `regex` |
| `mode` | 否 | `required`（必须出现）或 `forbidden`（禁止出现，默认） |
| `scope` | 否 | `body` / `table` / `header` / `footer` / `all`（默认） |
| `severity` | 否 | `high` / `middle`（默认）/ `low` |
| `message` | 否 | 命中或缺失时的补充说明 |
| `suggestion` | 否 | 修复建议 |
| `caseSensitive` | 否 | 是否区分大小写，默认 `false` |
| `maxHits` | 否 | 单条规则最多输出的命中数，默认 5，范围 1–50 |
| `enabled` | 否 | 是否启用该规则，默认 `true` |

行为说明：

- `required`：在指定范围内全文检索，未找到时生成一条“规则检索”差异，锚点取该范围第一个文本单元。
- `forbidden`：每个文本单元最多输出第一个命中，按 `maxHits` 截断，避免同一段重复刷屏。
- 命中结果统一写入 `check_diff`，`diff_type = 规则检索`，前端差异类型筛选会自动出现该类型。
- 表格按 `body.table[t].r[r].c[c]` 定位；页眉页脚按 `header[i].p[j]` / `footer[i].p[j]` 定位；正文按 `body.p[n]` 定位。
- 单条规则格式错误会被跳过，并在 `GET /api/rules/search` 的 `errors` 中返回；语法错误或根节点错误会导致 reload 失败，旧规则继续生效。
- 规则只读取解析后的纯文本，不读取图片 OCR，也不会修改原文档；`enabled: false` 的全局开关或单条规则都不会参与校验。
- 默认示例规则全部为 `enabled: false`，避免升级后突然给历史文档增加差异；使用时复制到 `backend/config/search-rules.yml` 并按需开启。

#### 从模板生成 YAML

模板管理页每张模板卡片的「生成检索规则」按钮会调用 `GET /api/templates/{id}/rules.yaml`，根据模板目录生成一份可直接使用的规则文件。

- 只解析带制表符和页码的目录行，例如 `第1章 绪论\t1`、`1.2 研究背景\t1`、`摘要\tI`，避免把正文误判为章节。
- 自动剥离章节编号和页码，只保留标题，并用 `[\s\u3000]*` 连接字符生成正则，兼容“绪　　论”“第1章 绪论”等写法。
- 最多生成 40 条章节规则，超出时优先保留章、节两级；每条规则为 `required`、`body`、默认 `enabled: false`。
- 额外生成一条 `tpl-placeholder` 禁止规则，检查 `XXX`、`TODO`、`待补充`、`请填写` 等占位符。
- 文件头注释包含模板名、结构统计、页面尺寸和页边距；文件名格式为 `模板名-检索规则.yml`。
- 下载后保存到 `backend/config/search-rules.yml`，按需开启规则，约 15 秒热加载或调用 reload 接口即可生效。

### 10.9 模板批注与章节级检索

模板中的 Word 批注会随解析进入 `document.comments`，不再只是“看不见的说明”：

- `DocxParser` 读取 `w:commentRangeStart`，将批注关联到 `body.p[n]`，记录作者、时间、正文和锚点；元数据版本升为 `6`，旧模板/旧任务在读取时自动重解析升级。
- 模板库预览和任务详情页工具栏显示「批注 N」。点击批注会切换到结构标注并定位到对应段落，方便对照模板要求。
- `TemplateCommentStrategy` 解析批注中的可量化约束，目前支持：
  - 字数范围：`300-600 字`、`300～600字` 等；
  - 关键词数量：`关键词 3-8 个`。
- 批注约束按“批注所在模板章节”生效：用 `DocumentSections` 按标题切分模板与目标文档，匹配同名章节后，在目标章节内统计字符数/关键词数，避免全文段落序号错位。
- 字数统计忽略空格、全角空格和换行；关键词支持中文/英文冒号、中文/英文分号分隔。
- 对于“黑体三号、固定值 23 磅、两端对齐”等语义型批注，会注入 DSH 用户提示词，要求按章节对齐并优先满足批注；配置 `DSH_API_KEY` 并将 `dsh.mock` 设为 `false` 后由真实模型处理。
- 本地规则只负责可确定计算的约束；复杂语义、版式意图仍由 DSH 判断，两者结果统一进入差异列表。

> 当前限制：批注只解析 Word 原生批注，不解析图片中的文字；本地暂不逐条解释所有自然语言批注，无法量化的部分交给 DSH。

### 10.10 段落样式继承

Word 中大量段落只设置 `pStyle`，缩进、行距、对齐写在样式定义里，段落本身没有 `w:pPr`。Apache POI 的 `paragraph.getIndentationFirstLine()` 等 getter 不会自动展开样式继承，直接读取会得到 `0`。

`DocxParser` 现在按以下顺序解析段落属性：

1. 段落直接格式 `w:pPr`；
2. 段落样式 `w:pStyle`；
3. 样式的 `w:basedOn` 链；
4. 默认 `Normal` 样式。

支持从 `CTPPr` 与 `CTPPrGeneral` 两种 Schema 类型中读取 `w:ind`、`w:spacing`、`w:jc`、`w:outlineLvl`。例如默认正文样式中的：

```xml
<w:ind w:firstLineChars="200" w:firstLine="480"/>
<w:spacing w:line="460" w:lineRule="exact"/>
<w:jc w:val="both"/>
```

会解析为：首行缩进 `24pt` / `200` 字符单位、固定行距 `23pt`、两端对齐。模板与目标文档使用同一套解析逻辑，因此不会再把“模板有缩进、文档缩进为 0”误报为格式差异。

元数据版本为 `6`；模板缓存或历史任务低于该版本时，会在读取详情或任务执行前自动重新解析并回写。

### 10.11 中文引号字体识别

中文引号 `“ ” ‘ ’` 属于“歧义字符”：Word/WPS 根据 Run 的 `w:rFonts/@w:hint` 决定它使用东亚字体还是西文字体。

- `w:hint="eastAsia"`：引号使用 `w:eastAsia`（例如宋体）；
- `w:hint="default"` 或 `w:hint="ascii"`：引号使用 `w:ascii`（例如 Times New Roman）。

模板中的示例引号通常显式设置 `w:hint="eastAsia"`，而复制/编辑后的正文可能变成 `default`，此时普通的中文字体比较仍然看到“宋体 → 宋体”，无法发现差异。

`DocxParser` 现在同时解析：

1. Run 的直接字体与 `w:hint`；
2. 字符样式字体；
3. 段落样式及 `basedOn` 链；
4. 默认 `Normal` 字体。

`CharacterFormatStrategy` 汇总模板中引号实际使用的字体作为期望值；目标文档中引号实际落入不同字体时，输出「引号字体」差异并定位到对应段落，例如：

```text
模板：宋体  ->  实际：Times New Roman
```

---

### 10.12 可靠性与安全加固

- **队列容量感知**：`TaskDispatcher` 只在 `checkExecutor` 有空闲线程、队列有空位时领取任务，避免 Redis 队列无限堆积。
- **超时看门狗**：`CheckTaskWatchdog` 每 30 秒扫描 RUNNING 超过 15 分钟的任务，条件更新为 FAILED，并清理 processing 队列。
- **失败重试**：网络超时、429/5xx、临时 IO 异常最多重试 2 次；重试耗尽进入 `DEAD`，同时写入 `doccheck:queue:dead` 死信队列。
- **上传防护**：校验扩展名与 `PK` 文件头；限制 ZIP entry 数、单 entry 大小、总解压大小与压缩比；同时设置 POI `ZipSecureFile` 阈值。
- **登录限流与审计**：登录 IP 10 次/分钟、账号 5 次/分钟，注册 IP 5 次/小时；登录、注册、修改密码写入 `auth_audit`，不记录密码与 Token。
- **自动化测试**：`mvn test` 当前 11 个用例，覆盖 DOCX 安全校验、登录限流、任务终态、文件哈希和实例队列；CI 同时执行后端测试/打包与前端构建。

已有数据库升级：

```bash
mysql -u doccheck -p doc_format_check < backend/src/main/resources/db/upgrade-20261111.sql
```

---


### 10.13 多实例、线程池与载荷优化

- **多实例队列**：每个实例使用独立的 `doccheck:queue:processing:{instanceId}`，实例心跳写入 `doccheck:instances`（TTL 90 秒）。实例异常退出后，其他实例通过 `recoverOrphaned()` 回收其 processing 任务；可用 `INSTANCE_ID` 或 `check.instance-id` 指定实例名。
- **线程池隔离**：`parseExecutor` 负责 DOCX 解析，`dshExecutor` 负责模型调用，避免慢模型请求占满解析线程；解析超时 120 秒、模型超时 180 秒。
- **文件与解析复用**：任务落盘前计算 SHA-256；同指纹文档复用已存文件和 v6 解析元数据，减少重复上传与解析。
- **详情载荷优化**：默认 `GET /api/tasks/{id}` 使用 light 模式，只返回任务统计；文档元数据和差异通过独立接口懒加载，差异首批 200 条、后台续拉，服务端支持严重等级/类型/关键词过滤。
- **组件拆分**：批注面板、差异面板、预览样式已分别抽为 `DocumentCommentPanel.vue`、`TaskDiffPanel.vue`、`DocumentPreview.css`；解析器拆出 `DocxFormatSupport.java`、`DocxRunParser.java`、`DocxStyleSupport.java`。当前 `TaskDetailView.vue` 约 720 行、`DocumentPreview.vue` 约 950 行、`DocxParser.java` 约 990 行。
- **测试**：`mvn test` 当前 11 个用例，覆盖 DOCX 安全、登录限流、任务终态、文件哈希、实例队列。

---

## 11. 关键文件清单

### 后端

| 文件 | 说明 |
|---|---|
| `backend/pom.xml` | 依赖与 Java 17 编译配置 |
| `backend/src/main/resources/application.yml` | 主配置 |
| `backend/src/main/resources/application-dev.yml` | 开发环境覆盖 |
| `backend/src/main/resources/db/schema.sql` | 完整建表 SQL |
| `backend/src/main/resources/db/upgrade-20261111.sql` | 已有数据库升级脚本 |
| `backend/src/main/resources/prompts/format-check-system.txt` | 固定系统提示词 |
| `backend/src/main/java/com/doccheck/parser/DocxFormatSupport.java` | 解析格式换算工具 |
| `backend/src/main/java/com/doccheck/parser/DocxRunParser.java` | Run 字符格式与字体继承解析 |
| `backend/src/main/java/com/doccheck/parser/DocxStyleSupport.java` | 文档样式元数据解析 |
| `backend/src/main/java/com/doccheck/service/CheckTaskWatchdog.java` | RUNNING 任务超时看门狗 |
| `backend/src/main/java/com/doccheck/service/DocxSecurityValidator.java` | DOCX 魔数与 Zip 炸弹防护 |
| `backend/src/main/java/com/doccheck/service/storage/FileHashes.java` | 文件 SHA-256 指纹 |
| `frontend/src/components/TaskDiffPanel.vue` | 差异筛选与列表组件 |
| `frontend/src/components/DocumentCommentPanel.vue` | 模板/文档批注面板 |
| `backend/src/main/java/com/doccheck/parser/DocxParser.java` | POI 全量格式解析（含批注锚点） |
| `backend/src/main/java/com/doccheck/parser/model/CommentMeta.java` | Word 批注元数据 |
| `backend/src/main/java/com/doccheck/check/DocumentSections.java` | 按标题切分章节并匹配同名章节 |
| `backend/src/main/java/com/doccheck/check/ParagraphAligner.java` | 按章节与标题/正文角色对齐段落 |
| `backend/src/main/java/com/doccheck/check/strategy/TemplateCommentStrategy.java` | 批注字数/关键词数量约束 |
| `backend/src/main/java/com/doccheck/dsh/HttpDshClient.java` | DSH HTTP 客户端 |
| `backend/src/main/java/com/doccheck/dsh/DshPromptBuilder.java` | 提示词组装 |
| `backend/src/main/java/com/doccheck/check/AbstractCheckPipeline.java` | 模板方法主流程 |
| `backend/src/main/java/com/doccheck/check/strategy/*.java` | 7 个格式策略 + YAML 检索策略 + 模板批注策略 |
| `backend/src/main/java/com/doccheck/service/SearchRuleService.java` | YAML 规则加载、校验与热更新 |
| `backend/src/main/java/com/doccheck/service/TemplateRuleYamlService.java` | 从模板目录生成检索规则 YAML |
| `backend/src/main/java/com/doccheck/service/GeneratedRuleYaml.java` | 生成结果（文件名 + YAML 内容） |
| `backend/config/search-rules.yml` | 可直接编辑的外部检索规则示例 |
| `backend/src/main/java/com/doccheck/service/TaskService.java` | 任务创建/执行/持久化 |
| `backend/src/main/java/com/doccheck/service/CheckQueueService.java` | Redis 队列/进度/死信 |
| `backend/src/main/java/com/doccheck/service/TaskDispatcher.java` | 按线程池容量领取任务 |
| `backend/src/main/java/com/doccheck/service/CheckTaskWatchdog.java` | RUNNING 超时看门狗 |
| `backend/src/main/java/com/doccheck/service/DocxSecurityValidator.java` | DOCX 魔数/ZIP 炸弹校验 |
| `backend/src/main/java/com/doccheck/service/LoginRateLimiter.java` | 登录/注册限流 |
| `backend/src/main/java/com/doccheck/service/AuthAuditService.java` | 认证审计 |
| `backend/src/main/java/com/doccheck/service/storage/StorageService.java` | 存储抽象（MinIO 扩展点） |
| `backend/src/main/java/com/doccheck/controller/*.java` | REST 接口 |

### 前端

| 文件 | 说明 |
|---|---|
| `frontend/src/views/LoginView.vue` | 登录页 |
| `frontend/src/views/TemplateManageView.vue` | 模板管理页 |
| `frontend/src/views/CheckSubmitView.vue` | 校验提交页 |
| `frontend/src/views/TaskListView.vue` | 任务历史页 |
| `frontend/src/views/TaskDetailView.vue` | 结果详情页 |
| `frontend/src/components/FileUploader.vue` | 通用文件上传 |
| `frontend/src/components/DiffTag.vue` | 差异标签 |
| `frontend/src/components/ProgressPanel.vue` | 进度条 |
| `frontend/src/components/DocumentPreview.vue` | 文档预览 + 高亮 |
| `frontend/src/api/request.js` | Axios 拦截器/Token/401 处理 |
| `frontend/src/router/index.js` | 路由与登录守卫 |

---

## 12. 本地验证记录

以下为当前工作区的实际验证结果（示例文档由本地脚本生成，仅用于冒烟）：

- 后端 `mvn package -DskipTests`：**BUILD SUCCESS**
- 产物：`backend/target/doc-check-server-1.0.0.jar`，约 60.6 MB
- 前端 `npm run build`：**成功**，1702 modules transformed，产物 `frontend/dist`
- `GET /api/health`：`UP`
- 登录 `admin/admin123`：成功
- 模板上传：`parseStatus=SUCCESS`，解析出 4 个正文段落、1 个表格
- 提交任务并轮询：`QUEUED → RUNNING → SUCCESS`
- 示例任务结果：17 条差异（高危 8、中危 6、低危 3），摘要字段与差异明细一致
- 动态表格回归：3 列 3 行示例模板 vs 5 列 2 行/5 行业务表，表格差异均为 0；去掉边框后仍能检出 1 条表格边框差异
- 模板下载：HTTP 200，DOCX 文件头 `PK`，38,502 bytes
- 模板 YAML 生成：论文模板生成 40 条章节规则 + 1 条占位符规则，`GET /api/templates/{id}/rules.yaml` 返回 200 / `application/yaml`；开启首条规则后 reload 成功、0 errors
- 未登录访问受保护接口：HTTP 401

> 本地验证使用了 phpStudy MySQL 8.0.12 与一个仅用于冒烟的 Node Redis 兼容服务；正式运行请使用 Redis 7。

---

## 13. 常见问题

### Q1：后端启动报数据库连接失败

确认 MySQL 已启动、端口正确，且已按 6.1.1 创建数据库与 `doccheck` 账号并授权。项目不再自动建库；`doccheck` 只需要 `doc_format_check.*` 的建表/读写权限。

### Q2：Redis 连接失败

确认 Redis 监听 6379，且 `requirepass` 与 `REDIS_PASSWORD` 一致。Redis 不可用时，登录与任务队列都会失败。

### Q3：上传大文件返回 413

后端默认单文件 50MB；Nginx 还需设置 `client_max_body_size 55m;`。如需更大文件，同时调整 `spring.servlet.multipart.max-file-size` 与 `max-request-size`。

### Q4：前端刷新子路由 404

History 路由需要服务端回退到 `index.html`，参见第 6.4 节 Nginx 配置。

### Q5：DSH 调用超时或返回非 JSON

- 检查 `DSH_BASE_URL` 是否包含协议与端口；
- 检查 `DSH_CHAT_PATH` 是否为 `/v1/chat/completions`；
- 增大 `dsh.timeout-seconds`；
- 若网关不支持 `response_format`，设置 `dsh.json-mode=false`；
- 查看后端日志中的 `DSH 返回 HTTP xxx` 与响应体摘要。

### Q6：演示模式没有真实模型效果

默认 `DSH_MOCK=true`，使用本地规则生成差异以保证零密钥可运行。接入真实模型请设置 `DSH_MOCK=false` 并配置 `DSH_API_KEY`。

### Q7：Maven 下载依赖慢

可在 `~/.m2/settings.xml` 配置阿里云镜像：

```xml
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### Q8：模板解析失败

仅支持 `.docx`（OOXML），不支持旧版 `.doc`。解析失败时模板 `parseStatus=FAILED`，`parseMessage` 会记录原因；可下载原文件确认是否为损坏文档或加密文档。

---

## 14. 安全与生产建议

- 修改默认管理员密码与数据库/Redis 密码，不要使用仓库中的演示值。
- 使用 HTTPS，Token 仅通过 `Authorization` 头传输；当前 Token 存 Redis，适合单服务或多实例共享。
- 上传文件仅按 UUID 存储，下载基于数据库路径，已做路径穿越校验；生产环境建议增加病毒扫描与文件类型魔数校验。
- 大模型提示词包含文档元数据，敏感场景请在接入真实 DSH 前做脱敏与合规评估。
- 任务队列当前为单 Redis 实例；生产可换 Redis Cluster/Sentinel，并将本地磁盘替换为 MinIO。
- 建议为 `/api/auth/login` 增加限流与登录审计。
- 定期清理 `data/uploads` 中未被数据库引用的孤儿文件。

---

## 15. 许可

本项目为示例工程，可按需修改与二次开发。

---

## 附录：完整源码文件清单

以下路径均相对于项目根目录 `D:\文档检测项目`。

```text
.gitignore
README.md
backend/pom.xml
backend/config/search-rules.yml
backend/src/main/java/com/doccheck/DocCheckApplication.java
backend/src/main/java/com/doccheck/check/AbstractCheckPipeline.java
backend/src/main/java/com/doccheck/check/CheckContext.java
backend/src/main/java/com/doccheck/check/CheckOutcome.java
backend/src/main/java/com/doccheck/check/DefaultCheckPipeline.java
backend/src/main/java/com/doccheck/check/DiffSupport.java
backend/src/main/java/com/doccheck/check/DocumentSections.java
backend/src/main/java/com/doccheck/check/ParagraphAligner.java
backend/src/main/java/com/doccheck/check/FormatCheckStrategy.java
backend/src/main/java/com/doccheck/check/MetaIndex.java
backend/src/main/java/com/doccheck/check/ProgressCallback.java
backend/src/main/java/com/doccheck/check/strategy/CharacterFormatStrategy.java
backend/src/main/java/com/doccheck/check/strategy/HeaderFooterStrategy.java
backend/src/main/java/com/doccheck/check/strategy/ImageFormatStrategy.java
backend/src/main/java/com/doccheck/check/strategy/NumberingFormatStrategy.java
backend/src/main/java/com/doccheck/check/strategy/PageLayoutStrategy.java
backend/src/main/java/com/doccheck/check/strategy/ParagraphFormatStrategy.java
backend/src/main/java/com/doccheck/check/strategy/TableFormatStrategy.java
backend/src/main/java/com/doccheck/check/strategy/TemplateCommentStrategy.java
backend/src/main/java/com/doccheck/check/strategy/YamlSearchStrategy.java
backend/src/main/java/com/doccheck/common/ApiResponse.java
backend/src/main/java/com/doccheck/common/BizException.java
backend/src/main/java/com/doccheck/common/GlobalExceptionHandler.java
backend/src/main/java/com/doccheck/common/PageResult.java
backend/src/main/java/com/doccheck/common/UserContext.java
backend/src/main/java/com/doccheck/config/AsyncConfig.java
backend/src/main/java/com/doccheck/config/AuthProperties.java
backend/src/main/java/com/doccheck/config/DshProperties.java
backend/src/main/java/com/doccheck/config/MybatisPlusConfig.java
backend/src/main/java/com/doccheck/config/StorageProperties.java
backend/src/main/java/com/doccheck/config/ValidationProperties.java
backend/src/main/java/com/doccheck/controller/AuthController.java
backend/src/main/java/com/doccheck/controller/DocumentImageController.java
backend/src/main/java/com/doccheck/controller/HealthController.java
backend/src/main/java/com/doccheck/controller/RuleController.java
backend/src/main/java/com/doccheck/controller/TaskController.java
backend/src/main/java/com/doccheck/controller/TemplateController.java
backend/src/main/java/com/doccheck/domain/dto/CreateTaskResponse.java
backend/src/main/java/com/doccheck/domain/dto/DiffVO.java
backend/src/main/java/com/doccheck/domain/dto/ChangePasswordRequest.java
backend/src/main/java/com/doccheck/domain/dto/LoginRequest.java
backend/src/main/java/com/doccheck/domain/dto/LoginResponse.java
backend/src/main/java/com/doccheck/domain/dto/ProfileStatsVO.java
backend/src/main/java/com/doccheck/domain/dto/ProfileTaskVO.java
backend/src/main/java/com/doccheck/domain/dto/RegisterRequest.java
backend/src/main/java/com/doccheck/domain/dto/SearchRuleSnapshotVO.java
backend/src/main/java/com/doccheck/domain/dto/TaskDetailVO.java
backend/src/main/java/com/doccheck/domain/dto/TaskVO.java
backend/src/main/java/com/doccheck/domain/dto/TemplateVO.java
backend/src/main/java/com/doccheck/domain/dto/UpdateProfileRequest.java
backend/src/main/java/com/doccheck/domain/dto/UserProfileVO.java
backend/src/main/java/com/doccheck/domain/dto/UserVO.java
backend/src/main/java/com/doccheck/domain/entity/CheckDiff.java
backend/src/main/java/com/doccheck/domain/entity/CheckTask.java
backend/src/main/java/com/doccheck/domain/entity/DocTemplate.java
backend/src/main/java/com/doccheck/domain/entity/SysUser.java
backend/src/main/java/com/doccheck/domain/enums/Severity.java
backend/src/main/java/com/doccheck/domain/enums/TaskStatus.java
backend/src/main/java/com/doccheck/domain/enums/TemplateScope.java
backend/src/main/java/com/doccheck/domain/model/FormatDiff.java
backend/src/main/java/com/doccheck/dsh/AnchorParser.java
backend/src/main/java/com/doccheck/dsh/DshClient.java
backend/src/main/java/com/doccheck/dsh/DshCompareRequest.java
backend/src/main/java/com/doccheck/dsh/DshCompareResult.java
backend/src/main/java/com/doccheck/dsh/DshPromptBuilder.java
backend/src/main/java/com/doccheck/dsh/HttpDshClient.java
backend/src/main/java/com/doccheck/dsh/MockDshClient.java
backend/src/main/java/com/doccheck/mapper/CheckDiffMapper.java
backend/src/main/java/com/doccheck/mapper/CheckTaskMapper.java
backend/src/main/java/com/doccheck/mapper/DocTemplateMapper.java
backend/src/main/java/com/doccheck/mapper/SysUserMapper.java
backend/src/main/java/com/doccheck/parser/DocxParser.java
backend/src/main/java/com/doccheck/parser/model/BlockMeta.java
backend/src/main/java/com/doccheck/parser/model/CommentMeta.java
backend/src/main/java/com/doccheck/parser/model/DocumentMeta.java
backend/src/main/java/com/doccheck/parser/model/DocumentStatistics.java
backend/src/main/java/com/doccheck/parser/model/DocumentStylesMeta.java
backend/src/main/java/com/doccheck/parser/model/HeaderFooterMeta.java
backend/src/main/java/com/doccheck/parser/model/ImageMeta.java
backend/src/main/java/com/doccheck/parser/model/IndentMeta.java
backend/src/main/java/com/doccheck/parser/model/NumberingMeta.java
backend/src/main/java/com/doccheck/parser/model/PageSetupMeta.java
backend/src/main/java/com/doccheck/parser/model/ParagraphMeta.java
backend/src/main/java/com/doccheck/parser/model/ParagraphSpacingMeta.java
backend/src/main/java/com/doccheck/parser/model/RunMeta.java
backend/src/main/java/com/doccheck/parser/model/SectionMeta.java
backend/src/main/java/com/doccheck/parser/model/StyleMeta.java
backend/src/main/java/com/doccheck/parser/model/TableCellMeta.java
backend/src/main/java/com/doccheck/parser/model/TableMeta.java
backend/src/main/java/com/doccheck/parser/model/TableRowMeta.java
backend/src/main/java/com/doccheck/rule/SearchRule.java
backend/src/main/java/com/doccheck/security/AuthInterceptor.java
backend/src/main/java/com/doccheck/security/DataInitializer.java
backend/src/main/java/com/doccheck/security/WebMvcConfig.java
backend/src/main/java/com/doccheck/service/AuthService.java
backend/src/main/java/com/doccheck/service/CheckQueueService.java
backend/src/main/java/com/doccheck/service/CheckTaskWorker.java
backend/src/main/java/com/doccheck/service/DocumentImage.java
backend/src/main/java/com/doccheck/service/DocumentImageService.java
backend/src/main/java/com/doccheck/service/GeneratedRuleYaml.java
backend/src/main/java/com/doccheck/service/SearchRuleService.java
backend/src/main/java/com/doccheck/service/TemplateRuleYamlService.java
backend/src/main/java/com/doccheck/service/TaskDispatcher.java
backend/src/main/java/com/doccheck/service/TaskService.java
backend/src/main/java/com/doccheck/service/TemplateDownload.java
backend/src/main/java/com/doccheck/service/TemplateService.java
backend/src/main/java/com/doccheck/service/storage/LocalStorageService.java
backend/src/main/java/com/doccheck/service/storage/StorageService.java
backend/src/main/java/com/doccheck/service/storage/StoredFile.java
backend/src/main/resources/application-dev.yml
backend/src/main/resources/application.yml
backend/src/main/resources/db/schema.sql
backend/src/main/resources/prompts/format-check-system.txt
backend/src/main/resources/rules/search-rules.yml
docker-compose.yml
docker/mysql-init/01-doccheck-user.sql
frontend/index.html
frontend/package-lock.json
frontend/package.json
frontend/src/App.vue
frontend/src/api/auth.js
frontend/src/api/request.js
frontend/src/api/task.js
frontend/src/api/template.js
frontend/src/components/DiffTag.vue
frontend/src/components/DocumentPreview.vue
frontend/src/components/FileUploader.vue
frontend/src/components/ProgressPanel.vue
frontend/src/layouts/AppLayout.vue
frontend/src/main.js
frontend/src/router/index.js
frontend/src/stores/auth.js
frontend/src/styles/main.css
frontend/src/utils/format.js
frontend/src/views/CheckSubmitView.vue
frontend/src/views/LoginView.vue
frontend/src/views/ProfileView.vue
frontend/src/views/TaskDetailView.vue
frontend/src/views/TaskListView.vue
frontend/src/views/TemplateManageView.vue
frontend/vite.config.js
```
