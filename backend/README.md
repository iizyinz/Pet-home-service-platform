# 宠物上门服务 · 后端

基于 PRD《宠物上门服务小程序 V1.0》实现的企业级后端：用户 / 服务人员 / 管理员三端协同，围绕「工单」从下单到结算的闭环，并接入 **Cloudflare R2 对象存储**（图片）与 **DeepSeek 大模型**（SSE 流式问答）。

## 技术栈

| 层次 | 选型 | 说明 |
| --- | --- | --- |
| JDK | 21 | 虚拟线程（SSE 流式问答、并发落库） |
| 框架 | Spring Boot 3.5.14 | Web / Validation / WebSocket / AOP / Scheduling |
| ORM | MyBatis-Plus 3.5.7（spring-boot3 starter） | 分页插件、逻辑删除、自动填充、条件构造器 |
| 数据库 | MySQL 8（utf8mb4 / InnoDB） | 21 张表，金额 DECIMAL(10,2)，时区 Asia/Shanghai |
| 鉴权 | JWT（jjwt 0.12.6）+ 拦截器 + BCrypt | 角色隔离 + 归属校验 |
| 文档 | springdoc-openapi 2.8.6（Swagger UI） | 不使用已不兼容 Spring Boot 3 的 Springfox |
| 对象存储 | AWS SDK v2 S3 2.31.28 → Cloudflare R2 | 图片上传返回公开访问 URL，无本地磁盘兜底 |
| 流式问答 | SSE + JDK HttpClient | OpenAI 兼容接口（DeepSeek），系统提示词见 `AiService` |
| 实时 | Spring WebSocket | IM 聊天定向推送、在线会话映射 |
| 词云 | HanLP（portable 1.8.4） | 分词 / 词性过滤 / 词频 Top80 |
| 其它 | Lombok、Validation、MapStruct | 样板简化 / 参数校验 / DTO 映射 |

## 快速开始

```bash
# 1. 初始化数据库（建库建表，21 表 + 索引）
mysql -uroot -p < src/main/resources/db/schema.sql

# 2. 配置数据源（默认 localhost:3306/pet_service，账号默认 root）
#    密码等敏感项必须用环境变量注入：MYSQL_USER、MYSQL_PWD、JWT_SECRET、ADMIN_INIT_PASSWORD
#    参见项目根目录 .env.example

# 3. 启动（默认 dev profile，端口 8080）
./mvnw spring-boot:run
# 或打包运行
./mvnw -DskipTests package && java -jar target/backend-1.0.0.jar

# 4. 运行测试
./mvnw test
```

Windows 下把 `./mvnw` 换成 `.\mvnw.cmd`。

管理员账号不再内置默认口令：首次启动时读取环境变量 `ADMIN_INIT_PASSWORD`（8-32 位）自动创建 `admin`，
日志与代码中均不出现口令；未设置该变量且数据库中没有管理员时，启动会直接报错提示。

## 环境变量

**密钥一律用环境变量注入，不要写进代码仓库。**（样例见根目录 `.env.example`）

| 变量 | 作用 | 默认值 |
| --- | --- | --- |
| `MYSQL_PWD` | 数据库密码（**必填，无默认值**） | 无，缺失则启动失败 |
| `MYSQL_USER` | 数据库账号 | `root` |
| `JWT_SECRET` | JWT 签名密钥（**必填，无默认值**） | 无，缺失则启动失败 |
| `ADMIN_INIT_PASSWORD` | 首次启动创建管理员 `admin` 的初始口令（8-32 位） | 无，库中无管理员且未设置则启动失败 |
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
| `SPRINGDOC_API_DOCS_ENABLED` / `SPRINGDOC_SWAGGER_UI_ENABLED` | 生产环境临时开文档 | `false` |

Windows 写入用户级环境变量（新开终端生效）：

```powershell
[Environment]::SetEnvironmentVariable('LLM_API_KEY', 'sk-xxx', 'User')
[Environment]::SetEnvironmentVariable('R2_SECRET_ACCESS_KEY', 'xxx', 'User')
```

## 对象存储（Cloudflare R2）

`POST /api/files/upload`（multipart 字段 `file`，≤5MB，仅 jpg/jpeg/png/gif/webp）→ 返回 `{ "url": "https://pub-xxx.r2.dev/..." }`。

