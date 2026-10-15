// ============================================================================
// 檔案名稱：mapDto.ts
// 模組說明：後端 RCS (Robot Control System) 地圖 API 傳輸資料結構定義 (DTO)
// 參考規範：MAP_API_SPEC.md
// ============================================================================

/**
 * 地圖基礎資訊 DTO
 */
export interface MapDto {
  /** 地圖唯一識別碼 (例：FACTORY_01) */
  mapCode: string
  /** 地圖顯示名稱 */
  mapName: string
  /** 地圖版本號碼 (從 1 開始) */
  version: number
  /** 是否為系統當前啟用中之唯一地圖 */
  isActive: boolean
  /** 廠房地面寬度 (公尺，對應 Three.js X 軸) */
  width: number
  /** 廠房地面長度 (公尺，對應 Three.js Z 軸) */
  height: number
  /** 原點 X 坐標 (公尺，預設 0.0) */
  originX?: number
  /** 原點 Y 坐標 (公尺，預設 0.0) */
  originY?: number
  /** 擴充設定字串 (JSON 格式，儲存圖層或備註資訊) */
  context?: string
}

/**
 * 拓撲路徑點類型列舉
 */
export type PointType = 'NORMAL' | 'CHARGER' | 'RACK' | 'STATION'

/**
 * 地圖路徑點 (Node) DTO
 */
export interface PointDto {
  /** 點位唯一編號 (例：P_01) */
  pointCode: string
  /** 所屬地圖編號 */
  mapCode: string
  /** 地面 X 坐標 (公尺，對應 Three.js X) */
  x: number
  /** 地面 Y 坐標 (公尺，對應 Three.js Z) */
  y: number
  /** 點位高度 (公尺，預設 0.0，對應 Three.js Y) */
  z?: number
  /** 點位功能類型 */
  pointType: PointType
  /** AGV 停靠時之預設轉向角度 (0 ~ 359 度) */
  headingAngle?: number
  /** 相鄰連通點位清單之 JSON 字串 (例："[\"P_02\", \"P_03\"]") */
  neighbors: string
  /** 該點允許之最高車速 (m/s) */
  maxSpeed?: number
  /** 是否已被停用/封鎖 */
  isDisabled?: boolean
}

/**
 * 3D 環境物件類型列舉
 */
export type MapObjectType = 'WALL' | 'RACK' | 'STATION' | 'CHARGER' | 'CONVEYOR'

/**
 * 3D 環境物件 (Object) DTO
 */
export interface MapObjectDto {
  /** 物件資料庫識別碼 */
  id?: number
  /** 所屬地圖編號 */
  mapCode: string
  /** 物件種類 */
  objectType: MapObjectType
  /** 中心點 X 坐標 (公尺，對應 Three.js X) */
  posX: number
  /** 中心點 Y 坐標 (公尺，對應 Three.js Z) */
  posY: number
  /** 中心點或底部高度 (公尺，對應 Three.js Y) */
  posZ?: number
  /** 繞垂直軸旋轉角度 (0 ~ 360 度) */
  rotation?: number
  /** 幾何長度 (公尺，沿 X 軸) */
  length: number
  /** 幾何寬度 (公尺，沿 Z 軸) */
  width: number
  /** 幾何高度 (公尺，沿 Y 軸) */
  height: number
  /** 貨架層數 (僅當 objectType 為 RACK 時有效) */
  rackLevels?: number
}

/**
 * 完整地圖資料傳輸封裝 DTO (MapDataDto)
 */
export interface MapDataDto {
  /** 地圖主檔資訊 */
  map: MapDto
  /** 路徑點清單 */
  points: PointDto[]
  /** 3D 環境物件清單 */
  objects: MapObjectDto[]
}
