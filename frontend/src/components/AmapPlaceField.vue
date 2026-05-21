<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { showToast } from 'vant'
import { amapCity, isAmapConfigured, loadAmap } from '@/utils/amap'
import type {
  AmapApi,
  AmapAutoComplete,
  AmapGeocodeResult,
  AmapGeocoder,
  AmapGeolocation,
  AmapGeolocationResult,
  AmapLngLat,
  AmapMap,
  AmapMapClickEvent,
  AmapMarker,
  AmapPlaceSearch,
  AmapPlaceSearchResult,
  AmapPoi,
  AmapPosition,
  AmapReGeocodeResult,
  AmapTip
} from '@/utils/amap'

interface SelectedPlace {
  name: string
  address?: string
  position?: AmapPosition
}

const MAX_PLACE_LENGTH = 128
const MAX_SUGGESTIONS = 8
const SEARCH_DELAY_MS = 280
const HIDE_DELAY_MS = 160
const DEFAULT_CENTER: [number, number] = [116.397428, 39.90923]

const props = withDefaults(defineProps<{
  modelValue: string
  label?: string
  placeholder?: string
  required?: boolean
}>(), {
  label: '地点',
  placeholder: '输入店名或地点，选择高德建议',
  required: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const configured = isAmapConfigured()
const autoComplete = ref<AmapAutoComplete | null>(null)
const fieldFocused = ref(false)
const fieldLoading = ref(false)
const loadFailed = ref(false)
const suggestions = ref<AmapTip[]>([])
const statusText = ref('')
const pickerVisible = ref(false)
const pickerKeyword = ref('')
const pickerSuggestions = ref<AmapTip[]>([])
const pickerStatusText = ref('')
const pickerSearching = ref(false)
const locating = ref(false)
const pickerMapLoading = ref(false)
const pickerMapStatus = ref('')
const selectedPlace = ref<SelectedPlace | null>(null)
const nearbyPlaces = ref<SelectedPlace[]>([])
const mapContainerRef = ref<HTMLElement | null>(null)
const amapApi = ref<AmapApi | null>(null)
const map = ref<AmapMap | null>(null)
const marker = ref<AmapMarker | null>(null)
const geocoder = ref<AmapGeocoder | null>(null)
const geolocation = ref<AmapGeolocation | null>(null)
const placeSearch = ref<AmapPlaceSearch | null>(null)
let fieldSearchTimer: ReturnType<typeof window.setTimeout> | undefined
let pickerSearchTimer: ReturnType<typeof window.setTimeout> | undefined
let hideTimer: ReturnType<typeof window.setTimeout> | undefined
let fieldRequestSeq = 0
let pickerRequestSeq = 0

const fieldValue = computed({
  get: () => props.modelValue,
  set: (value: string) => {
    emit('update:modelValue', value)
    scheduleFieldSearch(value)
  }
})

const fieldPlaceholder = computed(() => {
  return configured ? props.placeholder : '如美宜佳、公司楼下便利店'
})

const showSuggestions = computed(() => {
  return configured && fieldFocused.value && (suggestions.value.length > 0 || Boolean(statusText.value))
})

const selectedPlaceText = computed(() => {
  if (!selectedPlace.value) {
    return '未选择地点'
  }
  return selectedPlace.value.name
})

const selectedPlaceAddress = computed(() => normalizeText(selectedPlace.value?.address))

function normalizeText(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function truncatePlace(value: string) {
  return value.length > MAX_PLACE_LENGTH ? value.slice(0, MAX_PLACE_LENGTH) : value
}

function tipTitle(tip: AmapTip) {
  return normalizeText(tip.name) || '未命名地点'
}

function tipSubtitle(tip: AmapTip) {
  return [normalizeText(tip.district), normalizeText(tip.address)]
    .filter(Boolean)
    .join(' ')
}

function tipKey(tip: AmapTip, index: number) {
  return tip.id || `${tipTitle(tip)}-${tipSubtitle(tip)}-${index}`
}

function tipValue(tip: AmapTip) {
  return placeValue({
    name: tipTitle(tip),
    address: tipSubtitle(tip)
  })
}

function placeValue(place: SelectedPlace) {
  const address = normalizeText(place.address)
  const placeName = normalizeText(place.name)
  return truncatePlace(address ? `${placeName} ${address}` : placeName)
}

function isLngLat(value: unknown): value is AmapLngLat {
  return Boolean(value)
    && typeof value === 'object'
    && typeof (value as AmapLngLat).getLng === 'function'
    && typeof (value as AmapLngLat).getLat === 'function'
}

function toPosition(value: unknown): AmapPosition | null {
  if (Array.isArray(value) && value.length >= 2) {
    const lng = Number(value[0])
    const lat = Number(value[1])
    return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null
  }
  if (isLngLat(value)) {
    return value
  }
  if (typeof value === 'string') {
    const [lng, lat] = value.split(',').map((item) => Number(item.trim()))
    return Number.isFinite(lng) && Number.isFinite(lat) ? [lng, lat] : null
  }
  return null
}

function poiToPlace(poi: AmapPoi): SelectedPlace {
  return {
    name: normalizeText(poi.name) || '已选择地点',
    address: [normalizeText(poi.district), normalizeText(poi.address)].filter(Boolean).join(' '),
    position: toPosition(poi.location) || undefined
  }
}

function placeKey(place: SelectedPlace, index: number) {
  return `${place.name}-${place.address || ''}-${index}`
}

async function resizeMapAfterLayout() {
  await nextTick()
  map.value?.resize()
}

function firstPoi(result: AmapPlaceSearchResult | string) {
  return typeof result === 'string' ? undefined : result.poiList?.pois?.[0]
}

function firstGeocode(result: AmapGeocodeResult | string) {
  return typeof result === 'string' ? undefined : result.geocodes?.[0]
}

async function ensureAutoComplete() {
  if (!configured || loadFailed.value) {
    return null
  }
  if (autoComplete.value) {
    return autoComplete.value
  }

  fieldLoading.value = true
  statusText.value = ''
  try {
    const AMap = await loadAmap()
    amapApi.value = AMap
    const city = amapCity()
    autoComplete.value = new AMap.AutoComplete({
      city: city || undefined,
      citylimit: Boolean(city)
    })
    return autoComplete.value
  } catch {
    loadFailed.value = true
    statusText.value = '高德地点服务加载失败，可手动输入'
    return null
  } finally {
    fieldLoading.value = false
  }
}

async function searchTips(keyword: string) {
  const service = await ensureAutoComplete()
  if (!service) {
    return []
  }

  return new Promise<AmapTip[]>((resolve) => {
    service.search(keyword, (status, result) => {
      if (status !== 'complete' || typeof result === 'string') {
        resolve([])
        return
      }

      resolve((result.tips || [])
        .filter((tip) => Boolean(normalizeText(tip.name)))
        .slice(0, MAX_SUGGESTIONS))
    })
  })
}

function scheduleFieldSearch(keyword: string) {
  if (fieldSearchTimer) {
    window.clearTimeout(fieldSearchTimer)
  }

  const nextKeyword = keyword.trim()
  if (!configured || !fieldFocused.value || !nextKeyword) {
    suggestions.value = []
    statusText.value = ''
    return
  }

  fieldSearchTimer = window.setTimeout(() => {
    void searchFieldPlaces(nextKeyword)
  }, SEARCH_DELAY_MS)
}

async function searchFieldPlaces(keyword: string) {
  const currentSeq = ++fieldRequestSeq
  fieldLoading.value = true
  try {
    const tips = await searchTips(keyword)
    if (currentSeq !== fieldRequestSeq || !fieldFocused.value) {
      return
    }
    suggestions.value = tips
    statusText.value = tips.length ? '' : '未找到地点，可继续手动输入'
  } catch {
    suggestions.value = []
    statusText.value = '地点搜索暂不可用，可手动输入'
  } finally {
    if (currentSeq === fieldRequestSeq) {
      fieldLoading.value = false
    }
  }
}

function handleFocus() {
  if (hideTimer) {
    window.clearTimeout(hideTimer)
  }
  fieldFocused.value = true
  scheduleFieldSearch(props.modelValue)
}

function handleBlur() {
  hideTimer = window.setTimeout(() => {
    fieldFocused.value = false
  }, HIDE_DELAY_MS)
}

function selectTip(tip: AmapTip) {
  fieldRequestSeq += 1
  fieldLoading.value = false
  emit('update:modelValue', tipValue(tip))
  suggestions.value = []
  statusText.value = ''
  fieldFocused.value = false
}

function closeFieldSuggestions() {
  fieldRequestSeq += 1
  suggestions.value = []
  statusText.value = ''
  fieldFocused.value = false
}

async function ensurePickerServices() {
  if (loadFailed.value) {
    return null
  }

  const AMap = amapApi.value || await loadAmap()
  amapApi.value = AMap
  const city = amapCity()

  if (!geocoder.value) {
    geocoder.value = new AMap.Geocoder({
      city: city || undefined,
      extensions: 'all',
      radius: 1000
    })
  }
  if (!geolocation.value) {
    geolocation.value = new AMap.Geolocation({
      enableHighAccuracy: true,
      extensions: 'all',
      getCityWhenFail: true,
      needAddress: false,
      timeout: 10000
    })
  }
  if (!placeSearch.value) {
    placeSearch.value = new AMap.PlaceSearch({
      city: city || undefined,
      citylimit: Boolean(city),
      extensions: 'all',
      pageIndex: 1,
      pageSize: 10
    })
  }

  return AMap
}

async function openPicker() {
  if (!configured) {
    showToast('未配置高德 Key，可手动输入地点')
    return
  }

  closeFieldSuggestions()
  pickerKeyword.value = props.modelValue
  pickerSuggestions.value = []
  pickerStatusText.value = ''
  pickerMapStatus.value = ''
  selectedPlace.value = props.modelValue ? { name: props.modelValue } : null
  nearbyPlaces.value = []
  pickerVisible.value = true
  await nextTick()
  await initPickerMap()
  if (pickerKeyword.value.trim()) {
    schedulePickerSearch(pickerKeyword.value)
  }
}

async function initPickerMap() {
  if (!pickerVisible.value || !mapContainerRef.value) {
    return
  }

  pickerMapLoading.value = true
  try {
    const AMap = await ensurePickerServices()
    if (!AMap || !mapContainerRef.value) {
      return
    }

    if (!map.value) {
      map.value = new AMap.Map(mapContainerRef.value, {
        center: DEFAULT_CENTER,
        resizeEnable: true,
        viewMode: '2D',
        zoom: 14
      })
      marker.value = new AMap.Marker({
        anchor: 'bottom-center',
        map: map.value,
        position: DEFAULT_CENTER,
        title: '已选择地点'
      })
      map.value.addControl(new AMap.ToolBar({ position: 'RB' }))
      map.value.on('click', handleMapClick)
    } else {
      map.value.resize()
    }

    if (props.modelValue.trim()) {
      await centerByKeyword(props.modelValue.trim())
    } else if (amapCity()) {
      await centerByKeyword(amapCity())
    }
  } catch {
    loadFailed.value = true
    pickerMapStatus.value = '高德选址组件加载失败，可手动输入地点'
  } finally {
    pickerMapLoading.value = false
  }
}

function handleMapClick(event: AmapMapClickEvent) {
  void selectPosition(event.lnglat, '地图选点')
}

function locationErrorMessage(result: AmapGeolocationResult | string) {
  if (typeof result === 'string') {
    return result || '定位失败，请检查浏览器定位权限'
  }
  return normalizeText(result.message) || normalizeText(result.info) || '定位失败，请检查浏览器定位权限'
}

async function locateCurrentPosition() {
  if (locating.value) {
    return
  }

  try {
    locating.value = true
    pickerMapStatus.value = '正在获取当前位置...'
    await ensurePickerServices()

    const service = geolocation.value
    if (!service) {
      pickerMapStatus.value = '定位服务加载失败，可搜索或点击地图选点'
      return
    }

    service.getCurrentPosition((status, result) => {
      locating.value = false
      if (status === 'complete' && typeof result !== 'string') {
        const position = toPosition(result.position)
        if (position) {
          map.value?.setZoomAndCenter(17, position)
          void selectPosition(position, '当前位置')
          return
        }
      }

      pickerMapStatus.value = locationErrorMessage(result)
    })
  } catch {
    locating.value = false
    pickerMapStatus.value = '定位服务加载失败，可搜索或点击地图选点'
  }
}

async function centerByKeyword(keyword: string) {
  const service = geocoder.value
  if (!service) {
    return
  }

  const position = await new Promise<AmapPosition | null>((resolve) => {
    service.getLocation(keyword, (status, result) => {
      const geocode = status === 'complete' ? firstGeocode(result) : undefined
      resolve(toPosition(geocode?.location))
    })
  })

  if (position) {
    map.value?.setZoomAndCenter(16, position)
    marker.value?.setPosition(position)
  }
}

async function selectPosition(position: AmapPosition, fallbackName: string) {
  map.value?.setCenter(position)
  marker.value?.setPosition(position)
  nearbyPlaces.value = []
  selectedPlace.value = {
    name: fallbackName,
    position
  }

  const service = geocoder.value
  if (!service) {
    return
  }

  pickerMapStatus.value = '正在解析地址...'
  service.getAddress(position, (status, result) => {
    if (status !== 'complete' || typeof result === 'string') {
      pickerMapStatus.value = '地址解析失败，可继续点击地图或手动输入'
      return
    }

    const nextPlace = reverseGeocodeToPlace(result, position)
    nearbyPlaces.value = reverseGeocodeNearbyPlaces(result, position)
    selectedPlace.value = nextPlace
    pickerMapStatus.value = ''
    void resizeMapAfterLayout()
  })
}

function reverseGeocodeToPlace(result: AmapReGeocodeResult, position: AmapPosition): SelectedPlace {
  const poi = result.regeocode?.pois?.[0]
  if (poi) {
    return {
      ...poiToPlace(poi),
      position
    }
  }

  const address = normalizeText(result.regeocode?.formattedAddress)
  return {
    name: address || '地图选点',
    address,
    position
  }
}

function reverseGeocodeNearbyPlaces(result: AmapReGeocodeResult, position: AmapPosition) {
  return (result.regeocode?.pois || [])
    .map((poi) => ({
      ...poiToPlace(poi),
      position: toPosition(poi.location) || position
    }))
    .filter((place) => Boolean(normalizeText(place.name)))
    .slice(0, 6)
}

function selectNearbyPlace(place: SelectedPlace) {
  selectedPlace.value = place
  if (place.position) {
    map.value?.setCenter(place.position)
    marker.value?.setPosition(place.position)
  }
  pickerMapStatus.value = ''
}

function schedulePickerSearch(keyword: string) {
  if (pickerSearchTimer) {
    window.clearTimeout(pickerSearchTimer)
  }

  const nextKeyword = keyword.trim()
  if (!nextKeyword) {
    pickerSuggestions.value = []
    pickerStatusText.value = ''
    void resizeMapAfterLayout()
    return
  }

  pickerSearchTimer = window.setTimeout(() => {
    void searchPickerPlaces(nextKeyword)
  }, SEARCH_DELAY_MS)
}

async function searchPickerPlaces(keyword: string) {
  const currentSeq = ++pickerRequestSeq
  pickerSearching.value = true
  try {
    const tips = await searchTips(keyword)
    if (currentSeq !== pickerRequestSeq) {
      return
    }
    pickerSuggestions.value = tips
    pickerStatusText.value = tips.length ? '' : '未找到地点，可点击地图选点或手动输入'
  } catch {
    pickerSuggestions.value = []
    pickerStatusText.value = '地点搜索暂不可用，可点击地图选点或手动输入'
  } finally {
    if (currentSeq === pickerRequestSeq) {
      pickerSearching.value = false
    }
    void resizeMapAfterLayout()
  }
}

async function selectPickerTip(tip: AmapTip) {
  pickerRequestSeq += 1
  pickerSearching.value = false
  pickerSuggestions.value = []
  pickerStatusText.value = ''
  nearbyPlaces.value = []
  void resizeMapAfterLayout()
  pickerKeyword.value = tipTitle(tip)
  selectedPlace.value = {
    name: tipTitle(tip),
    address: tipSubtitle(tip),
    position: toPosition(tip.location) || undefined
  }

  const position = selectedPlace.value.position || await resolveTipPosition(tip)
  if (position) {
    selectedPlace.value = {
      ...selectedPlace.value,
      position
    }
    map.value?.setZoomAndCenter(16, position)
    marker.value?.setPosition(position)
    return
  }

  pickerMapStatus.value = '该地点暂无坐标，可直接确认文本'
}

async function resolveTipPosition(tip: AmapTip) {
  const currentPosition = toPosition(tip.location)
  if (currentPosition) {
    return currentPosition
  }

  const service = placeSearch.value
  if (!service) {
    return null
  }

  const keyword = tip.id || tipTitle(tip)
  return new Promise<AmapPosition | null>((resolve) => {
    const callback = (status: string, result: AmapPlaceSearchResult | string) => {
      const poi = status === 'complete' ? firstPoi(result) : undefined
      resolve(toPosition(poi?.location))
    }

    if (tip.id) {
      service.getDetails(keyword, callback)
      return
    }
    service.search(keyword, callback)
  })
}

function confirmPicker() {
  const manualValue = pickerKeyword.value.trim()
  if (selectedPlace.value) {
    emit('update:modelValue', placeValue(selectedPlace.value))
    pickerVisible.value = false
    return
  }
  if (manualValue) {
    emit('update:modelValue', truncatePlace(manualValue))
    pickerVisible.value = false
    return
  }
  showToast('请选择地点或输入地点名称')
}

function destroyPickerMap() {
  if (map.value) {
    map.value.off('click', handleMapClick)
    map.value.destroy()
  }
  map.value = null
  marker.value = null
}

onBeforeUnmount(() => {
  destroyPickerMap()
  if (fieldSearchTimer) {
    window.clearTimeout(fieldSearchTimer)
  }
  if (pickerSearchTimer) {
    window.clearTimeout(pickerSearchTimer)
  }
  if (hideTimer) {
    window.clearTimeout(hideTimer)
  }
})
</script>

<template>
  <div class="amap-place-field">
    <van-field
      v-model="fieldValue"
      :label="label"
      :placeholder="fieldPlaceholder"
      :required="required"
      clearable
      autocomplete="off"
      @focus="handleFocus"
      @blur="handleBlur"
    >
      <template v-if="configured" #button>
        <div class="amap-place-actions">
          <van-loading v-if="fieldLoading" size="16" />
          <van-button size="mini" plain type="primary" native-type="button" @click.stop="openPicker">选址</van-button>
        </div>
      </template>
    </van-field>

    <div v-if="showSuggestions" class="amap-place-suggestions">
      <button
        v-for="(tip, index) in suggestions"
        :key="tipKey(tip, index)"
        type="button"
        class="amap-place-option"
        @mousedown.prevent
        @click="selectTip(tip)"
      >
        <span class="amap-place-name">{{ tipTitle(tip) }}</span>
        <span v-if="tipSubtitle(tip)" class="amap-place-address">{{ tipSubtitle(tip) }}</span>
      </button>
      <div v-if="statusText && !suggestions.length" class="amap-place-status">{{ statusText }}</div>
    </div>

    <van-popup
      v-model:show="pickerVisible"
      position="bottom"
      round
      teleport="body"
      class="amap-picker-popup"
      @opened="initPickerMap"
    >
      <div class="amap-picker">
        <div class="amap-picker-top">
          <header class="amap-picker-header">
            <button type="button" class="amap-picker-text-button" @click="pickerVisible = false">取消</button>
            <strong>选择线下地点</strong>
            <button type="button" class="amap-picker-text-button primary" @click="confirmPicker">确定</button>
          </header>

          <div class="amap-picker-search">
            <van-field
              v-model="pickerKeyword"
              placeholder="搜索店名、小区、写字楼"
              clearable
              autocomplete="off"
              @update:model-value="schedulePickerSearch"
            >
              <template v-if="pickerSearching" #button>
                <van-loading size="16" />
              </template>
            </van-field>
            <div v-if="pickerSuggestions.length || pickerStatusText" class="amap-picker-results">
              <button
                v-for="(tip, index) in pickerSuggestions"
                :key="`picker-${tipKey(tip, index)}`"
                type="button"
                class="amap-picker-result-option"
                @click="selectPickerTip(tip)"
              >
                <span class="amap-place-name">{{ tipTitle(tip) }}</span>
                <span v-if="tipSubtitle(tip)" class="amap-place-address">{{ tipSubtitle(tip) }}</span>
              </button>
              <div v-if="pickerStatusText && !pickerSuggestions.length" class="amap-picker-result-status">{{ pickerStatusText }}</div>
            </div>
          </div>
        </div>

        <div class="amap-picker-map-wrap">
          <div ref="mapContainerRef" class="amap-picker-map"></div>
          <button
            type="button"
            class="amap-locate-button"
            :disabled="locating"
            @click="locateCurrentPosition"
          >
            {{ locating ? '定位中...' : '当前位置' }}
          </button>
          <div v-if="pickerMapLoading" class="amap-picker-map-mask">
            <van-loading size="24" />
            <span>正在加载地图...</span>
          </div>
        </div>

        <section class="amap-picker-selected">
          <div class="amap-picker-summary">
            <span class="amap-picker-selected-label">当前选择</span>
            <strong>{{ selectedPlaceText }}</strong>
            <span class="amap-picker-hint">{{ pickerMapStatus || selectedPlaceAddress || '可搜索地点，也可点击地图选点' }}</span>
          </div>
          <div v-if="nearbyPlaces.length" class="amap-nearby-list">
            <button
              v-for="(place, index) in nearbyPlaces"
              :key="placeKey(place, index)"
              type="button"
              class="amap-nearby-option"
              @click="selectNearbyPlace(place)"
            >
              <strong>{{ place.name }}</strong>
              <span v-if="place.address">{{ place.address }}</span>
            </button>
          </div>
        </section>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.amap-place-field {
  position: relative;
}

.amap-place-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.amap-place-suggestions {
  margin: 0 16px 10px 96px;
  overflow: hidden;
  border: 1px solid var(--border-warm);
  border-radius: 10px;
  background: var(--card-bg);
  box-shadow: var(--shadow-warm);
}

.amap-place-option {
  display: block;
  width: 100%;
  border: 0;
  border-bottom: 1px solid var(--border-warm);
  padding: 10px 12px;
  background: transparent;
  color: inherit;
  text-align: left;
}

.amap-place-option:last-child {
  border-bottom: 0;
}

.amap-place-name {
  display: block;
  font-size: 14px;
  line-height: 20px;
}

.amap-place-address,
.amap-place-status {
  display: block;
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 18px;
}

.amap-place-status {
  padding: 10px 12px;
}

.amap-picker-popup {
  --amap-picker-top-max: 176px;
  --amap-picker-bottom-max: 164px;
  height: 86vh;
  overflow: hidden;
}

.amap-picker {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--page-bg);
}

.amap-picker-top {
  flex: 0 1 auto;
  overflow: hidden;
  max-height: var(--amap-picker-top-max);
}

.amap-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: var(--card-bg);
  font-size: 16px;
}

.amap-picker-text-button {
  border: 0;
  background: transparent;
  color: var(--text-secondary);
  font-size: 14px;
}

.amap-picker-text-button.primary {
  color: var(--primary);
  font-weight: 600;
}

.amap-picker-search {
  padding: 10px 12px;
}

.amap-picker-search :deep(.van-cell) {
  border-radius: 12px;
}

.amap-picker-results {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  overflow-y: hidden;
  max-height: 64px;
  border-radius: 12px;
  padding: 6px;
  background: rgba(255, 253, 249, 0.96);
  box-shadow: 0 10px 26px rgba(var(--theme-shadow-warm-rgb), 0.12);
  scrollbar-width: none;
}

.amap-picker-results::-webkit-scrollbar {
  display: none;
}

.amap-picker-result-option {
  flex: 0 0 156px;
  display: grid;
  gap: 2px;
  border: 1px solid var(--border-warm);
  border-radius: 10px;
  padding: 6px 8px;
  background: var(--page-bg-soft);
  color: inherit;
  text-align: left;
}

.amap-picker-result-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amap-picker-result-status {
  padding: 8px 4px;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 20px;
  white-space: nowrap;
}

.amap-picker-map-wrap {
  flex: 1 1 auto;
  min-height: 0;
  position: relative;
  overflow: hidden;
  margin: 0 12px;
  border-radius: 16px;
  background: var(--border-warm);
}

.amap-picker-map {
  width: 100%;
  height: 100%;
}

.amap-locate-button {
  position: absolute;
  left: 12px;
  bottom: 12px;
  border: 0;
  border-radius: 999px;
  padding: 8px 12px;
  background: var(--card-bg);
  color: var(--primary);
  font-size: 13px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(var(--theme-shadow-warm-rgb), 0.14);
}

.amap-locate-button:disabled {
  color: var(--text-muted);
}

.amap-picker-map-mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  gap: 8px;
  background: rgba(255, 250, 244, 0.86);
  color: var(--text-secondary);
  font-size: 13px;
}

