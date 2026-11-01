// ============================================================================
// 檔案名稱：PathTopology.tsx
// 模組說明：AGV 拓撲路徑渲染元件
//           功能：繪製路徑點 (Node) 與路徑連線 (Edge)
//           點位依功能類型顯示不同顏色以利辨識
// ============================================================================

import { useEffect, useMemo } from 'react'
import { Html } from '@react-three/drei'
import * as THREE from 'three'
import type { ScenePoint, SceneEdge } from '../types/map3d'

/** 路徑拓撲元件屬性 */
interface PathTopologyProps {
  points: ScenePoint[]
  edges: SceneEdge[]
  selectedPointCode?: string | null
  selectedLineCode?: string | null
  showLabels?: boolean
  onSelectPoint?: (code: string) => void
  onSelectLine?: (code: string) => void
}

/** 各功能類型之路徑點顏色定義 */
const POINT_COLORS: Record<string, string> = {
  NORMAL:  '#64748b', // 一般通道點：灰藍色
  RACK:    '#3b82f6', // 貨架點：藍色
  STATION: '#FF8C00', // 工作站點：主題橙色
  CHARGER: '#10b981', // 充電點：綠色
}

/** 路徑點圓盤半徑 */
const POINT_RADIUS = 0.18

/** 路徑連線的垂直高度 (略高於地板防止 Z-fighting) */
const EDGE_Y = 0.04

