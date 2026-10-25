// ============================================================================
// 檔案名稱：mapApi.ts
// 模組說明：地圖 API 客戶端 — 向後端 RCS 取得地圖資料
//           載入失敗時傳遞錯誤，由呼叫端顯示錯誤狀態
// 端點規範：GET /api/maps/active | GET /api/maps/{mapCode}
// ============================================================================

import type { MapDataDto } from '../types/mapDto'

/** 後端 RCS API 基礎位址 */
const API_BASE = 'http://localhost:8080/api/maps'


// 取得目前啟用中的地圖，失敗時由呼叫端處理錯誤
export async function fetchActiveMap(): Promise<MapDataDto> {
  const response = await fetch(`${API_BASE}/active`, {
    signal: AbortSignal.timeout(5000), // 五秒連線逾時
  })

  if (!response.ok) {
    throw new Error(`載入啟用地圖失敗：HTTP ${response.status}`)
  }

  const data: MapDataDto = await response.json()
  return data
}

// 依地圖代碼取得指定地圖，失敗時由呼叫端處理錯誤
export async function fetchMapByCode(mapCode: string): Promise<MapDataDto> {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(mapCode)}`, {
    signal: AbortSignal.timeout(5000),
  })

  if (!response.ok) {
    throw new Error(`載入地圖「${mapCode}」失敗：HTTP ${response.status}`)
  }

  const data: MapDataDto = await response.json()
  return data
}

// ─────────────────────────────────────────────────────────────────────────────
export async function fetchMapList(): Promise<MapDataDto[]> {
  const response = await fetch(API_BASE, {
    signal: AbortSignal.timeout(5000),
  })

  if (!response.ok) {
    throw new Error(`載入地圖清單失敗：HTTP ${response.status}`)
  }

  const data: unknown = await response.json()

  if (!Array.isArray(data)) {
    throw new Error('地圖清單格式錯誤，預期為陣列')
  }

  return data
}

// ─────────────────────────────────────────────────────────────────────────────
// 儲存/更新地圖 (POST /api/maps)
// ─────────────────────────────────────────────────────────────────────────────
export async function saveMap(mapData: MapDataDto): Promise<MapDataDto> {
  const response = await fetch(`${API_BASE}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(mapData),
  })
  if (!response.ok) {
    throw new Error(`儲存地圖失敗：HTTP ${response.status}`)
  }
  return await response.json()
}


export async function importMap(jsonText: string): Promise<MapDataDto> {
  const response = await fetch(`${API_BASE}/import`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: jsonText,
  })

  if (!response.ok) {
    throw new Error(`儲存地圖失敗：HTTP ${response.status}`)
  }

  const savedMap: MapDataDto = await response.json()
  return savedMap
}