.amap-picker-selected {
  flex: 0 0 auto;
  display: grid;
  gap: 8px;
  overflow: visible;
  max-height: var(--amap-picker-bottom-max);
  margin: 10px 12px 12px;
  border-radius: 14px;
  padding: 10px;
  background: rgba(255, 253, 249, 0.96);
  box-shadow: 0 10px 30px rgba(var(--theme-shadow-warm-rgb), 0.16);
}

.amap-picker-summary {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.amap-picker-selected-label,
.amap-picker-address,
.amap-picker-hint {
  color: var(--text-muted);
  font-size: 12px;
}

.amap-picker-address {
  line-height: 18px;
}

.amap-nearby-list {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  overflow-y: hidden;
  padding-bottom: 2px;
  scrollbar-width: none;
}

.amap-nearby-list::-webkit-scrollbar {
  display: none;
}

.amap-nearby-option {
  flex: 0 0 144px;
  display: grid;
  gap: 2px;
  border: 1px solid var(--border-warm);
  border-radius: 10px;
  padding: 6px 8px;
  background: var(--page-bg-soft);
  color: inherit;
  text-align: left;
}

.amap-nearby-option strong,
.amap-picker-summary strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.amap-nearby-option span {
  overflow: hidden;
  color: var(--text-muted);
  font-size: 12px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 420px) {
  .amap-place-suggestions {
    margin-left: 16px;
  }

  .amap-picker-popup {
    height: 90vh;
  }
}
</style>
