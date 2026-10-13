/** 展示格式化：金额、时间、脱敏提示。后端金额为元（DECIMAL），时间 yyyy-MM-dd HH:mm:ss */

/** 金额 → 保留两位小数字符串 */
export function money(value) {
	if (value === null || value === undefined || value === '') return '0.00'
	const n = Number(value)
	if (Number.isNaN(n)) return String(value)
	return n.toFixed(2)
}

/** 去掉秒：2026-01-01 12:00:00 → 2026-01-01 12:00 */
export function timeShort(value) {
	if (!value) return ''
	return String(value).slice(0, 16)
}

/** 仅日期 */
export function dateShort(value) {
	if (!value) return ''
	return String(value).slice(0, 10)
}

/** 列表时间（今天/昨天/日期） */
export function timeLabel(value) {
	if (!value) return ''
	const s = String(value)
	const today = new Date()
	const d = new Date(s.replace(/-/g, '/'))
	const diff = today.getTime() - d.getTime()
	const day = 24 * 60 * 60 * 1000
	if (diff >= 0 && diff < day && d.getDate() === today.getDate()) {
		return s.slice(11, 16)
	}
	const y = new Date(today.getTime() - day)
	if (d.getFullYear() === y.getFullYear() && d.getMonth() === y.getMonth() && d.getDate() === y.getDate()) {
		return '昨天 ' + s.slice(11, 16)
	}
	return s.length >= 10 ? s.slice(5, 16) : s
}

/** 本地时间 → 后端格式 yyyy-MM-dd HH:mm:ss（下单用） */
export function toDateTimeString(date) {
	const d = date instanceof Date ? date : new Date(date)
	const p = (n) => (n < 10 ? '0' + n : '' + n)
	return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

/** 未读数展示：>99 显示 99+ */
export function badgeText(n) {
	if (!n) return ''
	return n > 99 ? '99+' : String(n)
}
