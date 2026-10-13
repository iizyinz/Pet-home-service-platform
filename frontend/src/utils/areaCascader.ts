import type { CascaderOption, CascaderProps } from 'element-plus'
import { provinceData } from '@/utils/areaData'

/**
 * 服务区域级联选择（省 → 市 → 区），数据源为内置的 utils/areaData.ts。
 *
 * 与 service_area 表的映射关系（表只有两级：1 市 / 2 区）：
 *  - 选到「市」→ level=1、parentId=0、name=市名
 *  - 选到「区」→ level=2、name=区名、parentId=所属市（不存在时由页面自动创建）
 *  - 「省」仅作导航分组，不入库
 *
 * code 由省市县在数据集中的位置生成（2 位省 + 2 位市 + 2 位区，市为 00），
 * 保证全局唯一且符合 uk_code 唯一索引，无需手动填写。
 */

export interface AreaCascaderOption extends CascaderOption {
  value: string
  label: string
  /** 区域编码（省节点为空） */
  code: string
  children?: AreaCascaderOption[]
}

/** 解析后的区域信息，用于构造提交 payload */
export interface ResolvedArea {
  province: string
  city: string
  /** 仅区级 */
  district?: string
  level: 1 | 2
  name: string
  code: string
  /** 所属市的编码（区级用于查找/创建父级） */
  cityCode: string
}

const pad2 = (n: number) => String(n).padStart(2, '0')

/**
 * 节点 value：带 p/c/d 层级前缀。
 * 直接用名称会导致跨层级撞名（如北京既是省又是市），带前缀后同一路径内唯一。
 */
export const toValue = (kind: 'p' | 'c' | 'd', name: string) => `${kind}|${name}`

/** 还原为名称 */
export const toLabel = (value: string) => (value.includes('|') ? value.slice(value.indexOf('|') + 1) : value)

/** 省索引 + 市索引 (+ 区索引) → 6 位编码，如 190106 */
export function areaCodeOf(provinceIndex: number, cityIndex: number, districtIndex?: number): string {
  const prefix = `${pad2(provinceIndex + 1)}${pad2(cityIndex + 1)}`
  return districtIndex === undefined ? `${prefix}00` : `${prefix}${pad2(districtIndex + 1)}`
}

/** el-cascader 的 options：省（导航） → 市（可选） → 区（可选） */
export const areaCascaderOptions: AreaCascaderOption[] = provinceData.map((province, pi) => ({
  value: toValue('p', province.name),
  label: province.name,
  code: '',
  children: province.children.map((city, ci) => ({
    value: toValue('c', city.name),
    label: city.name,
    code: areaCodeOf(pi, ci),
    children: city.children.map((district, di) => ({
      value: toValue('d', district.name),
      label: district.name,
      code: areaCodeOf(pi, ci, di),
    })),
  })),
}))

/**
 * 级联面板配置：
 * checkStrictly —— 任意一级都可选（选到市=市级区域，选到区=区级区域）
 * checkOnClickNode —— 点节点文字即选中（默认只有点叶子才选中）
 * Element Plus 在 checkStrictly 下不会自动关闭面板，可连续点到区级
 */
export const areaCascaderProps: CascaderProps = {
  emitPath: true,
  checkStrictly: true,
  checkOnClickNode: true,
  expandTrigger: 'hover',
}

/**
 * 把级联路径解析成可提交的区域信息。
 * @param path 形如 ['p|广东省','c|广州市','d|天河区']
 */
export function resolveAreaPath(path?: string[] | null): ResolvedArea | null {
  if (!Array.isArray(path) || path.length < 2) return null
  const provinceIndex = provinceData.findIndex((p) => toValue('p', p.name) === path[0])
  if (provinceIndex < 0) return null
  const province = provinceData[provinceIndex]
  if (!province) return null

  const cityIndex = province.children.findIndex((c) => toValue('c', c.name) === path[1])
  if (cityIndex < 0) return null
  const city = province.children[cityIndex]
  if (!city) return null
  const cityCode = areaCodeOf(provinceIndex, cityIndex)

  if (path.length === 2) {
    return { province: province.name, city: city.name, level: 1, name: city.name, code: cityCode, cityCode }
  }

  const districtIndex = city.children.findIndex((d) => toValue('d', d.name) === path[2])
  if (districtIndex < 0) return null
  const district = city.children[districtIndex]
  if (!district) return null
  return {
    province: province.name,
    city: city.name,
    district: district.name,
    level: 2,
    name: district.name,
    code: areaCodeOf(provinceIndex, cityIndex, districtIndex),
    cityCode,
  }
}

/**
 * 反查：按库中已有记录回填级联路径（编辑态回显）。
 * @param name 记录 name（市级=市名，区级=区名）
 * @param level 1 市 / 2 区
 * @param parentName 区级记录的上级市名
 * @returns 命中返回路径，不在内置地区库中返回 []
 */
export function findAreaPath(name: string, level?: number, parentName?: string): string[] {
  if (!name) return []
  for (const province of provinceData) {
    for (const city of province.children) {
      if (level === 1) {
        if (city.name === name) return [toValue('p', province.name), toValue('c', city.name)]
      } else {
        for (const district of city.children) {
          if (district.name === name && (!parentName || city.name === parentName)) {
            return [toValue('p', province.name), toValue('c', city.name), toValue('d', district.name)]
          }
        }
      }
    }
  }
  return []
}
