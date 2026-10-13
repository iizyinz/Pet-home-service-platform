# 宠物上门服务 · 前端

Vue 3 单页应用，一套代码承载三端：**用户门户**（下单、养宠管理、AI 顾问）、**服务人员工作台**（接单与服务进度）、**管理后台**（运营与数据看板）。

## 技术栈

| 类别 | 选型 | 说明 |
| --- | --- | --- |
| 框架 | Vue 3.5（Composition API + `<script setup>`） | SFC + TypeScript |
| 语言 | TypeScript（`vue-tsc` 类型检查） | 构建前强制 type-check |
| 构建 | Vite 8 | 代理 `/api`、`/ws`、`/uploads` 到本地后端 |
| UI | Element Plus 2.14 + `@element-plus/icons-vue` | 主题变量按三端覆盖 |
| 路由/状态 | Vue Router 5（history 模式）+ Pinia 4 | 全局守卫做主题注入与角色隔离 |
| 图表 | ECharts 5 + echarts-wordcloud | 趋势/排行/评分/词云/日历热力 |
| 网络 | Axios | 统一拦截器解包、401 自动跳登录 |
| Markdown | marked + dompurify | AI 回答渲染，消毒后 `v-html`（防 XSS） |
| 样式 | Sass + CSS 变量 tokens | `--color-*`、`--font-size-*`、`--shadow-*` |
| 测试 | Vitest + @vue/test-utils（jsdom） | 组件交互与工具函数单测 |

## 快速开始

```sh
npm install

npm run dev          # 开发服务器 http://localhost:5173（已开 HMR）
npm run build        # type-check + 生产构建（run-p 并行）
npm run type-check   # 仅 vue-tsc
npm run build-only   # 仅 vite build
npm run test:unit    # Vitest 单测
npm run preview      # 预览构建产物
```

**代理**：`vite.config.ts` 把 `/api`、`/ws`（WebSocket）、`/uploads` 转发到 `http://localhost:8080`，所以本地需先启动后端（`backend/`，见其 README）。改后端端口时同步改这里的 `target`。

## 三端结构

| 端 | 路由前缀 | 布局 | 视图目录 | 菜单数据 |
| --- | --- | --- | --- | --- |
| 用户门户 | `/` | `PortalLayout`（顶栏 + 内容） | `views/portal`（18 个：下单、订单、钱包、宠护、AI 顾问…） | `portalTabs` |
| 服务人员 | `/staff` | `StaffLayout` | `views/staff`（8 个：工作台、接单、排班、评价…） | `staffMenus` |
| 管理后台 | `/admin` | `AdminLayout`（顶栏 + 可折叠侧边栏） | `views/admin`（14 个：看板、订单、用户、服务区域、内容运营…） | `adminMenus` |
| 公共 | `/login`、`/403`、`/404` | — | `views/auth`、`views/error` | — |

路由拆分在 `router/routes/{common,portal,staff,admin}.ts`，共约 49 条路由。

## 目录结构

```
frontend/
├── vite.config.ts          # 别名 @、插件、后端代理
├── vitest.config.ts        # 继承 vite 配置 + jsdom 环境
└── src/
    ├── main.ts             # 创建 app、注册 pinia/router/Element Plus
    ├── api/                # 11 个接口模块（ai/auth/catalog/chat/content/notices/orders/pets/reviews/staffs/user）
    ├── router/
    │   ├── index.ts        # 合并四组路由 + setupGuards
    │   ├── guards.ts       # 主题注入 / 登录跳转 / 角色校验 / 文档标题
    │   ├── menus.ts        # 三端菜单与分组图标（MenuGroup.icon）
    │   └── routes/         # common / portal / staff / admin
    ├── layouts/            # PortalLayout / StaffLayout / AdminLayout
    │   └── components/     # Breadcrumb、NoticeBell、SideMenu（分组折叠图标）
    ├── views/              # 页面（portal / staff / admin / auth / error）
    ├── components/
    │   ├── common/         # 12 个通用：PageCard、TableWrapper、SearchForm、StatCard、
    │   │                   #   StatusTag、ImageUploader、RatingStars、Empty/Error/Pending、
    │   │                   #   ConfirmAction、BaseChart
    │   ├── business/       # OrderProgressTimeline、OrderStatusSteps
    │   └── charts/         # TrendLine/Pie/RankBar/RatingBar/RatingGauge/CalendarHeatmap/WordCloud
    ├── composables/        # usePage（分页）、useChartTheme、useProfileSync（顶栏头像同步）
    ├── stores/             # auth（登录态）、app（三端主题）
    ├── utils/              # request、markdown、areaData + areaCascader、constants、export、chart-theme
    ├── styles/             # tokens.css（设计变量）、element-theme.css、global.css
    ├── types/              # 共享类型与 .d.ts
    └── __tests__/          # Vitest 用例
```

