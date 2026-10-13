# 宠物上门服务（Pet Home Service）

面向「宠物上门服务」场景的全栈项目：**用户 · 服务人员 · 管理员三端协同**，围绕**工单（服务订单）从下单到结算的闭环**，把上门服务做成一笔**可查、可管、可追责**的工单。

一套后端 + 两套前端：

| 端 | 载体 | 目录 | 说明 |
| --- | --- | --- | --- |
| 后端 | Spring Boot 3 REST + WebSocket | `backend/` | 21 张表、JWT 鉴权、订单状态机、对象存储、AI 问答 |
| Web 前端 | Vue 3 单页应用（三端合一） | `frontend/` | 用户门户 `/`、服务人员工作台 `/staff`、管理后台 `/admin` |
| 小程序 | uni-app（微信小程序等） | `uniapp/` | 用户端 23 个页面，5 个 tab |

> 产品需求、UI 页面清单、设计规范见 [`文档/`](文档/)，数据库脚本见 [`数据库/`](数据库/)。

---

## 功能特性

**用户端**
- 登录 / 注册（图形验证码防爆破）、个人资料、余额钱包（充值 / 消费 / 退款流水）
- 宠物档案、上门地址（省市区三级联动）、我的收藏、我的评价
- 服务浏览（服务类型 / 服务项目 / 服务区域）、服务人员主页与信誉
- 下单 → 余额支付 → 服务进度 → 确认完成 → 评价，全链路工单
- 即时聊天（WebSocket）、AI 宠物问答（SSE 流式）、公告与通知

**服务人员端（工作台）**
- 接单 / 拒单、开始服务、服务过程反馈、提交结算
- 排期、服务评价、与用户实时沟通

**管理端**
- 用户 / 服务人员管理（资质审核）、宠物、服务类型 / 项目 / 区域主数据
- 订单治理、评价管理、轮播图与公告运营、余额流水、操作审计日志
- 运营看板：趋势、排行、评分、评价词云（HanLP 分词）、日历热力

**平台能力**
- 订单状态机（所有状态变更强制过状态机，非法流转返回 `2002`）
- 定时任务：超 24h 未接单自动取消退款、超 72h 未确认自动完成开放评价
- 图片统一走 Cloudflare R2（S3 兼容 API），不落本地磁盘
- AI 问答接入 OpenAI 兼容接口（默认 DeepSeek），SSE 流式、成功才落库
- JWT + 拦截器角色隔离 + 归属校验（越权 403）、`@AuditLog` AOP 操作审计

---

## 目录结构

```
宠物/
├── backend/                 # Spring Boot 3.5 + JDK 21 后端
│   ├── src/main/java/com/example/backend/
│   │   ├── common/          # 统一响应、错误码、异常、审计、上下文、工具
│   │   ├── config/          # MyBatis-Plus / Jackson / OpenAPI / WebSocket / 预置账号
│   │   ├── security/        # JWT 签发校验 + 鉴权拦截器
│   │   ├── entity|mapper|dto|enums/
│   │   ├── service/         # order(状态机) finance review staff user master auth ai chat storage notice admin
│   │   ├── controller/      # REST 接口（含 admin/ 通用 CRUD 基类）
│   │   ├── chat/            # WebSocket 在线管理与消息推送
│   │   └── job/             # OrderTimeoutJob 超时处理
│   ├── src/main/resources/
│   │   ├── application.yml  # 数据源 / JWT / R2 / LLM / 文档开关（dev·prod）
│   │   └── db/schema.sql    # 权威建库建表脚本（21 表 + 索引）
│   └── README.md            # 后端详细说明（接口总览、错误码、业务约束）
│
├── frontend/                # Vue 3 + Vite 8 + Element Plus + TypeScript
│   ├── src/{api,router,layouts,views,components,stores,utils,styles,__tests__}
│   └── README.md            # 前端详细说明（三端结构、请求层、测试）
│
├── uniapp/                  # uni-app 用户端小程序（HBuilderX 项目）
│   ├── pages.json           # 23 个页面 / 5 个 tab
│   ├── manifest.json        # 平台与 appid 配置
│   ├── pages/ api/ components/ utils/
│   └── static/              # tabbar 与业务图标
│
│
├── 文档/                    # PRD
├── .env.example             # 环境变量样例（复制使用，勿提交真实值）
├── .gitignore               # 根级忽略规则（日志 / 上传 / 构建产物 / .env）
└── README.md                # 本文件
```

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | JDK 21 · Spring Boot 3.5.14（web / validation / websocket / aop / scheduling，虚拟线程）· MyBatis-Plus 3.5.7 · MySQL 8（utf8mb4） |
| 鉴权 | JWT（jjwt 0.12.6）+ Spring Security Crypto（BCrypt）+ 拦截器角色隔离 |
| 文档 | springdoc-openapi 2.8.6（Swagger UI） |
| 存储 / AI | AWS SDK v2 S3 → Cloudflare R2 · JDK HttpClient SSE → DeepSeek（OpenAI 兼容）· HanLP 词云 |
| Web 前端 | Vue 3.5（Composition API）· TypeScript · Vite 8 · Element Plus 2.14 · Vue Router 5 · Pinia 4 · ECharts 5 · axios · marked + dompurify · Vitest 4 |
| 小程序 | uni-app（`vueVersion: 3`，页面为 Options API 写法），HBuilderX 运行 |
| 数据库 | 21 张表：`sys_user pet user_address service_type service_area service_item staff_profile staff_item favorite service_order order_progress review balance_record chat_conversation chat_message ai_conversation ai_message notice announcement banner operation_log` |

