<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import type { EChartsOption, EChartsType } from 'echarts'

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
let echartsPromise: Promise<typeof import('echarts/core')> | null = null

const chartStyle = computed(() => ({
  height: typeof props.height === 'number' ? `${props.height}px` : props.height
}))

async function loadEcharts() {
  echartsPromise ??= Promise.all([
    import('echarts/charts'),
    import('echarts/components'),
    import('echarts/core'),
    import('echarts/renderers')
  ]).then(([charts, components, core, renderers]) => {
    core.use([
      charts.BarChart,
      charts.LineChart,
      charts.PieChart,
      components.GridComponent,
      components.LegendComponent,
      components.TooltipComponent,
      renderers.CanvasRenderer
    ])
    return core
  })
  return echartsPromise
}

async function ensureChart() {
  if (!chartEl.value) return null
  if (!chart.value) {
    const echarts = await loadEcharts()
    if (!chartEl.value) return null
    chart.value = echarts.init(chartEl.value)
    chart.value.on('click', (params) => emit('chart-click', params))
  }
  return chart.value
}

async function renderChart() {
  await nextTick()
  const instance = await ensureChart()
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
