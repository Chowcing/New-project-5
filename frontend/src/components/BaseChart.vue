<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { EChartsOption, EChartsType } from 'echarts'

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = withDefaults(defineProps<{
  option: EChartsOption
  height?: number | string
}>(), {
  height: 240
})

const emit = defineEmits<{
  'chart-click': [params: unknown]
}>()

const chartEl = ref<HTMLDivElement | null>(null)
const chart = shallowRef<EChartsType | null>(null)
let resizeObserver: ResizeObserver | null = null

const chartStyle = computed(() => ({
  height: typeof props.height === 'number' ? `${props.height}px` : props.height
}))

function ensureChart() {
  if (!chartEl.value) return null
  if (!chart.value) {
    chart.value = init(chartEl.value)
    chart.value.on('click', (params) => emit('chart-click', params))
  }
  return chart.value
}

async function renderChart() {
  await nextTick()
  const instance = ensureChart()
  if (!instance) return
  instance.setOption(props.option, true)
  instance.resize()
}

onMounted(() => {
  renderChart()
  if (chartEl.value) {
    resizeObserver = new ResizeObserver(() => chart.value?.resize())
    resizeObserver.observe(chartEl.value)
  }
})

watch(() => props.option, renderChart, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart.value?.dispose()
  chart.value = null
})
</script>

<template>
  <div ref="chartEl" class="base-chart" :style="chartStyle" />
</template>

<style scoped>
.base-chart {
  width: 100%;
  min-height: 180px;
}
</style>