---

## 环境要求

- JDK 21+（后端）、Maven Wrapper 已自带（无需预装 Maven）
- Node.js `^22.18.0 || >=24.12.0`（前端）
- MySQL 8.x
- HBuilderX（运行 uniapp 小程序）
- 可选：Cloudflare R2 账号（图片上传）、DeepSeek 等大模型 API Key（AI 问答）

---

## 快速开始

### 0. 配置环境变量

复制根目录样例并填入真实值（`.env` 已被 gitignore，**不要提交**）：

```bash
cp .env.example .env
```

必填三项，缺失时后端**启动直接失败**（不提供任何内置默认凭据）：

| 变量 | 说明 |
| --- | --- |
| `MYSQL_PWD` | 数据库密码（账号默认 `root`，可用 `MYSQL_USER` 覆盖） |
| `JWT_SECRET` | JWT HS256 签名密钥，建议 32 字节以上随机串，如 `openssl rand -base64 48` |
| `ADMIN_INIT_PASSWORD` | 首次启动创建管理员 `admin` 的初始口令（8–32 位；库中已有管理员时忽略） |

Spring Boot **不自动读取 `.env`**，请按下面任一方式注入：

```powershell
# PowerShell（当前会话）
$env:MYSQL_PWD="你的数据库密码"
$env:JWT_SECRET="你的JWT密钥"
$env:ADMIN_INIT_PASSWORD="你的管理员初始口令"
```

```bash
# Linux / macOS
export MYSQL_PWD=xxx JWT_SECRET=xxx ADMIN_INIT_PASSWORD=xxx
```

IDE（IntelliJ）可在 Run Configuration → *Environment variables* 中填写。

### 1. 初始化数据库

```bash
mysql -uroot -p < backend/src/main/resources/db/schema.sql   # 权威脚本：建库 pet_service + 21 表
# 或使用演示数据
mysql -uroot -p < 数据库/pet_service.sql
```

### 2. 启动后端（:8080）

```bash
cd backend
./mvnw spring-boot:run            # Windows: .\mvnw.cmd spring-boot:run
# 打包运行
./mvnw -DskipTests package && java -jar target/backend-1.0.0.jar
```

- 默认 `dev` profile，端口 `8080`，Swagger UI：<http://localhost:8080/swagger-ui/index.html>
- 首次启动（库中无管理员）会用 `ADMIN_INIT_PASSWORD` 创建 `admin`，**日志与代码均不出现口令**，登录后请立即修改

### 3. 启动 Web 前端（:5173）

```bash
cd frontend
npm install
npm run dev                      # http://localhost:5173
```

`vite.config.ts` 已把 `/api`、`/ws`(WebSocket)、`/uploads` 代理到 `http://localhost:8080`。

### 4. 运行小程序端

用 **HBuilderX 打开 `uniapp/` 目录** → 运行到浏览器 / 微信开发者工具（非 CLI 项目，无 `package.json`）。
接口基地址在 `uniapp/utils/request.js` 的 `BASE_URL`（默认 `http://localhost:8080`），开发者工具需勾选「不校验合法域名」。

---

## 环境变量清单

| 变量 | 作用 | 默认值 |
| --- | --- | --- |
| `MYSQL_PWD` | 数据库密码 | **无（必填）** |
| `MYSQL_USER` | 数据库账号 | `root` |
| `JWT_SECRET` | JWT 签名密钥 | **无（必填）** |
| `ADMIN_INIT_PASSWORD` | 初始化管理员 `admin` 口令（8–32 位） | **无（库中无管理员时必填）** |
| `R2_ACCOUNT_ID` | Cloudflare 账户 ID | 空 |
| `R2_ENDPOINT` | S3 端点，留空按账户推导 | `https://{accountId}.r2.cloudflarestorage.com` |
| `R2_REGION` | 区域 | `auto` |
| `R2_BUCKET` | 存储桶名 | 空 |
| `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` | R2 API 凭据 | 空 |
| `R2_PUBLIC_BASE_URL` | 公开访问域名（末尾不带 `/`） | 空 |
| `R2_KEY_PREFIX` | 对象 Key 前缀 | `pet` |
| `LLM_BASE_URL` | 大模型地址（OpenAI 兼容） | `https://api.deepseek.com` |
| `LLM_API_KEY` | 大模型 API Key | 空 |
| `LLM_MODEL` | 模型名 | `deepseek-flash` |
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | 生产环境临时开接口文档 | `false` |

