/**
 * 图标生成器（纯 Node，无第三方依赖）
 * 用法：node tools/gen-icons.cjs
 * 输出：
 *   static/tab/*.png        TabBar 图标 81×81（普通 #999 / 选中 #FF8A3D）
 *   static/icons/*.png      页面图标 96×96（按主题色着色）
 *   tools/icon-preview.png  全量预览（用于人工检查）
 *
 * 设计空间 100×100，4 倍超采样绘制后按面积降采样，保证边缘平滑。
 */
const fs = require('fs')
const path = require('path')
const zlib = require('zlib')

const ROOT = path.join(__dirname, '..')
const RES = 400            // 绘制分辨率
const U = RES / 100        // 1 设计单位 = 4px
const AA = 0.5             // 抗锯齿过渡宽度（设计单位）

/* ---------------- 基础绘制 ---------------- */
const clamp01 = (v) => (v < 0 ? 0 : v > 1 ? 1 : v)
const smooth = (t) => t * t * (3 - 2 * t)
/** 距离场覆盖率：d<=inV 为 1，d>=outV 为 0 */
function band(d, inV, outV) {
	if (d <= inV) return 1
	if (d >= outV) return 0
	return smooth((outV - d) / (outV - inV))
}
function put(m, xi, yi, a) {
	if (xi < 0 || yi < 0 || xi >= RES || yi >= RES || a <= 0) return
	const i = yi * RES + xi
	if (a > m[i]) m[i] = a
}
/** fn(x, y) → 覆盖率，遍历设计坐标包围盒内像素 */
function forEachPixel(m, x0, y0, x1, y1, fn) {
	const px0 = Math.max(0, Math.floor(x0 * U))
	const px1 = Math.min(RES - 1, Math.ceil(x1 * U))
	const py0 = Math.max(0, Math.floor(y0 * U))
	const py1 = Math.min(RES - 1, Math.ceil(y1 * U))
	for (let py = py0; py <= py1; py++) {
		const y = (py + 0.5) / U
		for (let px = px0; px <= px1; px++) {
			const x = (px + 0.5) / U
			put(m, px, py, fn(x, y))
		}
	}
}
function segDist(x, y, x1, y1, x2, y2) {
	const dx = x2 - x1
	const dy = y2 - y1
	const l2 = dx * dx + dy * dy
	let t = l2 ? ((x - x1) * dx + (y - y1) * dy) / l2 : 0
	t = clamp01(t)
	const ex = x1 + t * dx - x
	const ey = y1 + t * dy - y
	return Math.sqrt(ex * ex + ey * ey)
}
function strokeLine(m, x1, y1, x2, y2, w) {
	const h = w / 2
	forEachPixel(
		m,
		Math.min(x1, x2) - h - 1, Math.min(y1, y2) - h - 1,
		Math.max(x1, x2) + h + 1, Math.max(y1, y2) + h + 1,
		(x, y) => band(segDist(x, y, x1, y1, x2, y2), h, h + AA)
	)
}
function strokePath(m, pts, w, closed) {
	for (let i = 0; i + 1 < pts.length; i++) {
		strokeLine(m, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], w)
	}
	if (closed && pts.length > 2) {
		const a = pts[pts.length - 1]
		strokeLine(m, a[0], a[1], pts[0][0], pts[0][1], w)
	}
}
function arcPts(cx, cy, r, a0, a1, n) {
	const out = []
	for (let i = 0; i <= n; i++) {
		const a = ((a0 + (a1 - a0) * (i / n)) * Math.PI) / 180
		out.push([cx + r * Math.cos(a), cy + r * Math.sin(a)])
	}
	return out
}
function fillCircle(m, cx, cy, r) {
	forEachPixel(m, cx - r - AA, cy - r - AA, cx + r + AA, cy + r + AA, (x, y) =>
		band(Math.sqrt((x - cx) ** 2 + (y - cy) ** 2) - r, -AA, AA)
	)
}
function strokeCircle(m, cx, cy, r, w) {
	const h = w / 2
	forEachPixel(m, cx - r - h - 1, cy - r - h - 1, cx + r + h + 1, cy + r + h + 1, (x, y) =>
		band(Math.abs(Math.sqrt((x - cx) ** 2 + (y - cy) ** 2) - r), h, h + AA)
	)
}
function fillEllipse(m, cx, cy, rx, ry) {
	forEachPixel(m, cx - rx - AA, cy - ry - AA, cx + rx + AA, cy + ry + AA, (x, y) => {
		const nx = (x - cx) / rx
		const ny = (y - cy) / ry
		return band((Math.sqrt(nx * nx + ny * ny) - 1) * Math.min(rx, ry), -AA, AA)
	})
}
function rrectSD(x, y, x0, y0, w, h, r) {
	const qx = Math.abs(x - (x0 + w / 2)) - (w / 2 - r)
	const qy = Math.abs(y - (y0 + h / 2)) - (h / 2 - r)
	return (
		Math.sqrt(Math.max(qx, 0) ** 2 + Math.max(qy, 0) ** 2) +
		Math.min(Math.max(qx, qy), 0) -
		r
	)
}
function fillRoundRect(m, x, y, w, h, r) {
	forEachPixel(m, x - AA, y - AA, x + w + AA, y + h + AA, (px, py) =>
		band(rrectSD(px, py, x, y, w, h, r), -AA, AA)
	)
}
function strokeRoundRect(m, x, y, w, h, r, sw) {
	const hh = sw / 2
	forEachPixel(m, x - hh - 1, y - hh - 1, x + w + hh + 1, y + h + hh + 1, (px, py) =>
		band(Math.abs(rrectSD(px, py, x, y, w, h, r)), hh, hh + AA)
	)
}
function fillPoly(m, pts) {
	const xs = pts.map((p) => p[0])
	const ys = pts.map((p) => p[1])
	forEachPixel(
		m,
		Math.min(...xs) - 1, Math.min(...ys) - 1,
		Math.max(...xs) + 1, Math.max(...ys) + 1,
		(x, y) => {
			let inside = false
			for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
				const [xi, yi] = pts[i]
				const [xj, yj] = pts[j]
				if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) inside = !inside
			}
			let d = Infinity
			for (let i = 0, j = pts.length - 1; i < pts.length; j = i++) {
				d = Math.min(d, segDist(x, y, pts[i][0], pts[i][1], pts[j][0], pts[j][1]))
			}
			return band(inside ? -d : d, -AA, AA)
		}
	)
}
function newMask() {
	return new Float32Array(RES * RES)
}

