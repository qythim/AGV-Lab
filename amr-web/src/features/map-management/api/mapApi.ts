// ============================================================================
// 檔案名稱：mapApi.ts
// 模組說明：地圖 API 客戶端 — 向後端 RCS 取得地圖資料
//           若 API 連線失敗，自動切換至本地模擬資料 (Mock Fallback)
// 端點規範：GET /api/maps/active | GET /api/maps/{mapCode}
// ============================================================================

import type { MapDataDto } from '../types/mapDto'

/** 後端 RCS API 基礎位址 */
const API_BASE = 'http://localhost:8080/api/maps'

// ─────────────────────────────────────────────────────────────────────────────
// 模擬備用資料 (Mock Fallback)
// 說明：當後端 RCS 尚未啟動或網路連線失敗時，前端仍可正常渲染 3D 場景
// ─────────────────────────────────────────────────────────────────────────────
export const MOCK_MAP_DATA: MapDataDto = {
  map: {
    mapCode: 'MOCK_FACTORY',
    mapName: '模擬倉庫 (離線展示)',
    version: 1,
    isActive: true,
    width: 40,
    height: 30,
    originX: 0,
    originY: 0,
  },
  points: [
    // 一般路徑點
    { pointCode: 'P_01', mapCode: 'MOCK_FACTORY', x: 5,  y: 5,  pointType: 'NORMAL',  neighbors: '["P_02","P_05"]' },
    { pointCode: 'P_02', mapCode: 'MOCK_FACTORY', x: 10, y: 5,  pointType: 'NORMAL',  neighbors: '["P_01","P_03","P_06"]' },
    { pointCode: 'P_03', mapCode: 'MOCK_FACTORY', x: 15, y: 5,  pointType: 'RACK',    neighbors: '["P_02","P_04"]' },
    { pointCode: 'P_04', mapCode: 'MOCK_FACTORY', x: 20, y: 5,  pointType: 'RACK',    neighbors: '["P_03","P_07"]' },
    { pointCode: 'P_05', mapCode: 'MOCK_FACTORY', x: 5,  y: 12, pointType: 'NORMAL',  neighbors: '["P_01","P_06"]' },
    { pointCode: 'P_06', mapCode: 'MOCK_FACTORY', x: 10, y: 12, pointType: 'STATION', neighbors: '["P_02","P_05","P_07"]' },
    { pointCode: 'P_07', mapCode: 'MOCK_FACTORY', x: 20, y: 12, pointType: 'NORMAL',  neighbors: '["P_04","P_06","P_08"]' },
    { pointCode: 'P_08', mapCode: 'MOCK_FACTORY', x: 28, y: 12, pointType: 'CHARGER', neighbors: '["P_07"]' },
    { pointCode: 'P_09', mapCode: 'MOCK_FACTORY', x: 5,  y: 20, pointType: 'NORMAL',  neighbors: '["P_05","P_10"]' },
    { pointCode: 'P_10', mapCode: 'MOCK_FACTORY', x: 15, y: 20, pointType: 'NORMAL',  neighbors: '["P_09","P_11"]' },
    { pointCode: 'P_11', mapCode: 'MOCK_FACTORY', x: 28, y: 20, pointType: 'CHARGER', neighbors: '["P_10","P_08"]' },
  ],
  objects: [
    // 周圍圍牆 (WALL)
    { id: 1,  mapCode: 'MOCK_FACTORY', objectType: 'WALL',     posX: 20,   posY: 0.5,  posZ: 0, rotation: 0,   length: 40,  width: 1,   height: 3.5 },
    { id: 2,  mapCode: 'MOCK_FACTORY', objectType: 'WALL',     posX: 20,   posY: 29.5, posZ: 0, rotation: 0,   length: 40,  width: 1,   height: 3.5 },
    { id: 3,  mapCode: 'MOCK_FACTORY', objectType: 'WALL',     posX: 0.5,  posY: 15,   posZ: 0, rotation: 0,   length: 1,   width: 30,  height: 3.5 },
    { id: 4,  mapCode: 'MOCK_FACTORY', objectType: 'WALL',     posX: 39.5, posY: 15,   posZ: 0, rotation: 0,   length: 1,   width: 30,  height: 3.5 },
    // 貨架排列 (RACK)
    { id: 5,  mapCode: 'MOCK_FACTORY', objectType: 'RACK',     posX: 15,   posY: 5,    posZ: 0, rotation: 0,   length: 3,   width: 1.5, height: 3.5, rackLevels: 3 },
    { id: 6,  mapCode: 'MOCK_FACTORY', objectType: 'RACK',     posX: 20,   posY: 5,    posZ: 0, rotation: 0,   length: 3,   width: 1.5, height: 3.5, rackLevels: 3 },
    { id: 7,  mapCode: 'MOCK_FACTORY', objectType: 'RACK',     posX: 15,   posY: 8,    posZ: 0, rotation: 0,   length: 3,   width: 1.5, height: 3.5, rackLevels: 2 },
    { id: 8,  mapCode: 'MOCK_FACTORY', objectType: 'RACK',     posX: 20,   posY: 8,    posZ: 0, rotation: 0,   length: 3,   width: 1.5, height: 3.5, rackLevels: 2 },
    // 工作站 (STATION)
    { id: 9,  mapCode: 'MOCK_FACTORY', objectType: 'STATION',  posX: 10,   posY: 12,   posZ: 0, rotation: 0,   length: 2.5, width: 1.5, height: 1.2 },
    // 充電站 (CHARGER)
    { id: 10, mapCode: 'MOCK_FACTORY', objectType: 'CHARGER',  posX: 28,   posY: 12,   posZ: 0, rotation: 180, length: 1.2, width: 1.0, height: 1.8 },
    { id: 11, mapCode: 'MOCK_FACTORY', objectType: 'CHARGER',  posX: 28,   posY: 20,   posZ: 0, rotation: 180, length: 1.2, width: 1.0, height: 1.8 },
    // 輸送帶 (CONVEYOR)
    { id: 12, mapCode: 'MOCK_FACTORY', objectType: 'CONVEYOR', posX: 8,    posY: 25,   posZ: 0, rotation: 0,   length: 6,   width: 1.2, height: 0.9 },
  ],
}

