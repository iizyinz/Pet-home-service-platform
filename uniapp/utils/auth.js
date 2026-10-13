/** 登录态存取：token 与用户信息本地缓存 */

export function setAuth(token, user, expiresAt) {
	uni.setStorageSync('token', token)
	uni.setStorageSync('loginUser', user || {})
	if (expiresAt) uni.setStorageSync('tokenExpiresAt', expiresAt)
}

export function getToken() {
	return uni.getStorageSync('token') || ''
}

export function getLoginUser() {
	return uni.getStorageSync('loginUser') || null
}

export function clearAuth() {
	uni.removeStorageSync('token')
	uni.removeStorageSync('loginUser')
	uni.removeStorageSync('tokenExpiresAt')
}

export function isLoggedIn() {
	return !!getToken()
}

const TAB_ROUTES = [
	'/pages/index/index',
	'/pages/service/list',
	'/pages/order/list',
	'/pages/message/index',
	'/pages/user/center'
]

export function isTabRoute(route) {
	return TAB_ROUTES.includes(route)
}

/** 进入需要登录的页面前校验；未登录跳登录页并记录回跳地址 */
export function ensureLogin(redirectUrl) {
	if (isLoggedIn()) return true
	const redirect = redirectUrl || getCurrentRedirect()
	// Tab 页不回跳参数，登录成功后统一 switchTab 回该 Tab
	uni.navigateTo({
		url: '/pages/login/login' + (redirect ? '?redirect=' + encodeURIComponent(redirect) : '')
	})
	return false
}

function getCurrentRedirect() {
	const pages = getCurrentPages()
	const cur = pages[pages.length - 1]
	return cur ? '/' + cur.route : ''
}
