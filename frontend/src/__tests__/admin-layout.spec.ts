import { describe, it, expect, beforeAll } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory, type Router } from 'vue-router'
import ElementPlus from 'element-plus'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { adminMenus } from '@/router/menus'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

/** jsdom 缺少 ResizeObserver / matchMedia，Element Plus 菜单、弹层会用到 */
beforeAll(() => {
  if (typeof globalThis.ResizeObserver === 'undefined') {
    class ResizeObserverPolyfill {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    globalThis.ResizeObserver = ResizeObserverPolyfill as unknown as typeof ResizeObserver
  }
  if (typeof window.matchMedia !== 'function') {
    window.matchMedia = (query: string) =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }) as unknown as MediaQueryList
  }
})

const AVATAR = 'https://example.com/avatar.png'

async function mountLayout(): Promise<{ wrapper: VueWrapper; router: Router }> {
  const pinia = createPinia()
  setActivePinia(pinia)

  const auth = useAuthStore()
  // token 留空：避免测试里发起 /user/profile 真实请求
  auth.user = { id: 1, username: 'admin', nickname: '平台管理员', avatar: AVATAR, role: 'ADMIN' }

  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin', component: { template: '<div class="stub-page" />' } }],
  })
  router.push('/admin')
  await router.isReady()

  const wrapper = mount(AdminLayout, {
    global: {
      plugins: [pinia, router, ElementPlus],
      // NoticeBell 挂载即请求接口，测试中打桩
      stubs: { NoticeBell: true },
    },
  })
  await flushPromises()
  return { wrapper, router }
}

describe('AdminLayout 布局', () => {
  it('顶栏与内容区上下排布（el-container 纵向）', async () => {
    const { wrapper } = await mountLayout()
    const main = wrapper.find('.admin-layout__main')

    expect(main.exists()).toBe(true)
    // Element Plus 只有 is-vertical（flex-direction: column）时才是上下结构
    expect(main.classes()).toContain('is-vertical')

    const header = wrapper.find('.admin-layout__header')
    const content = wrapper.find('.el-main')
    expect(header.exists() && content.exists()).toBe(true)
    // 顶栏必须排在内容区之前
    expect(header.element.compareDocumentPosition(content.element)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    )
    // 顶栏撑满主区域宽度（纵向布局下 width 不受内容挤压）
    expect(header.element.nextElementSibling).toBe(content.element)
  })

  it('侧边栏折叠时分组与菜单项仍显示图标', async () => {
    const { wrapper } = await mountLayout()
    const app = useAppStore()

    // 展开态：每个分组、每个菜单项都带图标
    expect(wrapper.findAll('.side-menu__group-icon')).toHaveLength(adminMenus.length)
    const items = wrapper.findAll('.el-menu-item')
    expect(items.length).toBeGreaterThan(0)
    items.forEach((item) => expect(item.find('.el-icon').exists()).toBe(true))

    app.collapsed = true
    await flushPromises()

    expect(wrapper.find('.el-menu--collapse').exists()).toBe(true)
    // 折叠态 Element Plus 会隐藏 title 里的 span，只能靠分组图标识别分组（原本这里没有图标，故整栏空白）
    const groupIcons = wrapper.findAll('.el-sub-menu__title .side-menu__group-icon')
    expect(groupIcons).toHaveLength(adminMenus.length)
    groupIcons.forEach((icon) => expect(icon.classes()).toContain('el-icon'))
    // title 结构：图标在前、分组名文本在后（折叠态文本被 EP 隐藏，图标即唯一可见内容）
    wrapper.findAll('.el-sub-menu__title').forEach((title) => {
      const first = title.element.firstElementChild
      expect(first?.classList.contains('el-icon')).toBe(true)
      expect(title.find('span').exists()).toBe(true)
    })
  })

  it('顶栏头像与账号 avatar 一致', async () => {
    const { wrapper } = await mountLayout()
    const img = wrapper.find('.admin-layout__avatar img')
    expect(img.exists()).toBe(true)
    expect(img.attributes('src')).toBe(AVATAR)
  })

  it('账号没有头像时回退为默认图标', async () => {
    const { wrapper } = await mountLayout()
    const auth = useAuthStore()
    auth.user = { id: 1, username: 'admin', nickname: '平台管理员', role: 'ADMIN' }
    await flushPromises()

    expect(wrapper.find('.admin-layout__avatar img').exists()).toBe(false)
    expect(wrapper.find('.admin-layout__avatar svg').exists()).toBe(true)
  })
})