export default function PathTopology({
  points,
  edges,
  selectedPointCode,
  selectedLineCode,
  showLabels = true,
  onSelectPoint,
  onSelectLine,
}: PathTopologyProps) {
  // ── 路徑連線幾何體 (LineSegments) ──────────────────────────────────────────
  // 將所有邊一次性合併到單一 BufferGeometry 以最佳化渲染效能
  const { lineGeometry, lineCodes } = useMemo(() => {
    const positions: number[] = []
    const colors: number[] = []
    const lineCodes: string[] = []
    const nodeByCode = new Map(points.map(point => [point.code, point]))
    for (const edge of edges) {
      const from = nodeByCode.get(edge.startPointCode)?.position
      const to = nodeByCode.get(edge.endPointCode)?.position
      if (!from || !to) continue
      const dx = to[0] - from[0], dz = to[2] - from[2]
      const distance = Math.hypot(dx, dz)
      if (distance === 0) continue
      const ux = dx / distance, uz = dz / distance
      // 反向路線分別偏移到行進方向左側，避免箭頭重疊。
      const offset = Math.min(0.08, distance * 0.04)
      const ox = -uz * offset, oz = ux * offset
      positions.push(from[0] + ox, EDGE_Y, from[2] + oz, to[0] + ox, EDGE_Y, to[2] + oz)
      const x = from[0] + dx * 0.6 + ox, z = from[2] + dz * 0.6 + oz
      const size = Math.min(0.25, distance * 0.15)
      positions.push(x, EDGE_Y, z, x - ux * size - uz * size * 0.5, EDGE_Y, z - uz * size + ux * size * 0.5)
      positions.push(x, EDGE_Y, z, x - ux * size + uz * size * 0.5, EDGE_Y, z - uz * size - ux * size * 0.5)
      lineCodes.push(edge.lineCode)
      const color = new THREE.Color(edge.lineCode === selectedLineCode ? '#38bdf8' : '#1e40af')
      for (let vertex = 0; vertex < 6; vertex++) colors.push(color.r, color.g, color.b)
    }
    const geo = new THREE.BufferGeometry()
    geo.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
    geo.setAttribute('color', new THREE.Float32BufferAttribute(colors, 3))
    return { lineGeometry: geo, lineCodes }
  }, [edges, points, selectedLineCode])

  useEffect(() => () => lineGeometry.dispose(), [lineGeometry])

  return (
    <group>
      {/* 路徑連線群 (一次繪製所有邊，效能最佳) */}
      <lineSegments geometry={lineGeometry}
        raycast={function (this: THREE.LineSegments, raycaster, intersections) {
          if (!onSelectLine) return
          const threshold = raycaster.params.Line.threshold
          try {
            raycaster.params.Line.threshold = 0.08
            THREE.LineSegments.prototype.raycast.call(this, raycaster, intersections)
          } finally {
            raycaster.params.Line.threshold = threshold
          }
        }}
        onClick={event => {
          if (!onSelectLine || event.index == null) return
          // 每條路線包含主線與兩段箭頭，共六個頂點。
          const code = lineCodes[Math.floor(event.index / 6)]
          if (!code) return
          event.stopPropagation()
          onSelectLine(code)
        }}
        onPointerOver={event => {
          if (!onSelectLine) return
          event.stopPropagation()
          document.body.style.cursor = 'pointer'
        }}
        onPointerOut={() => { document.body.style.cursor = 'default' }}
      >
        <lineBasicMaterial vertexColors linewidth={1} />
      </lineSegments>

      {/* 各路徑點標記 */}
      {points.map((point) => {
        const isSelected = selectedPointCode === point.code
        const color = POINT_COLORS[point.type] ?? POINT_COLORS.NORMAL
        const label = point.name?.trim() || point.code

        return (
          <group
            key={point.code}
            position={point.position}
            onClick={(e) => {
              e.stopPropagation()
              onSelectPoint?.(point.code)
            }}
          >
            {/* 圓盤底座 */}
            <mesh
              rotation={[-Math.PI / 2, 0, 0]}
              onPointerOver={(e) => { e.stopPropagation(); document.body.style.cursor = 'pointer' }}
              onPointerOut={() => { document.body.style.cursor = 'default' }}
            >
              <circleGeometry args={[POINT_RADIUS, 16]} />
              <meshBasicMaterial color={color} />
            </mesh>

            {showLabels && (
              <Html
                transform
                sprite
                distanceFactor={7}
                position={[0, 0.08, 0]}
                style={{ pointerEvents: 'none', userSelect: 'none' }}
              >
                <span
                  title={label}
                  style={{
                    display: 'block',
                    maxWidth: 84,
                    overflow: 'hidden',
                    color: isSelected ? '#e0f2fe' : '#94a3b8',
                    fontFamily: 'monospace',
                    fontSize: 10,
                    fontWeight: isSelected ? 700 : 500,
                    lineHeight: 1.2,
                    opacity: isSelected ? 1 : 0.78,
                    textAlign: 'center',
                    textOverflow: 'ellipsis',
                    textShadow: '0 1px 2px #05102b',
                    transform: 'translate(-50%, -135%)',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {label}
                </span>
              </Html>
            )}

            {/* 外框環 (增加辨識度) */}
            <mesh rotation={[-Math.PI / 2, 0, 0]}>
              <ringGeometry args={[POINT_RADIUS, POINT_RADIUS + 0.05, 16]} />
              <meshBasicMaterial color="#ffffff" transparent opacity={0.6} />
            </mesh>

            {/* 隱形擴大點擊熱區 (半徑 0.5m 圓柱，確保任何視角皆能輕鬆點中) */}
            <mesh
              visible={false}
              position={[0, 0.2, 0]}
              onPointerOver={(e) => { e.stopPropagation(); document.body.style.cursor = 'pointer' }}
              onPointerOut={() => { document.body.style.cursor = 'default' }}
            >
              <cylinderGeometry args={[0.5, 0.5, 0.4, 12]} />
            </mesh>

            {/* 選取光環 (當前被點選時顯示亮藍色外圈) */}
            {isSelected && (
              <mesh rotation={[-Math.PI / 2, 0, 0]} renderOrder={1000}>
                <ringGeometry args={[POINT_RADIUS + 0.08, POINT_RADIUS + 0.18, 24]} />
                <meshBasicMaterial color="#38bdf8" transparent opacity={0.9} depthTest={false} depthWrite={false} />
              </mesh>
            )}

            {/* 充電站或工作站加入點光源強調 */}
            {(point.type === 'CHARGER' || point.type === 'STATION') && (
              <pointLight color={color} intensity={4} distance={3} position={[0, 0.5, 0]} />
            )}
          </group>
        )
      })}
    </group>
  )
}
