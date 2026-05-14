import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Vant from 'vant'
import 'vant/lib/index.css'
import './styles/main.css'
import App from './App.vue'
import router from './router'

// Temporary CI verification change: this type mismatch should fail the frontend build on the test branch.
const __ciVerificationShouldFail: string = 1

createApp(App)
  .use(createPinia())
  .use(router)
  .use(Vant)
  .mount('#app')