未配置 R2 / LLM 时系统仍可启动，仅对应功能（图片上传、AI 问答）不可用。

---

## 三端对接

| 端 | 基地址 | 说明 |
| --- | --- | --- |
| 后端 | `http://localhost:8080` | REST 前缀 `/api`；WebSocket `/ws/chat?token=<JWT>` |
| Web 前端 | axios `baseURL: '/api'` | Vite 代理 `/api`、`/ws`(ws:true)、`/uploads` → 8080 |
| 小程序 | `http://localhost:8080` | 硬编码于 `uniapp/utils/request.js`；真机 / 生产改 HTTPS 域名并加入白名单 |

统一响应：`{ code, message, data }`（`code=0` 成功）；分页 `{ list, total, page, size }`；鉴权 `Authorization: Bearer <JWT>`。

---

## 核心业务逻辑

**订单状态机**（`OrderStateMachine`，EnumMap 转移表，条件 UPDATE 落库）：

```
PENDING_CONFIRM --accept--> CONFIRMED --start--> SERVING --finish--> PENDING_SETTLE --confirm--> COMPLETED
      |                        |                     |                     |
   reject / cancel / timeout   └─────────────────────┴───── feedback（不改状态）
      ↓
  CANCELLED（终态，同事务退款 + REFUND 流水 + 通知）
```

**定时任务**（`OrderTimeoutJob`，每 5 分钟）：
- `PENDING_CONFIRM` 超 24h → 自动取消并退款（幂等）
- `PENDING_SETTLE` 超 72h → 自动完成并开放评价（每单独立事务）

**关键业务约束**：下单扣款原子化（`UPDATE ... WHERE balance >= amount`）、一单一评（uk_order）+ 聚合 SQL 重算信誉、聊天先落库再 WebSocket 推送、AI SSE 失败不写入会话、归属校验越权 403。

详见 [`backend/README.md`](backend/README.md) 的「核心业务约束」与 [`文档/宠物上门服务小程序PRD.md`](文档/宠物上门服务小程序PRD.md)。

---

## 测试

```bash
# 后端（状态机 / 提示词契约 / R2 真实上传）
cd backend && ./mvnw test
./mvnw -Dtest=R2StorageServiceTest test     # 需注入 R2_* 环境变量，否则自动跳过

# 前端（Vitest 4，24 条用例）
cd frontend && npm run test:unit
```

---

## 安全与隐私

本项目已按「**凭据零入库、日志零敏感、产物零残留**」清理，约束如下：

1. **凭据只走环境变量**：`MYSQL_PWD`、`JWT_SECRET`、`ADMIN_INIT_PASSWORD`、`R2_*`、`LLM_API_KEY` 全部无默认值，配置文件与代码中不出现任何真实密钥 / 口令。
2. **不内置默认管理员口令**：管理端登录页不再展示任何默认账号提示；初始口令由 `ADMIN_INIT_PASSWORD` 注入，创建成功后日志**只打印账号不打印口令**。
3. **日志脱敏**：对象存储启动日志不打印 endpoint（含 Cloudflare Account ID），上传日志只打印对象 Key 不打印公开域名；测试报告同样做域名脱敏。
4. **构建与运行产物不入库**：`logs/`、`uploads/`、`target/`、`dist/`、`*.log`、`*.pid`、`.env` 已在根级与各级 `.gitignore` 中忽略（日志可能包含本机路径、账号、第三方域名）。
5. **数据库脚本已脱敏**：演示备份中的第三方存储域名、手机号替换为占位符，管理员口令哈希已移除（改由环境变量初始化）。

**上线前检查清单**

- [ ] 生成并注入全新的 `JWT_SECRET`（历史密钥视为已泄漏，需轮换）
- [ ] 设置强 `MYSQL_PWD`，数据库不对公网暴露
- [ ] 设置 `ADMIN_INIT_PASSWORD`，登录后立即修改管理员密码
- [ ] R2 建议绑定自定义域并关闭 `r2.dev` 公开访问，更新 `R2_PUBLIC_BASE_URL`
- [ ] `LLM_API_KEY` 走密钥管理服务注入
- [ ] 使用 `prod` profile 启动（接口文档默认关闭），前端替换为真实 API 域名并关闭 CORS 全放行
- [ ] 小程序上线前在 `manifest.json` 填写正式 appid（勿提交个人测试 appid）

---

## 文档

| 文件 | 内容 |
| --- | --- |
| [`文档/宠物上门服务小程序PRD.md`](文档/宠物上门服务小程序PRD.md) | PRD V1.0：角色权限、订单状态机、数据模型、接口契约、验收标准 |