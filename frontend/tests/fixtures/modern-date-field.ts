import { createApp } from 'vue'
import Vant from 'vant'
import 'vant/lib/index.css'
import '@/styles/main.css'
import ModernDateFieldFixture from './modern-date-field-fixture.vue'

const app = createApp(ModernDateFieldFixture)

app.use(Vant)
app.mount('#app')
