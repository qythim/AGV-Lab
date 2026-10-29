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
  SceneObject,
} from '../types/map3d'

function mapPointDtoToScenePoint(dto: PointDto): ScenePoint {
  return {
    code: dto.pointCode,
    // y 軸固定為小值使點位略高於地板，避免 Z-fighting
    position: [dto.x, 0.05, dto.y],
    type: dto.pointType,
    name: dto.pointName,
  }
}

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
  const scenePoints = dto.pointList.map(mapPointDtoToScenePoint)
  const codes = new Set(dto.pointList.map(point => point.pointCode))
  if (!dto.lineList.every(line => codes.has(line.startPointCode) && codes.has(line.endPointCode))) {
    throw new Error('路線參照不存在的點位')
  }
  const edges = dto.lineList.map(line => ({
    ...line,
    robotCarrierState: [...line.robotCarrierState],
    allowedTravelModes: [...line.allowedTravelModes],
  }))
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
