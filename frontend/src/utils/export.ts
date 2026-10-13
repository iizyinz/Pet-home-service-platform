/** 前端 CSV 导出（后端未提供导出接口，见设计文档 §1.4） */

export interface ExportColumn {
  label: string
  key: string
}

/** 将当前筛选条件下已加载的数据导出为 CSV（带 BOM，Excel 中文不乱码） */
export function exportCsv(filename: string, columns: ExportColumn[], rows: Record<string, unknown>[]): void {
  const escape = (v: unknown): string => {
    if (v === null || v === undefined) return '""'
    const s = Array.isArray(v) ? v.join(' / ') : String(v)
    return `"${s.replace(/"/g, '""')}"`
  }
  const csv = [
    columns.map((c) => escape(c.label)).join(','),
    ...rows.map((r) => columns.map((c) => escape(r[c.key])).join(',')),
  ].join('\r\n')

  const blob = new Blob([`\uFEFF${csv}`], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${filename}-${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
