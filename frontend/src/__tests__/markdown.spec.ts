import { describe, it, expect } from 'vitest'
import { renderMarkdown } from '@/utils/markdown'

describe('renderMarkdown', () => {
  it('空输入返回空串', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
  })

  it('渲染标题、加粗与有序/无序列表', () => {
    const html = renderMarkdown('# 标题\n\n**加粗**\n\n- 甲\n- 乙')
    expect(html).toContain('<h1>标题</h1>')
    expect(html).toContain('<strong>加粗</strong>')
    expect(html).toContain('<li>甲</li>')
    expect(html).toContain('<li>乙</li>')
  })

  it('GFM 换行转为 <br>（回答常用单行换行）', () => {
    expect(renderMarkdown('第一行\n第二行')).toContain('<br>')
  })

  it('渲染引用块与表格', () => {
    const html = renderMarkdown('> 注意事项\n\n| a | b |\n| - | - |\n| 1 | 2 |')
    expect(html).toContain('<blockquote>')
    expect(html).toContain('<table>')
    expect(html).toContain('<td>1</td>')
  })

  it('消毒模型输出：脚本与事件属性被剔除', () => {
    const html = renderMarkdown('<script>alert(1)</script>\n\n<img src=x onerror="alert(2)">')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('onerror')
  })

  it('消毒危险链接协议', () => {
    const html = renderMarkdown('[点我](javascript:alert(3))')
    expect(html).not.toContain('javascript:')
  })

  it('行内代码保留', () => {
    expect(renderMarkdown('`npm run dev`')).toContain('<code>npm run dev</code>')
  })
})
