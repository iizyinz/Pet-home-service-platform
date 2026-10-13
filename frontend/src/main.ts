import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import '@/styles/tokens.css'
import '@/styles/element-theme.css'
import '@/styles/global.css'
import App from './App.vue'
import router from './router'
import { registerChartTheme } from '@/utils/chart-theme'

const app = createApp(App)

// ECharts 主题：色彩方案 → 'pet' 主题（三端主色由 html.theme-* CSS 变量驱动）
registerChartTheme()

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn, size: 'default' })

app.mount('#app')