- 走 R2 的 **S3 兼容 API**（`software.amazon.awssdk:s3`），`R2StorageService` 懒加载构建 `S3Client`
- 对象 Key：`{prefix}/{yyyy}/{MM}/{dd}/{uuid}.{ext}`，例如 `pet/2026/10/12/6bfa97aa....png`
- 响应头 `Cache-Control: public, max-age=31536000, immutable`（图片不可变，可长期缓存）
- 未配置 R2 环境变量时：启动打印 WARN，上传报 `4002 文件上传失败`，**不做本地磁盘兜底**
- 本地上传已废弃：`app.upload.*` 仅供 `WebMvcConfig` 映射历史 `/uploads/**` 静态资源

> r2.dev 公开域有速率限制且不走边缘缓存，生产建议绑定自定义域——只改 `R2_PUBLIC_BASE_URL` 即可，历史图片链接不受影响。

## AI 智能问答（DeepSeek）

`/api/ai/**`：会话管理 + SSE 流式问答，前端页面 `/ai`。

| 接口 | 说明 |
| --- | --- |
| `POST /api/ai/conversations` | 新建会话 |
| `GET /api/ai/conversations` | 我的会话（按更新时间倒序，首问后自动用问题做标题） |
| `GET /api/ai/conversations/{id}/messages` | 历史消息（`role: user/assistant`） |
| `POST /api/ai/chat` | 提问，`text/event-stream` |
| `GET /api/ai/quick-questions` | 首页快捷问题 |

- 上下文组装：**系统提示词 + 会话历史（最近 20 条）+ 当前问题**；系统提示词定义在 `AiService.SYSTEM_PROMPT`（角色 / 安全准则 / 工作流 / 输出规范）
- SSE 事件：`message={delta}` 增量、`done={content}` 完成、`error={code,message}` 失败
- **成功才落库**（user + assistant 同批写入），失败只回 `error` 事件且不污染会话历史（AC-08）
- 模型异常统一 `3001 AI 服务暂时不可用`，会话不存在/越权 `3002`

## API 文档

- 开发环境（`dev`，默认）：Swagger UI → <http://localhost:8080/swagger-ui/index.html>，OpenAPI JSON → <http://localhost:8080/v3/api-docs>
- 生产环境（`prod`）：文档默认关闭，临时开启：

```bash
SPRINGDOC_API_DOCS_ENABLED=true SPRINGDOC_SWAGGER_UI_ENABLED=true java -jar target/backend-1.0.0.jar
```

## 统一约定

- 响应体：`{ code, message, data }`，`code=0` 成功；分页 `{ list, total, page, size }`
- 鉴权：`Authorization: Bearer <JWT>`；白名单：`/api/auth/**`、`/ws/**?token=`、文档与静态资源
- HTTP 状态：401 未登录、403 越权（AC-09）、400 参数校验；业务错误 HTTP 200 + 非 0 code
- 时间：`yyyy-MM-dd HH:mm:ss`（GMT+8）；金额：元（DECIMAL(10,2)）
- 常用错误码：`2002 非法状态流转`、`3001/3002 AI`、`4002 文件上传失败`、`1001 验证码失效`

## 接口总览

**用户端（USER）**

`AuthController` 登录/注册/验证码 · `UserController` 资料与钱包 · `PetController` 宠物 · `AddressController` 上门地址 · `ServiceCatalogController` 服务类型/项目/区域 · `OrderController` 下单与工单流转 · `ReviewController` 评价 · `StaffController` 服务人员主页与收藏 · `NoticeController` 公告 · `ContentController` 首页内容 · `FileController` 图片上传 · `AiController` AI 问答 · `ChatController` IM

**服务端（STAFF）**

`StaffWorkbenchController` 接单/服务进度/结算 · `StaffController` 档案与排班 · 聊天复用 `ChatController`

**管理端（ADMIN）**

`AbstractCrudController` 提供通用 CRUD 基类（分页 / 详情 / 新增 / 修改 / 删除 + 审计），其上派生：
`AdminUser` `AdminStaff` `AdminServiceType` `AdminServiceItem` `AdminServiceArea` `AdminOrder` `AdminReview` `AdminAnnouncement` `AdminBanner` `AdminOps`（操作日志） · `DashboardController` 数据看板与评价词云