// ─────────────────────────────────────────────────────────────────────────────
// API 回應資料來源類型
// ─────────────────────────────────────────────────────────────────────────────
export type DataSource = 'api' | 'mock'

export interface MapLoadResult {
  /** 地圖資料內容 */
  data: MapDataDto
  /** 資料來源標示 (api = 後端即時 | mock = 本地模擬) */
  source: DataSource
}

// ─────────────────────────────────────────────────────────────────────────────
// 取得目前啟用中的地圖 (GET /api/maps/active)
// ─────────────────────────────────────────────────────────────────────────────
export async function fetchActiveMap(): Promise<MapLoadResult> {
  try {
    const response = await fetch(`${API_BASE}/active`, {
      signal: AbortSignal.timeout(5000), // 5 秒連線逾時
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    const data: MapDataDto = await response.json()
    return { data, source: 'api' }
  } catch {
    // 後端離線時切換至模擬資料
    console.warn('[MapApi] 後端 RCS 連線失敗，切換至本地模擬資料 (Mock Fallback)')
    return { data: MOCK_MAP_DATA, source: 'mock' }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 依地圖編號取得指定地圖 (GET /api/maps/{mapCode})
// ─────────────────────────────────────────────────────────────────────────────
export async function fetchMapByCode(mapCode: string): Promise<MapLoadResult> {
  try {
    const response = await fetch(`${API_BASE}/${encodeURIComponent(mapCode)}`, {
      signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}: ${response.statusText}`)
    }
    const data: MapDataDto = await response.json()
    return { data, source: 'api' }
  } catch {
    console.warn(`[MapApi] 無法取得地圖 ${mapCode}，切換至本地模擬資料`)
    return { data: MOCK_MAP_DATA, source: 'mock' }
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 取得所有地圖清單 (GET /api/maps)
// 說明：供管理介面列表顯示使用
// ─────────────────────────────────────────────────────────────────────────────
export async function fetchMapList(): Promise<MapDataDto[]> {
  try {
    const response = await fetch(`${API_BASE}`, {
      signal: AbortSignal.timeout(5000),
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const raw = await response.json()
    if (!Array.isArray(raw)) return []

    // 將後端可能回傳的扁平 MapDto 陣列標準化為前端統一的 MapDataDto 格式
    return raw.map((item: any) => {
      if (item && item.map) {
        return item as MapDataDto
      }
      return {
        map: item,
        points: Array.isArray(item?.points) ? item.points : [],
        objects: Array.isArray(item?.objects) ? item.objects : [],
      } as MapDataDto
    })
  } catch {
    console.warn('[MapApi] 無法取得地圖清單，傳回空陣列')
    return []
  }
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
