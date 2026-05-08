import { load as loadAmapApi } from '@amap/amap-jsapi-loader'

export interface AmapTip {
  id?: string
  name?: string
  district?: string
  address?: string
  location?: AmapPosition
}

export interface AmapAutoCompleteResult {
  tips?: AmapTip[]
  info?: string
  count?: string | number
}

export interface AmapAutoComplete {
  search(keyword: string, callback: (status: string, result: AmapAutoCompleteResult | string) => void): void
}

export interface AmapLngLat {
  getLng(): number
  getLat(): number
  toArray(): [number, number]
}

export type AmapPosition = AmapLngLat | [number, number]

export interface AmapMapClickEvent {
  lnglat: AmapLngLat
}

export interface AmapMap {
  add(control: unknown): void
  addControl(control: unknown): void
  destroy(): void
  off(event: string, handler: (event: AmapMapClickEvent) => void): void
  on(event: string, handler: (event: AmapMapClickEvent) => void): void
  resize(): void
  setCenter(position: AmapPosition): void
  setZoomAndCenter(zoom: number, position: AmapPosition): void
}

export interface AmapMarker {
  setPosition(position: AmapPosition): void
}

export interface AmapPoi {
  id?: string
  name?: string
  address?: string
  district?: string
  location?: AmapPosition
}

export interface AmapPlaceSearchResult {
  poiList?: {
    pois?: AmapPoi[]
  }
}

export interface AmapPlaceSearch {
  getDetails(id: string, callback: (status: string, result: AmapPlaceSearchResult | string) => void): void
  search(keyword: string, callback: (status: string, result: AmapPlaceSearchResult | string) => void): void
}

export interface AmapReGeocodeResult {
  regeocode?: {
    formattedAddress?: string
    pois?: AmapPoi[]
  }
}

export interface AmapGeocodeResult {
  geocodes?: Array<{
    formattedAddress?: string
    location?: AmapPosition
  }>
}

export interface AmapGeocoder {
  getAddress(position: AmapPosition, callback: (status: string, result: AmapReGeocodeResult | string) => void): void
  getLocation(keyword: string, callback: (status: string, result: AmapGeocodeResult | string) => void): void
}

export interface AmapGeolocationResult {
  position?: AmapPosition
  accuracy?: number
  info?: string
  message?: string
}

export interface AmapGeolocation {
  getCityInfo(callback: (status: string, result: AmapGeolocationResult | string) => void): void
  getCurrentPosition(callback: (status: string, result: AmapGeolocationResult | string) => void): void
}

export interface AmapApi {
  AutoComplete: new (options?: { city?: string; citylimit?: boolean }) => AmapAutoComplete
  Geocoder: new (options?: { city?: string; extensions?: string; radius?: number }) => AmapGeocoder
  Geolocation: new (options?: {
    enableHighAccuracy?: boolean
    extensions?: string
    getCityWhenFail?: boolean
    needAddress?: boolean
    timeout?: number
  }) => AmapGeolocation
  Map: new (container: HTMLElement | string, options?: { center?: AmapPosition; resizeEnable?: boolean; viewMode?: string; zoom?: number }) => AmapMap
  Marker: new (options: { anchor?: string; map?: AmapMap; position: AmapPosition; title?: string }) => AmapMarker
  PlaceSearch: new (options?: { city?: string; citylimit?: boolean; extensions?: string; pageIndex?: number; pageSize?: number }) => AmapPlaceSearch
  ToolBar: new (options?: { position?: string | Record<string, number> }) => unknown
}

interface AmapWindow extends Window {
  _AMapSecurityConfig?: {
    securityJsCode?: string
  }
}

let amapPromise: Promise<AmapApi> | null = null

export function amapKey() {
  return (import.meta.env.VITE_AMAP_KEY || '').trim()
}

export function amapCity() {
  return (import.meta.env.VITE_AMAP_CITY || '').trim()
}

export function isAmapConfigured() {
  return Boolean(amapKey())
}

export function loadAmap() {
  const key = amapKey()
  if (!key) {
    return Promise.reject(new Error('未配置 VITE_AMAP_KEY'))
  }

  if (!amapPromise) {
    const securityJsCode = (import.meta.env.VITE_AMAP_SECURITY_JS_CODE || '').trim()
    if (securityJsCode) {
      const amapWindow = window as AmapWindow
      amapWindow._AMapSecurityConfig = { securityJsCode }
    }

    amapPromise = (loadAmapApi({
      key,
      version: '2.0',
      plugins: ['AMap.AutoComplete', 'AMap.Geocoder', 'AMap.Geolocation', 'AMap.PlaceSearch', 'AMap.ToolBar']
    }) as Promise<AmapApi>).catch((error) => {
      amapPromise = null
      throw error
    })
  }

  return amapPromise
}
