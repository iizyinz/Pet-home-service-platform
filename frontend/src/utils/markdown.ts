import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * AI 回答的 Markdown 渲染。
 * - marked：GFM + 换行按 <br> 处理（回答里常用单行换行）
 * - DOMPurify：模型输出属于不可信内容，必须消毒后再 v-html，防 XSS
 */
marked.setOptions({ gfm: true, breaks: true })

/** 渲染为安全 HTML；输入为空返回空串 */
export function renderMarkdown(src?: string | null): string {
  if (!src) return ''
  const html = marked.parse(src, { async: false })
  return DOMPurify.sanitize(html, { USE_PROFILES: { html: true } })
}
