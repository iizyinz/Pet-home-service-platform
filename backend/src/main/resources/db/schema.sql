-- ============================================================
-- 宠物上门服务小程序 数据库初始化脚本（MySQL 8 / utf8mb4 / InnoDB）
-- 金额统一 DECIMAL(10,2)（元）；时间 GMT+8；主键自增 BIGINT
-- ============================================================
CREATE DATABASE IF NOT EXISTS pet_service DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE pet_service;

-- ---------------- 账号（用户/服务人员/管理员统一） ----------------
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(64)  NOT NULL COMMENT '登录名',
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  nickname      VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
  avatar        VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  phone         VARCHAR(20)  DEFAULT NULL COMMENT '手机号（展示脱敏）',
  role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 USER/STAFF/ADMIN',
  balance       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '账户余额（元）',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 冻结',
  last_login_at DATETIME     DEFAULT NULL COMMENT '最后登录时间',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_role (role),
  KEY idx_phone (phone)
) ENGINE=InnoDB COMMENT='账号表（三类角色统一）';

-- ---------------- 宠物档案 ----------------
DROP TABLE IF EXISTS pet;
CREATE TABLE pet (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id      BIGINT       NOT NULL COMMENT '主人（sys_user.id）',
  name         VARCHAR(64)  NOT NULL COMMENT '宠物昵称',
  breed        VARCHAR(64)  DEFAULT NULL COMMENT '品种',
  age          VARCHAR(32)  DEFAULT NULL COMMENT '年龄描述',
  weight_kg    DECIMAL(5,2) DEFAULT NULL COMMENT '体重(kg)',
  health_info  VARCHAR(500) DEFAULT NULL COMMENT '健康信息',
  special_need VARCHAR(500) DEFAULT NULL COMMENT '特殊需求',
  avatar       VARCHAR(255) DEFAULT NULL COMMENT '宠物头像',
  deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 1 删除',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='宠物档案';

-- ---------------- 上门地址 ----------------
DROP TABLE IF EXISTS user_address;
CREATE TABLE user_address (
  id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT      NOT NULL COMMENT '归属用户',
  contact_name  VARCHAR(64) NOT NULL COMMENT '联系人',
  contact_phone VARCHAR(20) NOT NULL COMMENT '联系电话',
  region        VARCHAR(128) DEFAULT NULL COMMENT '省市区',
  detail        VARCHAR(255) NOT NULL COMMENT '详细地址',
  is_default    TINYINT     NOT NULL DEFAULT 0 COMMENT '1 默认地址',
  deleted       TINYINT     NOT NULL DEFAULT 0,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='上门地址';

-- ---------------- 服务类型 ----------------
DROP TABLE IF EXISTS service_type;
CREATE TABLE service_type (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  name       VARCHAR(64) NOT NULL COMMENT '类型名称',
  icon       VARCHAR(255) DEFAULT NULL COMMENT '图标',
  sort       INT         NOT NULL DEFAULT 0 COMMENT '排序',
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  deleted    TINYINT     NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_name (name)
) ENGINE=InnoDB COMMENT='服务类型（分类）';

-- ---------------- 服务区域 ----------------
DROP TABLE IF EXISTS service_area;
CREATE TABLE service_area (
  id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  name       VARCHAR(64) NOT NULL COMMENT '区域名称',
  code       VARCHAR(32) NOT NULL COMMENT '区域编码',
  parent_id  BIGINT      DEFAULT 0 COMMENT '父级ID（0 顶级）',
  level      TINYINT     NOT NULL DEFAULT 1 COMMENT '层级 1 市 2 区',
  status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  deleted    TINYINT     NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_code (code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB COMMENT='服务区域';

-- ---------------- 服务项目 ----------------
DROP TABLE IF EXISTS service_item;
CREATE TABLE service_item (
  id                BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  type_id           BIGINT        NOT NULL COMMENT '所属服务类型',
  name              VARCHAR(128)  NOT NULL COMMENT '项目名称',
  price             DECIMAL(10,2) NOT NULL COMMENT '单价（元/次）',
  duration_minutes  INT           NOT NULL DEFAULT 60 COMMENT '服务时长(分钟)',
  cover_url         VARCHAR(255)  DEFAULT NULL COMMENT '封面图',
  applicable_breeds JSON          DEFAULT NULL COMMENT '适用品种数组',
  notice            VARCHAR(500)  DEFAULT NULL COMMENT '注意事项',
  status            TINYINT       NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
  deleted           TINYINT       NOT NULL DEFAULT 0,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_type (type_id),
  KEY idx_status (status)
) ENGINE=InnoDB COMMENT='服务项目';

-- ---------------- 服务人员档案 ----------------
DROP TABLE IF EXISTS staff_profile;
CREATE TABLE staff_profile (
  user_id         BIGINT       NOT NULL COMMENT '账号ID（主键，1:1 sys_user）',
  intro           TEXT         COMMENT '个人简介',
  experience_years INT         DEFAULT 0 COMMENT '从业年限',
  good_at_breeds  JSON         DEFAULT NULL COMMENT '擅长品种ID数组',
  area_id         BIGINT       DEFAULT NULL COMMENT '服务区域',
  cert_status     TINYINT      NOT NULL DEFAULT 0 COMMENT '0 待审核 1 已通过 2 已驳回',
  cert_images     JSON         DEFAULT NULL COMMENT '资质证书图片数组',
  reject_reason   VARCHAR(255) DEFAULT NULL COMMENT '驳回原因',
  avg_rating      DECIMAL(2,1) NOT NULL DEFAULT 0.0 COMMENT '综合评分(1位小数)',
  completed_count INT          NOT NULL DEFAULT 0 COMMENT '累计完成订单数',
  status          TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 下架',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  KEY idx_area (area_id),
  KEY idx_cert (cert_status)
) ENGINE=InnoDB COMMENT='服务人员档案';

-- ---------------- 服务人员-可服务项目 ----------------
DROP TABLE IF EXISTS staff_item;
CREATE TABLE staff_item (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  staff_id   BIGINT   NOT NULL COMMENT '服务人员',
  item_id    BIGINT   NOT NULL COMMENT '服务项目',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_staff_item (staff_id, item_id),
  KEY idx_item (item_id)
) ENGINE=InnoDB COMMENT='服务人员可服务项目关联';

-- ---------------- 收藏 ----------------
DROP TABLE IF EXISTS favorite;
CREATE TABLE favorite (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT   NOT NULL COMMENT '用户',
  staff_id   BIGINT   NOT NULL COMMENT '服务人员',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_staff (user_id, staff_id)
) ENGINE=InnoDB COMMENT='服务人员收藏';

-- ---------------- 服务订单（核心） ----------------
DROP TABLE IF EXISTS service_order;
CREATE TABLE service_order (
  id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_no         VARCHAR(32)   NOT NULL COMMENT 'SO 开头订单号',
  user_id          BIGINT        NOT NULL COMMENT '下单用户',
  staff_id         BIGINT        DEFAULT NULL COMMENT '接单服务人员',
  pet_id           BIGINT        NOT NULL COMMENT '宠物',
  service_item_id  BIGINT        NOT NULL COMMENT '服务项目',
  address_id       BIGINT        NOT NULL COMMENT '上门地址',
  item_name        VARCHAR(128)  NOT NULL COMMENT '服务项目名称快照',
  amount           DECIMAL(10,2) NOT NULL COMMENT '订单金额(元)',
  status           VARCHAR(20)   NOT NULL DEFAULT 'PENDING_CONFIRM' COMMENT '状态机 code',
  appointment_time DATETIME      NOT NULL COMMENT '预约服务时间',
  start_time       DATETIME      DEFAULT NULL COMMENT '实际开始时间',
  finish_time      DATETIME      DEFAULT NULL COMMENT '服务师标记完成时间',
  confirm_time     DATETIME      DEFAULT NULL COMMENT '用户确认完成时间',
  cancel_reason    VARCHAR(255)  DEFAULT NULL COMMENT '取消/拒单原因',
  remark           VARCHAR(255)  DEFAULT NULL COMMENT '用户备注',
  progress_read_at DATETIME      DEFAULT NULL COMMENT '用户查看进度最后阅读时间(V1订单粒度)',
  created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_user_status (user_id, status),
  KEY idx_staff_status (staff_id, status),
  KEY idx_status_created (status, created_at),
  KEY idx_appointment (appointment_time)
) ENGINE=InnoDB COMMENT='服务订单（状态机核心表）';

-- ---------------- 服务过程反馈 ----------------
DROP TABLE IF EXISTS order_progress;
CREATE TABLE order_progress (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id     BIGINT       NOT NULL COMMENT '订单',
  title        VARCHAR(128) NOT NULL COMMENT '标题',
  images       JSON         DEFAULT NULL COMMENT '图片数组',
  service_time VARCHAR(64)  DEFAULT NULL COMMENT '服务时间/说明',
  created_by   BIGINT       NOT NULL COMMENT '发布人',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_order (order_id)
) ENGINE=InnoDB COMMENT='服务过程反馈';

-- ---------------- 服务评价 ----------------
DROP TABLE IF EXISTS review;
CREATE TABLE review (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  order_id   BIGINT       NOT NULL COMMENT '订单(一单一评)',
  user_id    BIGINT       NOT NULL COMMENT '评价人',
  staff_id   BIGINT       NOT NULL COMMENT '被评服务师',
  rating     TINYINT      NOT NULL COMMENT '星级 1-5',
  content    VARCHAR(500) DEFAULT NULL COMMENT '文字内容',
  images     JSON         DEFAULT NULL COMMENT '图片数组',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 显示 0 隐藏(违规)',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order (order_id),
  KEY idx_staff (staff_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='服务评价（一单一评）';

-- ---------------- 余额流水 ----------------
DROP TABLE IF EXISTS balance_record;
CREATE TABLE balance_record (
  id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id       BIGINT        NOT NULL COMMENT '归属用户',
  type          VARCHAR(16)   NOT NULL COMMENT 'CONSUME/RECHARGE/REFUND/ADJUST',
  amount        DECIMAL(10,2) NOT NULL COMMENT '变动金额(正为入账 负为出账)',
  balance_after DECIMAL(10,2) NOT NULL COMMENT '变动后余额快照',
  order_id      BIGINT        DEFAULT NULL COMMENT '关联订单',
  channel       VARCHAR(32)   NOT NULL DEFAULT 'BALANCE' COMMENT '渠道(预留真实支付)',
  remark        VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_created (user_id, created_at),
  KEY idx_order (order_id),
  KEY idx_type (type)
) ENGINE=InnoDB COMMENT='余额流水';

-- ---------------- 聊天会话 ----------------
DROP TABLE IF EXISTS chat_conversation;
CREATE TABLE chat_conversation (
  id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_a_id       BIGINT       NOT NULL COMMENT '参与者A(较小ID)',
  user_b_id       BIGINT       NOT NULL COMMENT '参与者B(较大ID)',
  last_message    VARCHAR(255) DEFAULT NULL COMMENT '最新一条摘要',
  last_message_at DATETIME     DEFAULT NULL COMMENT '最新消息时间',
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pair (user_a_id, user_b_id),
  KEY idx_a_time (user_a_id, last_message_at),
  KEY idx_b_time (user_b_id, last_message_at)
) ENGINE=InnoDB COMMENT='聊天会话(参与者对唯一)';

-- ---------------- 聊天消息 ----------------
DROP TABLE IF EXISTS chat_message;
CREATE TABLE chat_message (
  id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id BIGINT   NOT NULL COMMENT '会话',
  sender_id       BIGINT   NOT NULL COMMENT '发送者',
  receiver_id     BIGINT   NOT NULL COMMENT '接收者',
  content         TEXT     NOT NULL COMMENT '内容',
  msg_type        VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT/IMAGE',
  is_read         TINYINT  NOT NULL DEFAULT 0 COMMENT '1 已读',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_conv_id (conversation_id, id),
  KEY idx_receiver_read (receiver_id, is_read)
) ENGINE=InnoDB COMMENT='聊天消息';

-- ---------------- AI 问答会话/消息 ----------------
DROP TABLE IF EXISTS ai_conversation;
CREATE TABLE ai_conversation (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT       NOT NULL COMMENT '归属用户',
  title      VARCHAR(128) DEFAULT NULL COMMENT '会话标题(取首个问题)',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, updated_at)
) ENGINE=InnoDB COMMENT='AI 宠物问答会话';

DROP TABLE IF EXISTS ai_message;
CREATE TABLE ai_message (
  id              BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  conversation_id BIGINT   NOT NULL COMMENT '会话',
  role            VARCHAR(10) NOT NULL COMMENT 'system/user/assistant',
  content         TEXT     NOT NULL COMMENT '内容',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_conv (conversation_id, id)
) ENGINE=InnoDB COMMENT='AI 问答消息';

-- ---------------- 站内通知 / 公告 / 轮播图 ----------------
DROP TABLE IF EXISTS notice;
CREATE TABLE notice (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT       NOT NULL COMMENT '接收人',
  type       VARCHAR(32)  NOT NULL COMMENT 'ORDER/REVIEW/CERT/SYSTEM',
  title      VARCHAR(128) NOT NULL COMMENT '标题',
  content    VARCHAR(500) DEFAULT NULL COMMENT '内容',
  biz_id     BIGINT       DEFAULT NULL COMMENT '业务ID(订单等)',
  is_read    TINYINT      NOT NULL DEFAULT 0 COMMENT '1 已读',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_read (user_id, is_read, created_at)
) ENGINE=InnoDB COMMENT='站内通知';

DROP TABLE IF EXISTS announcement;
CREATE TABLE announcement (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  title      VARCHAR(128) NOT NULL COMMENT '标题',
  content    TEXT         COMMENT '内容',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 发布 0 草稿/下架',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='系统公告';

DROP TABLE IF EXISTS banner;
CREATE TABLE banner (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  title      VARCHAR(128) DEFAULT NULL COMMENT '标题',
  image_url  VARCHAR(255) NOT NULL COMMENT '图片',
  link       VARCHAR(255) DEFAULT NULL COMMENT '跳转地址',
  sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sort (status, sort)
) ENGINE=InnoDB COMMENT='首页轮播图';

-- ---------------- 操作日志（审计） ----------------
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  operator_id   BIGINT       DEFAULT NULL COMMENT '操作人ID',
  operator_name VARCHAR(64)  DEFAULT NULL COMMENT '操作人',
  action        VARCHAR(64)  NOT NULL COMMENT '动作',
  target        VARCHAR(64)  DEFAULT NULL COMMENT '对象类型/ID',
  detail        VARCHAR(2000) DEFAULT NULL COMMENT '操作详情',
  result        VARCHAR(16)  NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAIL',
  ip            VARCHAR(64)  DEFAULT NULL COMMENT '来源IP',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_operator (operator_id, created_at),
  KEY idx_action (action)
) ENGINE=InnoDB COMMENT='管理端操作日志(审计)';



