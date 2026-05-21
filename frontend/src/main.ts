import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import './styles/main.css'
import App from './App.vue'
import router from './router'
import { applyThemePreference } from '@/utils/themes'

applyThemePreference()

createApp(App)
  .use(createPinia())
  .use(router)
  .use(Vant)
  .mount('#app')
