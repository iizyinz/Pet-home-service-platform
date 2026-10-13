/**
 * 轻量 Markdown → HTML（供 rich-text 渲染 AI 回答）
 *
 * 支持：标题（h1-h6）、段落与换行、粗体/斜体/删除线、行内代码、
 *      围栏代码块、无序/有序列表、引用（可嵌套）、分割线、表格、
 *      链接与图片（降级为高亮文本，见下方说明）。
 *
 * 输出为「带内联样式」的 HTML 字符串，兼容 H5 / 小程序 / App 的 <rich-text>：
 *  - rich-text 不支持节点点击事件 → 链接渲染为高亮文本，图片渲染为「图片：alt」，
 *    避免小程序域名白名单导致裂图；
 *  - 尺寸使用 px（在各端与 28rpx 基准字号等效），不使用 rpx 以免 H5 端失效。
 */

const S = {
	p: 'margin:0 0 10px;line-height:1.7;font-size:14px;word-break:break-all;',
	h1: 'margin:16px 0 8px;font-size:18px;line-height:1.4;font-weight:700;',
	h2: 'margin:14px 0 8px;font-size:16px;line-height:1.4;font-weight:700;',
	h3: 'margin:12px 0 6px;font-size:15px;line-height:1.4;font-weight:700;',
	h4: 'margin:12px 0 6px;font-size:14px;line-height:1.4;font-weight:700;',
	ul: 'margin:0 0 10px;padding-left:20px;line-height:1.7;font-size:14px;',
	li: 'margin:3px 0;word-break:break-all;',
	pre:
		'margin:10px 0;padding:12px;background:#F7F8FA;border:1px solid #E5E6EB;' +
		'border-radius:10px;font-family:Menlo,Consolas,"Courier New",monospace;' +
		'font-size:13px;line-height:1.6;color:#2F3540;white-space:pre-wrap;word-break:break-all;',
	code:
		'background:#FFF1E6;color:#E2603C;padding:1px 6px;border-radius:5px;' +
		'font-family:Menlo,Consolas,"Courier New",monospace;font-size:13px;',
	quote:
		'margin:10px 0;padding:8px 14px;border-left:3px solid #FF8A3D;background:#FFF7F0;' +
		'border-radius:0 8px 8px 0;color:#6B7280;font-size:14px;',
	hr: 'border:none;border-top:1px solid #E5E6EB;margin:14px 0;height:0;',
	link: 'color:#5B7FFF;',
	strong: 'font-weight:700;',
	em: 'font-style:italic;',
	del: 'color:#999999;',
	table: 'border-collapse:collapse;margin:10px 0;width:100%;font-size:13px;',
	th: 'border:1px solid #E5E6EB;background:#F7F8FA;padding:6px 8px;font-weight:600;text-align:left;',
	td: 'border:1px solid #E5E6EB;padding:6px 8px;text-align:left;word-break:break-all;'
}

