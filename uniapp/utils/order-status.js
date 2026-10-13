/**
 * 订单状态机映射（PRD 第四章 / 附录A）与展示辅助。
 * 后端字段：status（状态码）、statusLabel（中文名）、availableActions（可执行动作）。
 */

export const ORDER_STATUS = {
	PENDING_CONFIRM: { label: '待确认', color: '#FFB020', bg: '#FFF7E6' },
	CONFIRMED: { label: '已确认', color: '#5B7FFF', bg: '#EEF2FF' },
	SERVING: { label: '服务中', color: '#4ECB71', bg: '#EBFBF1' },
	PENDING_SETTLE: { label: '待确认完成', color: '#5B7FFF', bg: '#EEF2FF' },
	COMPLETED: { label: '已完成', color: '#4ECB71', bg: '#EBFBF1' },
	CANCELLED: { label: '已取消', color: '#999999', bg: '#F2F3F5' }
}

/** 订单中心 Tab（与后端 status 参数一致，ALL 表示全部） */
export const ORDER_TABS = [
	{ key: 'ALL', label: '全部' },
	{ key: 'PENDING_CONFIRM', label: '待确认' },
	{ key: 'CONFIRMED', label: '已确认' },
	{ key: 'SERVING', label: '服务中' },
	{ key: 'PENDING_SETTLE', label: '待确认完成' },
	{ key: 'COMPLETED', label: '已完成' },
	{ key: 'CANCELLED', label: '已取消' }
]

export function statusMeta(status) {
	return ORDER_STATUS[status] || { label: status || '未知状态', color: '#999999', bg: '#F2F3F5' }
}

/** 流水类型（balance_record.type） */
export const RECORD_TYPE = {
	CONSUME: { label: '消费', sign: '-' },
	RECHARGE: { label: '充值', sign: '+' },
	REFUND: { label: '退款', sign: '+' },
	ADJUST: { label: '人工调整', sign: '' }
}

/** 可执行动作 → 按钮文案（后端 availableActions 驱动，前端不自行推断） */
export const ACTION_TEXT = {
	cancel: '取消订单',
	confirm: '确认完成',
	review: '评价',
	chat: '私信'
}
