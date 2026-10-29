// ============================================================================
// 檔案名稱：sceneDataToMapDto.ts
// 模組說明：Three.js SceneData 空間坐標 → 後端 RCS MapDataDto 逆向轉換工具
//
// 逆向坐標轉換規則：
//   Three.js position.x  → RCS posX (不變)
//   Three.js position.z  → RCS posY (地面縱深轉為 2D Y)
//   Three.js position.y - height / 2 → RCS posZ (還原為底部錨點)
//   Three.js rotationY (弧度) → RCS rotation (度)：deg = rad * (180 / Math.PI)
// ============================================================================

import type { MapDataDto, PointDto, MapObjectDto } from '../../map-management/types/mapDto'
import type { SceneData } from '../types/map3d'

/**
 * 將 3D SceneData 逆向轉換為後端 RCS 規範的 MapDataDto
 * @param sceneData 當前編輯器中的 3D 場景資料
 * @param originalDto 原始後端 DTO (保留 version, context, width, height 等地圖原檔資訊)
 */
export function sceneDataToMapDto(sceneData: SceneData, originalDto?: MapDataDto): MapDataDto {
  const mapCode = sceneData.mapCode

  const pointList: PointDto[] = sceneData.points.map(sp => ({
    pointCode: sp.code,
    pointName: sp.name,
    pointType: sp.type,
    x: Number(sp.position[0].toFixed(2)),
    y: Number(sp.position[2].toFixed(2)),
  }))
  // 直接保留有向路線與限制，不從幾何位置推測鄰接關係。
  const lineList = sceneData.edges.map(line => ({
    ...line,
    robotCarrierState: [...line.robotCarrierState],
    allowedTravelModes: [...line.allowedTravelModes],
  }))

  // 2. 轉換 3D 環境物件 (SceneObject → MapObjectDto)
  const objects: MapObjectDto[] = sceneData.objects.map((so) => {
    const height = so.size.height
    // Three.js 中心點 Y 減去半高 = RCS 底部錨點 posZ
    const posZ = Math.max(0, Number((so.position[1] - height / 2).toFixed(2)))
    const rotationDeg = Number(((so.rotationY * 180) / Math.PI).toFixed(1))

    return {
      id: typeof so.id === 'number' ? so.id : undefined,
      mapCode,
      objectType: so.type,
      posX: Number(so.position[0].toFixed(2)),
      posY: Number(so.position[2].toFixed(2)),
      posZ,
      rotation: ((rotationDeg % 360) + 360) % 360,
      length: Number(so.size.length.toFixed(2)),
      width: Number(so.size.width.toFixed(2)),
      height: Number(so.size.height.toFixed(2)),
      rackLevels: so.rackLevels,
    }
  })

  // 3. 地圖資訊 (保留原有資訊或更新尺寸)
  const baseMap = originalDto?.map ?? {
    mapCode: sceneData.mapCode,
    mapName: sceneData.mapName,
    version: 1,
    isActive: true,
    width: sceneData.floorWidth,
    height: sceneData.floorDepth,
    originX: 0,
    originY: 0,
  }

  return {
    map: {
      ...baseMap,
      width: sceneData.floorWidth,
      height: sceneData.floorDepth,
    },
    pointList,
    lineList,
    objects,
  }
}
