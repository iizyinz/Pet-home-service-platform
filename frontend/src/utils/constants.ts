/** 枚举字典（PRD 附录A）与前端展示映射 */

/** 订单状态 */
export const ORDER_STATUS: Record<string, { label: string; color: string; type: 'primary' | 'success' | 'warning' | 'info' | 'danger' }> = {
  PENDING_CONFIRM: { label: '待确认', color: '#FFB020', type: 'warning' },
  CONFIRMED: { label: '已确认', color: '#5B7FFF', type: 'primary' },
  SERVING: { label: '服务中', color: '#5B7FFF', type: 'primary' },
  PENDING_SETTLE: { label: '待确认完成', color: '#5B7FFF', type: 'primary' },
  COMPLETED: { label: '已完成', color: '#4ECB71', type: 'success' },
  CANCELLED: { label: '已取消', color: '#FF4D4F', type: 'danger' },
}

/** 订单列表 Tab（PRD 5.5/6.2） */
export const ORDER_TABS = [
  { label: '全部', value: 'ALL' },
  { label: '待确认', value: 'PENDING_CONFIRM' },
  { label: '已确认', value: 'CONFIRMED' },
  { label: '服务中', value: 'SERVING' },
  { label: '待确认完成', value: 'PENDING_SETTLE' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已取消', value: 'CANCELLED' },
]

/** 角色 */
export const ROLE_LABEL: Record<string, string> = {
  USER: '用户',
  STAFF: '服务人员',
  ADMIN: '管理员',
}

/** 认证状态 cert_status */
export const CERT_STATUS: Record<number, { label: string; type: 'primary' | 'success' | 'warning' | 'danger' }> = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已驳回', type: 'danger' },
}

/** 余额流水类型 */
export const RECORD_TYPE: Record<string, { label: string; color: string }> = {
  CONSUME: { label: '消费', color: '#FF4D4F' },
  RECHARGE: { label: '充值', color: '#4ECB71' },
  REFUND: { label: '退款', color: '#5B7FFF' },
  ADJUST: { label: '人工调整', color: '#FFB020' },
}

/** 账号状态 */
export const USER_STATUS: Record<number, { label: string; type: 'success' | 'danger' }> = {
  1: { label: '正常', type: 'success' },
  0: { label: '冻结', type: 'danger' },
}

/** 格式化金额（元，两位小数） */
export function fmtMoney(v: unknown): string {
  const n = Number(v ?? 0)
  return n.toFixed(2)
}

/** 格式化时间 yyyy-MM-dd HH:mm */
export function fmtTime(v?: string | null): string {
  if (!v) return '-'
  return v.length > 16 ? v.substring(0, 16) : v
}

/** 手机号脱敏（前端兜底） */
export function maskPhone(phone?: string | null): string {
  if (!phone || phone.length !== 11) return phone || '-'
  return `${phone.substring(0, 3)}****${phone.substring(7)}`
}