## 目录结构

```
backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/backend/
    │   │   ├── BackendApplication.java      # 启动类（@MapperScan/@EnableScheduling）
    │   │   ├── common/                      # 统一响应、错误码、异常、上下文、审计、工具
    │   │   ├── config/                      # MyBatis-Plus/Jackson/OpenAPI/WebSocket/WebMvc/预置账号
    │   │   ├── security/                    # JWT + 鉴权拦截器
    │   │   ├── entity/                      # 21 张表实体（OpenAPI @Schema）
    │   │   ├── mapper/                      # MyBatis-Plus Mapper（含聚合 SQL）
    │   │   ├── dto/                         # 请求/响应 DTO（@Valid + @Schema）
    │   │   ├── enums/                       # 订单状态/动作枚举
    │   │   ├── service/
    │   │   │   ├── order/                   # OrderService + OrderStateMachine
    │   │   │   ├── finance/ review/ staff/ user/ master/ notice/
    │   │   │   ├── auth/                    # AuthService、CaptchaService
    │   │   │   ├── ai/                      # AiService、LlmClient（SSE）
    │   │   │   ├── chat/                    # 聊天落库与推送
    │   │   │   ├── storage/                 # R2StorageService（对象存储）
    │   │   │   └── admin/                   # DashboardService、WordCloudService
    │   │   ├── controller/                  # REST 接口（@Tag/@Operation）
    │   │   │   └── admin/                   # 管理端（通用 CRUD 基类 + 各资源）
    │   │   ├── chat/                        # WebSocket 在线管理与处理器
    │   │   └── job/                         # OrderTimeoutJob（超时取消/自动确认）
    │   └── resources/
    │       ├── application.yml              # 数据源/JWT/R2/LLM/文档开关（dev/prod）
    │       └── db/schema.sql                # 建库建表脚本（21 表 + 索引）
    └── test/java/com/example/backend/
        ├── OrderStateMachineTest.java       # 状态机单元测试（AC-03）
        ├── AiServicePromptTest.java         # 系统提示词契约测试
        └── service/storage/R2StorageServiceTest.java   # R2 真实上传测试（无环境变量自动跳过）
```

## 核心业务约束（对应 PRD 验收）

1. 下单扣款原子化（`UPDATE ... WHERE balance >= amount`）+ CONSUME 流水，失败整体回滚（AC-01/02）
2. 状态流转集中于 `OrderStateMachine`，条件 UPDATE 落库，并发/非法流转返回 2002（AC-03）
3. 取消/拒单/超时同事务退款 + REFUND 流水 + 通知（AC-04）
4. 进度已读：订单粒度 `progress_read_at`（AC-05）
5. 一单一评（uk_order）+ 聚合 SQL 重算信誉（AC-06）
6. 聊天先落库再 WebSocket 推送（AC-07）；AI SSE 失败不写入会话（AC-08）
7. 归属校验越权 403（AC-09）；看板/词云走聚合 SQL（AC-10）
8. 图片上传统一走 R2，接口契约保持 `POST /api/files/upload` → `{url}` 不变

## 测试

```bash
./mvnw test                                      # 状态机 + 提示词契约
./mvnw -Dtest=R2StorageServiceTest test          # 真实上传：需注入 R2_* 环境变量，否则自动 skip
```

- `OrderStateMachineTest`：合法/非法状态流转、并发语义（AC-03）
- `AiServicePromptTest`：系统提示词必须包含非兽医声明、拒绝有害建议、请遵医嘱、Markdown 输出等约束
- `R2StorageServiceTest`：真实上传 + 公开 URL GET 200 校验

## 日志与运维

- 日志文件：`./logs/pet-home-service.log`（`com.example.backend` 为 info）
- dev profile 开启 MyBatis SQL 打印，prod 关闭文档与 SQL 日志
- `OrderTimeoutJob`：`PENDING_CONFIRM` 超过 24h 自动取消退款，`PENDING_SETTLE` 超过 72h 自动完成（`app.order.*` 可配）
- 停机：`server.lifecycle.timeout-per-shutdown-phase: 30s`，优雅关闭
