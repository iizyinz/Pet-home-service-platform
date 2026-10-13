/**
 * 请求封装：统一 { code, message, data } 解析、token 注入、401/403/超时处理。
 * 后端约定见 backend/README.md：
 *  - code=0 成功；业务错误为非 0 code（HTTP 仍可能为 200）
 *  - 401 未登录、403 越权、400 参数校验、超时/断网走 fail 回调
 */
const BASE_URL = 'http://localhost:8080'
const TIMEOUT = 10000

const WHITE_LIST = ['/api/auth/captcha', '/api/auth/login', '/api/auth/register']

function getToken() {
	return uni.getStorageSync('token') || ''
}

/** 401 统一处理：清理登录态并跳转登录页（避免重复跳转） */
function handleUnauthorized() {
	uni.removeStorageSync('token')
	uni.removeStorageSync('loginUser')
	const pages = getCurrentPages()
	const cur = pages[pages.length - 1]
	const route = cur ? '/' + cur.route : ''
	if (route === '/pages/login/login') return
	uni.navigateTo({
		url: '/pages/login/login?redirect=' + encodeURIComponent(route)
	})
}

/**
 * 发起请求
 * @param {Object} options { url, method, data, header, loading, silent }
 *  - loading: 显示 loading 提交中提示（默认 false）
 *  - silent: 失败时不自动 toast（默认 false，由页面自行提示）
 * @returns {Promise<any>} resolve 业务 data
 */
export function request(options) {
	const {
		url,
		method = 'GET',
		data = {},
		header = {},
		loading = false,
		silent = false
	} = options

	const finalHeader = { 'Content-Type': 'application/json', ...header }
	if (getToken() && !WHITE_LIST.includes(url)) {
		finalHeader.Authorization = 'Bearer ' + getToken()
	}

	if (loading) uni.showLoading({ title: '加载中', mask: true })

	return new Promise((resolve, reject) => {
		uni.request({
			url: BASE_URL + url,
			method,
			data,
			header: finalHeader,
			timeout: TIMEOUT,
			success: (res) => {
				if (loading) uni.hideLoading()
				const body = res.data
				// 网关/代理异常，未返回标准结构
				if (!body || typeof body.code === 'undefined') {
					if (!silent) uni.showToast({ title: '服务响应异常，请稍后重试', icon: 'none' })
					reject({ code: -1, message: '服务响应异常' })
					return
				}
				if (body.code === 0) {
					resolve(body.data)
					return
				}
				// 业务失败
				if (body.code === 401) {
					handleUnauthorized()
					if (!silent) uni.showToast({ title: body.message || '登录已过期，请重新登录', icon: 'none' })
					reject(body)
					return
				}
				if (body.code === 403) {
					if (!silent) uni.showToast({ title: body.message || '暂无权限执行此操作', icon: 'none' })
					reject(body)
					return
				}
				if (!silent) uni.showToast({ title: body.message || '操作失败', icon: 'none' })
				reject(body)
			},
			fail: (err) => {
				if (loading) uni.hideLoading()
				const msg = /timeout/i.test(err.errMsg || '')
					? '请求超时，请检查网络后重试'
					: '网络连接失败，请检查网络后重试'
				if (!silent) uni.showToast({ title: msg, icon: 'none' })
				reject({ code: -2, message: msg, errMsg: err.errMsg })
			}
		})
	})
}

export const get = (url, data, opts = {}) => request({ url, method: 'GET', data, ...opts })
export const post = (url, data, opts = {}) => request({ url, method: 'POST', data, ...opts })
export const put = (url, data, opts = {}) => request({ url, method: 'PUT', data, ...opts })
export const del = (url, data, opts = {}) => request({ url, method: 'DELETE', data, ...opts })

/** 上传图片：POST /api/files/upload，字段 file，返回 { url } */
export function uploadFile(filePath) {
	return new Promise((resolve, reject) => {
		uni.uploadFile({
			url: BASE_URL + '/api/files/upload',
			filePath,
			name: 'file',
			header: { Authorization: 'Bearer ' + getToken() },
			success: (res) => {
				let body = null
				try {
					body = JSON.parse(res.data)
				} catch (e) {
					body = null
				}
				if (body && body.code === 0) {
					resolve(body.data.url)
				} else {
					const msg = (body && body.message) || '上传失败'
					uni.showToast({ title: msg, icon: 'none' })
					reject(body || { code: -1, message: msg })
				}
			},
			fail: (err) => {
				uni.showToast({ title: '上传失败，请检查网络', icon: 'none' })
				reject({ code: -2, message: '上传失败', errMsg: err.errMsg })
			}
		})
	})
}

export const BASE_URL_CONFIG = BASE_URL