/* ---------------- 图标形状（设计空间 100×100） ---------------- */
function drawHome(m) {
	strokePath(m, [[16, 52], [50, 20], [84, 52]], 7)      // 屋顶
	strokeLine(m, 26, 46, 26, 80, 7)                       // 左墙
	strokeLine(m, 74, 46, 74, 80, 7)                       // 右墙
	strokeLine(m, 16, 82, 84, 82, 7)                       // 地面
	strokePath(m, [[42, 82], [42, 64], [58, 64], [58, 82]], 6) // 门
}
function drawPaw(m) {
	fillEllipse(m, 50, 66, 17, 14)   // 掌垫
	fillEllipse(m, 26, 46, 8, 10)    // 左二趾
	fillEllipse(m, 40, 33, 8, 10.5)  // 左一趾
	fillEllipse(m, 60, 33, 8, 10.5)  // 右一趾
	fillEllipse(m, 74, 46, 8, 10)    // 右二趾
}
function drawDoc(m) {
	strokeRoundRect(m, 25, 16, 50, 68, 8, 6.5)
	strokeLine(m, 36, 36, 64, 36, 5.5)
	strokeLine(m, 36, 50, 64, 50, 5.5)
	strokeLine(m, 36, 64, 54, 64, 5.5)
}
function drawBubble(m) {
	strokeRoundRect(m, 14, 18, 72, 48, 14, 6.5)
	strokePath(m, [[32, 63], [32, 82], [53, 66]], 6.5)
	fillCircle(m, 36, 42, 4)
	fillCircle(m, 50, 42, 4)
	fillCircle(m, 64, 42, 4)
}
function drawPerson(m) {
	strokeCircle(m, 50, 32, 14, 6.5)
	strokePath(m, arcPts(50, 86, 24, 180, 360, 14), 6.5)
}
function drawSearch(m) {
	strokeCircle(m, 42, 42, 22, 7)
	strokeLine(m, 58, 58, 82, 82, 7)
}
function drawBell(m) {
	const dome = arcPts(50, 44, 17, 180, 360, 12)
	strokePath(m, [[25, 68], ...dome, [75, 68]], 6.5, true)
	fillCircle(m, 50, 77, 4.5)
}
function drawClock(m) {
	strokeCircle(m, 50, 50, 30, 6.5)
	strokeLine(m, 50, 50, 50, 29, 6)
	strokeLine(m, 50, 50, 65, 58, 6)
}
function drawCheckCircle(m) {
	strokeCircle(m, 50, 50, 30, 6.5)
	strokePath(m, [[34, 51], [45, 63], [67, 39]], 6.5)
}
function drawPlay(m) {
	strokeCircle(m, 50, 50, 30, 6.5)
	fillPoly(m, [[43, 36], [43, 64], [66, 50]])
}
function drawHourglass(m) {
	strokePath(m, [[32, 20], [68, 20], [44, 50], [68, 80], [32, 80], [56, 50]], 6, true)
}
function drawCheckDouble(m) {
	strokePath(m, [[20, 52], [33, 65], [52, 40]], 6)
	strokePath(m, [[44, 56], [57, 69], [80, 38]], 6)
}
function drawPin(m) {
	const dome = arcPts(50, 40, 18, 180, 360, 12)
	strokePath(m, [...dome, [50, 84]], 6, true)
	fillCircle(m, 50, 40, 7)
}
function drawHeart(m) {
	fillCircle(m, 36, 38, 15)
	fillCircle(m, 64, 38, 15)
	fillPoly(m, [[22, 42], [50, 84], [78, 42]])
}
function drawHeartOutline(m) {
	const pts = arcPts(36, 38, 15, 90, 270, 12)          // 左瓣：底→左→顶
	pts.push([40, 25], [45, 29], [50, 31], [55, 29], [60, 25]) // 顶部凹陷（平滑心尖）
	pts.push(...arcPts(64, 38, 15, 270, 450, 12))          // 右瓣：顶→右→底
	pts.push([50, 84])                                     // 底部尖角
	strokePath(m, pts, 6, true)
}
function drawStar(m) {
	const pts = []
	const cx = 50
	const cy = 53
	for (let k = 0; k < 10; k++) {
		const r = k % 2 === 0 ? 30 : 13
		const a = ((-90 + k * 36) * Math.PI) / 180
		pts.push([cx + r * Math.cos(a), cy + r * Math.sin(a)])
	}
	fillPoly(m, pts)
}
function drawWallet(m) {
	strokeRoundRect(m, 16, 30, 68, 42, 9, 6)
	strokeLine(m, 16, 45, 84, 45, 5.5)
	fillCircle(m, 66, 58, 4.5)
}
function drawSpark(m) {
	fillPoly(m, [
		[50, 16], [57.5, 42.5], [84, 50], [57.5, 57.5],
		[50, 84], [42.5, 57.5], [16, 50], [42.5, 42.5]
	])
	fillPoly(m, [
		[76, 14], [78.5, 19.5], [84, 22], [78.5, 24.5],
		[76, 30], [73.5, 24.5], [68, 22], [73.5, 19.5]
	])
}
function drawPeople(m) {
	strokeCircle(m, 65, 41, 11, 6)
	strokePath(m, arcPts(65, 82, 17, 180, 360, 10), 6)
	fillCircle(m, 40, 36, 13)
	strokePath(m, arcPts(40, 86, 21, 180, 360, 12), 6.5)
}
function drawImage(m) {
	strokeRoundRect(m, 16, 24, 68, 52, 9, 6)
	strokePath(m, [[26, 66], [42, 46], [54, 60], [62, 52], [76, 66]], 5.5)
	fillCircle(m, 64, 38, 5)
}
function drawNewChat(m) {
	strokeRoundRect(m, 14, 16, 72, 50, 14, 6.5)
	strokePath(m, [[32, 63], [32, 82], [53, 66]], 6.5)
	strokeLine(m, 50, 30, 50, 54, 6.5)
	strokeLine(m, 38, 42, 62, 42, 6.5)
}
function drawInbox(m) {
	strokePath(
		m,
		[[18, 30], [36, 30], [44, 48], [56, 48], [64, 30], [82, 30], [82, 74], [18, 74]],
		6,
		true
	)
}
function drawWarn(m) {
	strokePath(m, [[50, 18], [86, 78], [14, 78]], 6.5, true)
	strokeLine(m, 50, 38, 50, 60, 6.5)
	fillCircle(m, 50, 71, 3.8)
}