## 关键约定

### 请求层 `utils/request.ts`

- `axios.create({ baseURL: '/api', timeout: 15000 })`，请求拦截注入 `Authorization: Bearer <token>`
- 响应拦截统一解包 `{ code, message, data }`：`code=0` 直接返回 `data`；`401` 清 token 并跳 `/login?redirect=`；`403` 提示无权限；其余 `ElMessage` 报错
- 辅助方法：`get / post / put / del / upload`（带进度）、`ssePost`（POST + `text/event-stream` 流式解析）、`createChatSocket`（WebSocket，带 token 握手）
- 分页结构 `PageResult<T> = { list, total, page, size }`；登录态存 `localStorage`：`pet_token` / `pet_user`

### 路由与守卫 `router/guards.ts`

1. 按 `meta.theme`（无则按路径前缀）把 `portal/staff/admin` 主题类注入 `<html>`，下拉框、对话框等 teleport 元素也继承该端主色
2. 未登录访问受保护路由 → `/login?redirect=`
3. 已登录访问登录页 → 按角色回首页
4. 角色不符 → `/403`；同时设置 `document.title`

菜单与侧边栏渲染共用 `menus.ts` 的 `MenuNode / MenuGroup`，分组标题的图标由 `MenuGroup.icon` 提供（`SideMenu.vue` 标题 slot 渲染 `<el-icon>`）。

### 表格页模式

`usePage`（分页状态 + 请求封装）+ `SearchForm`（查询条件）+ `TableWrapper`（表格/加载/空态）+ `PageCard`（页面容器）+ `ConfirmAction`（危险操作二次确认），管理端列表页统一按此组合。

### Markdown 渲染 `utils/markdown.ts`

`renderMarkdown(src)` = `marked`（GFM + `breaks`）→ `DOMPurify` 消毒 → HTML。用于 AI 顾问的**历史回答气泡与流式临时气泡**（用户自己的消息和错误气泡保持纯文本）。模型输出属于不可信内容，必须消毒后再 `v-html`，防止 XSS。

### 行政区数据

- `utils/areaData.ts`：全国省 → 市 → 区数据源
- `utils/areaCascader.ts`：转成 `el-cascader` 选项、按位置生成 6 位区域编码、路径解析（`resolveAreaPath`）与反查回显（`findAreaPath`），供服务区域管理的级联选择使用

### 图片上传

`components/common/ImageUploader.vue` → `upload()` → `POST /api/files/upload` → 返回图片 URL（后端存 Cloudflare R2）。

### 头像同步

`composables/useProfileSync.ts` 在进入布局时拉取 `/user/profile`，保证三端顶栏头像与账号 `avatar` 一致。

## 测试

```sh
npm run test:unit                       # 全部
npx vitest run --config vitest.config.ts src/__tests__/markdown.spec.ts   # 单个文件
```

| 用例文件 | 覆盖 |
| --- | --- |
| `admin-layout.spec.ts` | 管理端顶栏/内容上下排布、侧边栏折叠显示图标、三端头像取账号 avatar |
| `area-cascader.spec.ts` | 省市区级联选项与编码全局唯一、路径解析/反查、**真实挂载 `el-cascader` 点击省/市/区任意一级选中且面板不关闭** |
| `base-chart.spec.ts` | 看板图表空态切换、初始化/setOption 刷新、实例销毁、loading 动画 |
| `markdown.spec.ts` | 标题/列表/引用/表格渲染、GFM 换行，以及脚本、事件属性、`javascript:` 链接的消毒 |

当前共 **4 个文件 / 24 条用例**。

## 构建与注意事项

- `npm run build` 会先跑 `vue-tsc`，类型错误会直接中断构建
- 产物 `dist/` 为纯静态资源，可交由后端托管或任意静态服务器托管（接口仍需指向 `/api`）
- 首包体积告警：`index-*.js` 较大是 Element Plus + ECharts + 路由表合并的结果；AI、看板、管理端各页面已按路由做了代码分割（懒加载 chunk）
- 生产环境需关闭后端 CORS 全放行并配置真实 API 域名
