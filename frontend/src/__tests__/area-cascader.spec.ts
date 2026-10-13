import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus, { ElCascader } from 'element-plus'
import {
  areaCascaderOptions,
  areaCascaderProps,
  areaCodeOf,
  findAreaPath,
  resolveAreaPath,
  toValue,
} from '@/utils/areaCascader'

/** 广东省在 provinceData 中的索引（0 基） */
const GD = 18

describe('areaCascader 工具', () => {
  it('级联选项为 省 → 市 → 区 三层', () => {
    expect(areaCascaderOptions.length).toBeGreaterThan(30)
    const guangdong = areaCascaderOptions.find((o) => o.label === '广东省')
    expect(guangdong).toBeTruthy()
    const guangzhou = guangdong?.children?.find((c) => c.label === '广州市')
    expect(guangzhou).toBeTruthy()
    expect(guangzhou?.children?.some((d) => d.label === '天河区')).toBe(true)
    // 省节点不入库，无编码
    expect(guangdong?.code).toBe('')
    expect(guangzhou?.code).not.toBe('')
  })

  it('生成的区域编码全局唯一且符合格式', () => {
    const codes: string[] = []
    for (const p of areaCascaderOptions) {
      codes.push(...(p.children ?? []).map((c) => c.code))
      for (const c of p.children ?? []) codes.push(...(c.children ?? []).map((d) => d.code))
    }
    expect(codes.length).toBeGreaterThan(2000)
    expect(new Set(codes).size).toBe(codes.length)
    expect(codes.every((code) => /^\d{6}$/.test(code))).toBe(true)
    expect(areaCodeOf(GD, 0)).toBe('190100') // 市级补 00
    expect(areaCodeOf(GD, 0, 3)).toBe('190104')
  })

  it('选到「市」解析为市级区域', () => {
    const r = resolveAreaPath([toValue('p', '广东省'), toValue('c', '广州市')])
    expect(r).toMatchObject({ level: 1, name: '广州市', code: '190100', cityCode: '190100' })
    expect(r?.district).toBeUndefined()
  })

  it('选到「区」解析为区级区域并带上所属市编码', () => {
    const r = resolveAreaPath([
      toValue('p', '广东省'),
      toValue('c', '广州市'),
      toValue('d', '天河区'),
    ])
    expect(r).toMatchObject({
      level: 2,
      name: '天河区',
      code: '190104',
      city: '广州市',
      cityCode: '190100',
      province: '广东省',
    })
  })

  it('直辖市省市同名也能解析', () => {
    const r = resolveAreaPath([toValue('p', '北京市'), toValue('c', '北京市'), toValue('d', '朝阳区')])
    expect(r).toMatchObject({ level: 2, name: '朝阳区', code: '010103', city: '北京市' })
  })

  it('只选到省或非法路径返回 null', () => {
    expect(resolveAreaPath([toValue('p', '广东省')])).toBeNull()
    expect(resolveAreaPath([])).toBeNull()
    expect(resolveAreaPath(undefined)).toBeNull()
    expect(resolveAreaPath([toValue('p', '火星省'), toValue('c', '广州市')])).toBeNull()
  })

  it('按库中记录反查级联路径（编辑回显）', () => {
    expect(findAreaPath('广州市', 1)).toEqual([toValue('p', '广东省'), toValue('c', '广州市')])
    expect(findAreaPath('天河区', 2, '广州市')).toEqual([
      toValue('p', '广东省'),
      toValue('c', '广州市'),
      toValue('d', '天河区'),
    ])
    expect(findAreaPath('朝阳区', 2, '北京市')).toEqual([
      toValue('p', '北京市'),
      toValue('c', '北京市'),
      toValue('d', '朝阳区'),
    ])
    // 不在内置地区库 → 空数组（页面此时只允许改状态）
    expect(findAreaPath('自定义片区', 1)).toEqual([])
  })
})

describe('el-cascader 交互（任意一级可点选）', () => {
  function mountCascader() {
    return mount(
      {
        components: { ElCascader },
        data: () => ({ value: [] as string[] }),
        template:
          '<el-cascader v-model="value" :options="options" :props="props" style="width: 300px" />',
        computed: {
          options: () => areaCascaderOptions,
          props: () => areaCascaderProps,
        },
      },
      { global: { plugins: [ElementPlus] } },
    )
  }

  function panelNodes(label: string): HTMLElement[] {
    return Array.from(document.body.querySelectorAll<HTMLElement>('.el-cascader-node')).filter((n) =>
      n.textContent?.includes(label),
    )
  }

  it('点省/市/区 任意一级都会选中，且面板不关闭', async () => {
    const wrapper = mountCascader()
    await wrapper.find('input').trigger('click')
    expect(document.querySelector('.el-cascader-panel')).toBeTruthy()

    const clickNode = async (label: string) => {
      const node = panelNodes(label)[0]
      expect(node, `面板中应存在节点 ${label}`).toBeTruthy()
      node!.click()
      await wrapper.vm.$nextTick()
    }

    await clickNode('广东省')
    expect((wrapper.vm as unknown as { value: string[] }).value).toEqual([toValue('p', '广东省')])
    // 选中后不自动关闭（checkStrictly 下 Element Plus 不 emit close）
    expect(document.querySelector('.el-cascader-panel')).toBeTruthy()

    await clickNode('广州市')
    expect((wrapper.vm as unknown as { value: string[] }).value).toEqual([
      toValue('p', '广东省'),
      toValue('c', '广州市'),
    ])

    await clickNode('天河区')
    expect((wrapper.vm as unknown as { value: string[] }).value).toEqual([
      toValue('p', '广东省'),
      toValue('c', '广州市'),
      toValue('d', '天河区'),
    ])
  })
})