/* ---------------- 降采样 / 上色 / PNG 编码 ---------------- */
function resample(mask, size) {
	const out = new Float32Array(size * size)
	const s = RES / size
	for (let y = 0; y < size; y++) {
		const y0 = Math.floor(y * s)
		const y1 = Math.min(RES, Math.ceil((y + 1) * s))
		for (let x = 0; x < size; x++) {
			const x0 = Math.floor(x * s)
			const x1 = Math.min(RES, Math.ceil((x + 1) * s))
			let sum = 0
			let n = 0
			for (let yy = y0; yy < y1; yy++) {
				for (let xx = x0; xx < x1; xx++) {
					sum += mask[yy * RES + xx]
					n++
				}
			}
			out[y * size + x] = n ? sum / n : 0
		}
	}
	return out
}
function hexRgb(hex) {
	const h = hex.replace('#', '')
	return [
		parseInt(h.slice(0, 2), 16),
		parseInt(h.slice(2, 4), 16),
		parseInt(h.slice(4, 6), 16)
	]
}
function colorize(mask, size, color) {
	const [r, g, b] = hexRgb(color)
	const buf = Buffer.alloc(size * size * 4)
	for (let i = 0; i < size * size; i++) {
		buf[i * 4] = r
		buf[i * 4 + 1] = g
		buf[i * 4 + 2] = b
		buf[i * 4 + 3] = Math.round(Math.min(1, mask[i]) * 255)
	}
	return buf
}
const CRC_TABLE = (() => {
	const t = new Uint32Array(256)
	for (let n = 0; n < 256; n++) {
		let c = n
		for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
		t[n] = c >>> 0
	}
	return t
})()
function crc32(buf) {
	let c = 0xffffffff
	for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8)
	return (c ^ 0xffffffff) >>> 0
}
function chunk(type, data) {
	const len = Buffer.alloc(4)
	len.writeUInt32BE(data.length, 0)
	const t = Buffer.from(type, 'ascii')
	const crc = Buffer.alloc(4)
	crc.writeUInt32BE(crc32(Buffer.concat([t, data])), 0)
	return Buffer.concat([len, t, data, crc])
}
function encodePNG(size, rgba) {
	const stride = size * 4
	const raw = Buffer.alloc((stride + 1) * size)
	for (let y = 0; y < size; y++) {
		raw[y * (stride + 1)] = 0
		rgba.copy(raw, y * (stride + 1) + 1, y * stride, (y + 1) * stride)
	}
	const ihdr = Buffer.alloc(13)
	ihdr.writeUInt32BE(size, 0)
	ihdr.writeUInt32BE(size, 4)
	ihdr[8] = 8
	ihdr[9] = 6
	return Buffer.concat([
		Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
		chunk('IHDR', ihdr),
		chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
		chunk('IEND', Buffer.alloc(0))
	])
}

