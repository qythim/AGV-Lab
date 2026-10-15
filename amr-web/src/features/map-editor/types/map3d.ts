// ============================================================================
// 檔案名稱：map3d.ts
// 模組說明：Three.js 3D 渲染所需之純前端資料結構定義
//           與後端 DTO 保持分離，透過 mapCoordinateMapper.ts 進行轉換
// ============================================================================

/**
 * Three.js 三維坐標向量
 */
export type Vec3 = [number, number, number]

/**
 * 坐標系轉換後之路徑點 (Node)
 */
export interface ScenePoint {
  /** 點位唯一編號 */
  code: string
  /** Three.js 空間坐標 [x, y, z] */
  position: Vec3
  /** 點位功能類型 (影響渲染顏色) */
  type: 'NORMAL' | 'CHARGER' | 'RACK' | 'STATION'
  /** AGV 停靠方向角度 (弧度) */
  headingRad: number
  /** 是否停用 */
  isDisabled: boolean
}

/**
 * 兩個路徑點之間的連線邊 (Edge)
 */
export interface SceneEdge {
  /** 起點坐標 */
  from: Vec3
  /** 終點坐標 */
  to: Vec3
}

/**
 * 坐標系轉換後之 3D 環境物件
 */
export interface SceneObject {
  /** 資料庫識別碼 */
  id: number
  /** 物件種類 */
  type: 'WALL' | 'RACK' | 'STATION' | 'CHARGER' | 'CONVEYOR'
  /** Three.js 中心點坐標 (底部中心，Y 已加上 height/2) */
  position: Vec3
  /** Y 軸旋轉角度 (弧度) */
  rotationY: number
  /** 幾何尺寸 (公尺) */
  size: { length: number; width: number; height: number }
  /** 貨架層數 (僅 RACK 使用) */
  rackLevels: number
}

/**
 * 場景完整資料 (已完成坐標系轉換，可直接供 FactoryScene 使用)
 */
export interface SceneData {
  /** 地圖唯一識別碼 */
  mapCode: string
  /** 地圖顯示名稱 */
  mapName: string
  /** 廠房地面尺寸 (Three.js X 軸) */
  floorWidth: number
  /** 廠房地面尺寸 (Three.js Z 軸) */
  floorDepth: number
  /** 路徑拓撲點列表 */
  points: ScenePoint[]
  /** 路徑連線邊列表 */
  edges: SceneEdge[]
  /** 3D 環境物件列表 */
  objects: SceneObject[]
}