/** HTML 实体转义（必须在行内解析前执行） */
function esc(s) {
	return String(s)
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;')
		.replace(/'/g, '&#39;')
}

/** 行内语法：代码 → 图片/链接 → 粗体/斜体/删除线 */
function inline(raw) {
	let t = esc(raw)
	// 1) 行内代码先行抽取，保护其中的 markdown 符号
	const codes = []
	t = t.replace(/`([^`]+)`/g, (m, c) => {
		codes.push(c)
		return '\u0000' + (codes.length - 1) + '\u0000'
	})
	// 2) 图片：降级为高亮文本（rich-text 无法点击，且小程序有域名白名单）
	t = t.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (m, alt, src) => {
		const label = alt || src || '图片'
		return '<span style="' + S.link + '">图片：' + label + '</span>'
	})
	// 3) 链接：降级为高亮文本
	t = t.replace(/\[([^\]]+)\]\(([^)]+)\)/g, (m, txt) => '<span style="' + S.link + '">' + txt + '</span>')
	// 4) 强调
	t = t.replace(/\*\*([^*]+)\*\*/g, '<strong style="' + S.strong + '">$1</strong>')
	t = t.replace(/__([^_]+)__/g, '<strong style="' + S.strong + '">$1</strong>')
	t = t.replace(/\*([^*\n]+)\*/g, '<em style="' + S.em + '">$1</em>')
	t = t.replace(/(^|[^*\w])_([^_\n]+)_(?!\w)/g, '$1<em style="' + S.em + '">$2</em>')
	t = t.replace(/~~([^~]+)~~/g, '<del style="' + S.del + '">$1</del>')
	// 5) 还原行内代码
	t = t.replace(/\u0000(\d+)\u0000/g, (m, i) => '<code style="' + S.code + '">' + codes[Number(i)] + '</code>')
	return t
}

/** 列表行文本（去掉标记） */
const UNORDERED = /^\s*[-*+]\s+/
const ORDERED = /^\s*\d+[.)]\s+/
const HEADING = /^(#{1,6})\s+(.*)$/
const HR = /^\s{0,3}([-*_])\s*(\1\s*){2,}$/
const QUOTE = /^\s*>/
const FENCE_LEFT = /^\s*```/

/** 表格行切分 */
function splitRow(row) {
	let r = row.trim()
	if (r.charAt(0) === '|') r = r.slice(1)
	if (r.charAt(r.length - 1) === '|') r = r.slice(0, -1)
	return r.split('|').map((c) => c.trim())
}

/**
 * Markdown → HTML
 * @param {string} md
 * @returns {string} 带内联样式的 HTML
 */
export function mdToHtml(md) {
	if (md === null || md === undefined) return ''
	let text = String(md).replace(/\r\n?/g, '\n')

	// 1) 抽取围栏代码块为占位符（内容不参与后续解析）
	const blocks = []
	text = text.replace(/```[ \t]*([a-zA-Z0-9_+-]*)[ \t]*\n([\s\S]*?)```/g, (m, lang, code) => {
		blocks.push('<pre style="' + S.pre + '">' + esc(code.replace(/\n$/, '')) + '</pre>')
		return '\n\u0001' + (blocks.length - 1) + '\u0001\n'
	})

	const lines = text.split('\n')
	const out = []
	let para = []
	const flush = () => {
		if (!para.length) return
		out.push('<p style="' + S.p + '">' + para.map(inline).join('<br/>') + '</p>')
		para = []
	}

	let i = 0
	while (i < lines.length) {
		const line = lines[i]

		// 代码块占位
		const bm = line.match(/^\u0001(\d+)\u0001$/)
		if (bm) {
			flush()
			out.push(blocks[Number(bm[1])] || '')
			i++
			continue
		}
		// 流式输出中残留的未闭合围栏行：跳过，避免显示 ```js
		if (FENCE_LEFT.test(line)) {
			i++
			continue
		}
		// 空行
		if (!line.trim()) {
			flush()
			i++
			continue
		}
		// 标题
		const hm = line.match(HEADING)
		if (hm) {
			flush()
			const lv = hm[1].length
			const st = lv <= 1 ? S.h1 : lv === 2 ? S.h2 : lv === 3 ? S.h3 : S.h4
			out.push('<h' + lv + ' style="' + st + '">' + inline(hm[2]) + '</h' + lv + '>')
			i++
			continue
		}
		// 分割线
		if (HR.test(line)) {
			flush()
			out.push('<div style="' + S.hr + '"></div>')
			i++
			continue
		}
		// 引用（可嵌套：递归解析）
		if (QUOTE.test(line)) {
			flush()
			const buf = []
			while (i < lines.length && QUOTE.test(lines[i])) {
				buf.push(lines[i].replace(/^\s*>\s?/, ''))
				i++
			}
			out.push('<blockquote style="' + S.quote + '">' + mdToHtml(buf.join('\n')) + '</blockquote>')
			continue
		}
		// 无序列表
		if (UNORDERED.test(line)) {
			flush()
			const items = []
			while (i < lines.length && UNORDERED.test(lines[i])) {
				items.push(lines[i].replace(UNORDERED, ''))
				i++
			}
			out.push(
				'<ul style="' + S.ul + '">' +
					items.map((t) => '<li style="' + S.li + '">' + inline(t) + '</li>').join('') +
					'</ul>'
			)
			continue
		}
		// 有序列表
		if (ORDERED.test(line)) {
			flush()
			const items = []
			while (i < lines.length && ORDERED.test(lines[i])) {
				items.push(lines[i].replace(ORDERED, ''))
				i++
			}
			out.push(
				'<ol style="' + S.ul + '">' +
					items.map((t) => '<li style="' + S.li + '">' + inline(t) + '</li>').join('') +
					'</ol>'
			)
			continue
		}
		// 表格：当前行含 | 且下一行是分隔行
		if (
			line.indexOf('|') > -1 &&
			i + 1 < lines.length &&
			lines[i + 1].indexOf('-') > -1 &&
			/^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1])
		) {
			flush()
			const head = splitRow(line)
			i += 2
			const rows = []
			while (i < lines.length && lines[i].trim() && lines[i].indexOf('|') > -1) {
				rows.push(splitRow(lines[i]))
				i++
			}
			const thead =
				'<tr>' + head.map((c) => '<th style="' + S.th + '">' + inline(c) + '</th>').join('') + '</tr>'
			const tbody = rows
				.map(
					(r) =>
						'<tr>' +
						head.map((_, ci) => '<td style="' + S.td + '">' + inline(r[ci] || '') + '</td>').join('') +
						'</tr>'
				)
				.join('')
			out.push('<table style="' + S.table + '"><thead>' + thead + '</thead><tbody>' + tbody + '</tbody></table>')
			continue
		}
		// 普通段落
		para.push(line)
		i++
	}
	flush()
	return out.join('')
}

export default { mdToHtml }