/* ---------------- 清单 ---------------- */
const C = {
	primary: '#FF8A3D',
	info: '#5B7FFF',
	success: '#4ECB71',
	warning: '#FFB020',
	error: '#FF4D4F',
	gray: '#999999',
	gray2: '#666666'
}
const TABS = [
	['home', drawHome],
	['service', drawPaw],
	['order', drawDoc],
	['message', drawBubble],
	['mine', drawPerson]
]
const ICONS = [
	['search', drawSearch, C.gray],
	['bell', drawBell, C.gray2],
	['ai', drawSpark, C.info],
	['image', drawImage, C.gray2],
	['new-chat', drawNewChat, C.primary],
	['people', drawPeople, C.primary],
	['order-pending', drawClock, C.warning],
	['order-confirmed', drawCheckCircle, C.primary],
	['order-serving', drawPlay, C.primary],
	['order-settling', drawHourglass, C.primary],
	['order-done', drawCheckDouble, C.primary],
	['menu-pets', drawPaw, C.primary],
	['menu-address', drawPin, C.info],
	['menu-favorites', drawHeart, C.error],
	['menu-reviews', drawStar, C.warning],
	['menu-balance', drawWallet, C.success],
	['menu-ai', drawSpark, C.info],
	['menu-profile', drawPerson, C.primary],
	['state-empty', drawInbox, C.gray],
	['state-error', drawWarn, C.error],
	['chat', drawBubble, C.primary],
	['favorite', drawHeartOutline, C.gray],
	['favorite-on', drawHeart, C.error]
]

