// ============================================================================
// 檔案名稱：mapCoordinateMapper.ts
// 模組說明：後端 RCS 坐標系 → Three.js 空間坐標 轉換工具
//
// 坐標轉換規則 (依 MAP_API_SPEC.md)：
//   RCS posX  → Three.js position.x  (不變)
//   RCS posY  → Three.js position.z  (地面縱深軸)
//   RCS posZ  → Three.js position.y  (高度，預設 0)
//   旋轉角度 (度) → 弧度：rotationY = rotation × (Math.PI / 180)
//
// 物件垂直置中：position.y = posZ + height / 2
//   (Three.js BoxGeometry 以中心點為錨，RCS 以底部為錨)
// ============================================================================

import type { MapDataDto, PointDto, MapObjectDto } from '../../map-management/types/mapDto'
import type {
  SceneData,
  ScenePoint,
  SceneEdge,
  SceneObject,
  Vec3,
} from '../types/map3d'

// ─────────────────────────────────────────────────────────────────────────────
// 解析 neighbors JSON 字串為點位編號陣列
// 例："[\"P_02\",\"P_03\"]"  →  ["P_02", "P_03"]
// ─────────────────────────────────────────────────────────────────────────────
function parseNeighbors(neighborsJson: string): string[] {
  try {
    const parsed = JSON.parse(neighborsJson)
    if (Array.isArray(parsed)) return parsed as string[]
    return []
  } catch {
    return []
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 將單一路徑點 DTO 轉換為 Three.js ScenePoint
// ─────────────────────────────────────────────────────────────────────────────
function mapPointDtoToScenePoint(dto: PointDto): ScenePoint {
  return {
    code: dto.pointCode,
    // y 軸固定為小值使點位略高於地板，避免 Z-fighting
    position: [dto.x, 0.05, dto.y],
    type: dto.pointType,
    // 將停靠角度從度轉換為弧度
    headingRad: ((dto.headingAngle ?? 0) * Math.PI) / 180,
    isDisabled: dto.isDisabled ?? false,
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 根據所有路徑點建立連線邊列表 (避免重複邊 A→B 與 B→A)
// ─────────────────────────────────────────────────────────────────────────────
function buildEdges(points: PointDto[]): SceneEdge[] {
  // 建立點位編號 → Three.js 坐標的查詢表
  const positionMap = new Map<string, Vec3>()
  for (const p of points) {
    positionMap.set(p.pointCode, [p.x, 0.05, p.y])
  }

  const addedEdges = new Set<string>()
  const edges: SceneEdge[] = []

  for (const point of points) {
    const neighbors = parseNeighbors(point.neighbors)
    const fromPos = positionMap.get(point.pointCode)
    if (!fromPos) continue

    for (const neighborCode of neighbors) {
      const toPos = positionMap.get(neighborCode)
      if (!toPos) continue

      // 使用排序後的組合鍵去除重複邊
      const edgeKey = [point.pointCode, neighborCode].sort().join('|')
      if (addedEdges.has(edgeKey)) continue
      addedEdges.add(edgeKey)

      edges.push({ from: fromPos, to: toPos })
    }
  }

  return edges
}

// ─────────────────────────────────────────────────────────────────────────────
// 將單一 3D 環境物件 DTO 轉換為 Three.js SceneObject
// ─────────────────────────────────────────────────────────────────────────────
function mapObjectDtoToSceneObject(dto: MapObjectDto, index: number): SceneObject {
  const posZ = dto.posZ ?? 0
  const height = dto.height

  return {
    id: dto.id ?? index,
    type: dto.objectType,
    position: [
      dto.posX,
      posZ + height / 2, // BoxGeometry 中心點 Y = 底部偏移 + 半高
      dto.posY,           // RCS posY → Three.js Z 軸
    ],
    // 旋轉角度 (度) 轉換為弧度，Three.js 旋轉方向為右手定則
    rotationY: ((dto.rotation ?? 0) * Math.PI) / 180,
    size: {
      length: dto.length,
      width: dto.width,
      height: dto.height,
    },
    rackLevels: dto.rackLevels ?? 1,
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// 主轉換函式：將完整 MapDataDto 轉換為可供 FactoryScene 直接使用之 SceneData
// ─────────────────────────────────────────────────────────────────────────────
export function mapDtoToSceneData(dto: MapDataDto): SceneData {
  const scenePoints = dto.points.map(mapPointDtoToScenePoint)
  const edges = buildEdges(dto.points)
  const sceneObjects = dto.objects.map((obj, i) =>
    mapObjectDtoToSceneObject(obj, i)
  )

  return {
    mapCode: dto.map.mapCode,
    mapName: dto.map.mapName,
    // RCS 地圖寬度對應 Three.js X 軸；地圖高度對應 Three.js Z 軸
    floorWidth: dto.map.width,
    floorDepth: dto.map.height,
    points: scenePoints,
    edges,
    objects: sceneObjects,
  }
}