function saveIcon(dir, name, draw, size, color) {
	const m = newMask()
	draw(m)
	const file = path.join(dir, name + '.png')
	fs.writeFileSync(file, encodePNG(size, colorize(resample(m, size), size, color)))
	return file
}

/* ---------------- 预览拼图 ---------------- */
function buildPreview(rows, cellSize, cols) {
	const pad = 8
	const W = cols * (cellSize + pad) + pad
	const H = rows.length * (cellSize + pad) + pad
	const buf = Buffer.alloc(W * H * 4)
	for (let i = 0; i < W * H; i++) {
		buf[i * 4] = 245
		buf[i * 4 + 1] = 246
		buf[i * 4 + 2] = 248
		buf[i * 4 + 3] = 255
	}
	rows.forEach((row, ry) => {
		row.forEach((cell, rx) => {
			if (!cell) return
			const ox = pad + rx * (cellSize + pad)
			const oy = pad + ry * (cellSize + pad)
			// 白色卡片底
			for (let y = 0; y < cellSize; y++) {
				for (let x = 0; x < cellSize; x++) {
					const i = ((oy + y) * W + (ox + x)) * 4
					buf[i] = 255
					buf[i + 1] = 255
					buf[i + 2] = 255
					buf[i + 3] = 255
				}
			}
			const mask = resample(cell.mask, cellSize)
			const [r, g, b] = hexRgb(cell.color)
			for (let y = 0; y < cellSize; y++) {
				for (let x = 0; x < cellSize; x++) {
					const a = mask[y * cellSize + x]
					if (a <= 0) continue
					const i = ((oy + y) * W + (ox + x)) * 4
					buf[i] = Math.round(r * a + buf[i] * (1 - a))
					buf[i + 1] = Math.round(g * a + buf[i + 1] * (1 - a))
					buf[i + 2] = Math.round(b * a + buf[i + 2] * (1 - a))
				}
			}
		})
	})
	fs.writeFileSync(path.join(__dirname, 'icon-preview.png'), encodePNG(W, buf))
}

/* ---------------- 主流程 ---------------- */
function main() {
	const tabDir = path.join(ROOT, 'static', 'tab')
	const icoDir = path.join(ROOT, 'static', 'icons')
	fs.mkdirSync(tabDir, { recursive: true })
	fs.mkdirSync(icoDir, { recursive: true })

	const previewRows = []
	const rowTab = []
	const rowTabActive = []
	for (const [name, draw] of TABS) {
		saveIcon(tabDir, name, draw, 81, '#999999')
		saveIcon(tabDir, name + '-active', draw, 81, C.primary)
		const m = newMask()
		draw(m)
		rowTab.push({ mask: m, color: '#999999' })
		const m2 = newMask()
		draw(m2)
		rowTabActive.push({ mask: m2, color: C.primary })
	}
	previewRows.push(rowTab, rowTabActive)

	let current = []
	for (const [name, draw, color] of ICONS) {
		saveIcon(icoDir, name, draw, 96, color)
		const m = newMask()
		draw(m)
		current.push({ mask: m, color })
		if (current.length === 6) {
			previewRows.push(current)
			current = []
		}
	}
	if (current.length) previewRows.push(current)

	buildPreview(previewRows, 96, 6)

	const tabCount = fs.readdirSync(tabDir).length
	const iconCount = fs.readdirSync(icoDir).length
	console.log('生成完成: static/tab ' + tabCount + ' 个, static/icons ' + iconCount + ' 个')
	console.log('预览图: tools/icon-preview.png（行1-2 Tab 普通/选中，行3-6 页面图标）')
}
main()
