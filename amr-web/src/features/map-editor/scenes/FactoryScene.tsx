// ============================================================================
// 檔案名稱：FactoryScene.tsx
// 模組說明：Three.js 3D 倉庫場景主元件
//           • 啟動時向 RCS 後端取得 active 地圖資料
//           • 後端離線時自動切換至本地模擬資料 (Mock Fallback)
//           • 依 JSON 資料動態渲染地板、路徑拓撲、3D 環境物件
// ============================================================================

import { Suspense, useEffect, useState } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Grid } from '@react-three/drei'

import { fetchActiveMap, fetchMapByCode, type DataSource } from '../../map-management/api/mapApi'
import { mapDtoToSceneData } from '../utils/mapCoordinateMapper'
import type { SceneData } from '../types/map3d'

import Rack     from '../objects/Rack'
import Wall     from '../objects/Wall'
import Charger  from '../objects/Charger'
import Station  from '../objects/Station'
import Conveyor from '../objects/Conveyor'
import PathTopology from '../components/PathTopology'

// ─────────────────────────────────────────────────────────────────────────────
// 場景 HUD 覆蓋層 (純 HTML，疊加在 Canvas 上方)
// ─────────────────────────────────────────────────────────────────────────────
interface SceneHudProps {
  sceneData: SceneData | null
  source: DataSource | null
  loading: boolean
}

function SceneHud({ sceneData, source, loading }: SceneHudProps) {
  if (loading) {
    return (
      <div style={{
        position: 'absolute', top: 12, left: 12, zIndex: 10,
        background: 'rgba(5,16,43,0.85)', border: '1px solid #FF8C00',
        borderRadius: 8, padding: '8px 14px', color: '#FF8C00',
        fontSize: 13, fontFamily: 'monospace',
      }}>
        ⏳ 正在載入地圖資料...
      </div>
    )
  }
  if (!sceneData) return null

  return (
    <div style={{
      position: 'absolute', top: 12, left: 12, zIndex: 10,
      background: 'rgba(5,16,43,0.85)', border: '1px solid #FF8C00',
      borderRadius: 8, padding: '10px 16px', color: '#e2e8f0',
      fontSize: 12, fontFamily: 'monospace', lineHeight: 1.7,
    }}>
      {/* 地圖名稱 */}
      <div style={{ color: '#FF8C00', fontWeight: 700, fontSize: 14, marginBottom: 4 }}>
        🗺 {sceneData.mapName}
      </div>
      {/* 地圖基礎資訊 */}
      <div>📐 地板：{sceneData.floorWidth} × {sceneData.floorDepth} m</div>
      <div>📍 路徑點：{sceneData.points.length} 個</div>
      <div>📦 場景物件：{sceneData.objects.length} 個</div>
      {/* 資料來源標示 */}
      <div style={{ marginTop: 6, fontSize: 11 }}>
        {source === 'api'
          ? <span style={{ color: '#10b981' }}>● 即時後端資料 (RCS API)</span>
          : <span style={{ color: '#f59e0b' }}>⚠ 本地模擬資料 (Mock)</span>
        }
      </div>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 3D 場景內容 (渲染於 Canvas 內部)
// ─────────────────────────────────────────────────────────────────────────────
function SceneContent({ sceneData }: { sceneData: SceneData }) {
  const { floorWidth, floorDepth, points, edges, objects } = sceneData

  // 地板中心點 (地圖原點設定在場景中心)
  const floorCenterX = floorWidth / 2
  const floorCenterZ = floorDepth / 2

  return (
    <>
      {/* ── 場景環境 ──────────────────────────────────────────────────── */}
      <color attach="background" args={['#05102b']} />
      <fogExp2 attach="fog" args={['#05102b', 0.018]} />

      {/* 環境光 */}
      <ambientLight intensity={0.6} />

      {/* 主方向光 (產生陰影) */}
      <directionalLight
        position={[floorWidth * 0.6, 30, floorDepth * 0.4]}
        intensity={1.6}
        castShadow
        shadow-mapSize={[2048, 2048]}
        shadow-camera-near={1}
        shadow-camera-far={120}
        shadow-camera-left={-floorWidth}
        shadow-camera-right={floorWidth}
        shadow-camera-top={floorDepth}
        shadow-camera-bottom={-floorDepth}
      />

      {/* 場景主題色點光源 */}
      <pointLight color="#FF8C00" position={[floorCenterX, 8, floorCenterZ]} intensity={30} distance={40} />

      {/* ── 地板與網格 ──────────────────────────────────────────────────── */}
      <mesh
        position={[floorCenterX, 0, floorCenterZ]}
        rotation={[-Math.PI / 2, 0, 0]}
        receiveShadow
      >
        <planeGeometry args={[floorWidth, floorDepth]} />
        <meshStandardMaterial color="#0d1b38" roughness={0.5} metalness={0.15} />
      </mesh>

      {/* 網格線疊加 */}
      <Grid
        args={[floorWidth, floorDepth]}
        position={[floorCenterX, 0.01, floorCenterZ]}
        sectionColor="#1e3a5f"
        cellColor="#0f2547"
        sectionSize={5}
        cellSize={1}
      />

      {/* ── 拓撲路徑 ──────────────────────────────────────────────────── */}
      <PathTopology points={points} edges={edges} />

      {/* ── 3D 環境物件 (依 objectType 選擇對應元件) ──────────────────── */}
      {objects.map((obj) => {
        const key = obj.id
        const commonProps = {
          position: obj.position as [number, number, number],
          rotationY: obj.rotationY,
          size: obj.size,
        }

        switch (obj.type) {
          case 'RACK':
            return <Rack key={key} {...commonProps} rackLevels={obj.rackLevels} />
          case 'WALL':
            return <Wall key={key} {...commonProps} />
          case 'CHARGER':
            return <Charger key={key} {...commonProps} />
          case 'STATION':
            return <Station key={key} {...commonProps} />
          case 'CONVEYOR':
            return <Conveyor key={key} {...commonProps} />
          default:
            return null
        }
      })}

      {/* ── 攝影機控制 ──────────────────────────────────────────────────── */}
      <OrbitControls
        target={[floorCenterX, 0, floorCenterZ]}
        enableDamping
        dampingFactor={0.05}
        maxPolarAngle={Math.PI / 2.05}
      />
    </>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 主場景元件
// ─────────────────────────────────────────────────────────────────────────────
/** FactoryScene 元件屬性 */
interface FactorySceneProps {
  /** 指定地圖編號；若未傳入則自動載入後端啟用中之地圖 */
  mapCode?: string
}

export default function FactoryScene({ mapCode }: FactorySceneProps = {}) {
  const [sceneData, setSceneData] = useState<SceneData | null>(null)
  const [source, setSource] = useState<DataSource | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // 根據是否傳入 mapCode 決定呼叫哪個 API
    const loader = mapCode
      ? fetchMapByCode(mapCode)
      : fetchActiveMap()
    loader.then(({ data, source }) => {
      setSceneData(mapDtoToSceneData(data))
      setSource(source)
      setLoading(false)
    })
  }, [mapCode])

  // 計算初始相機位置：依地圖尺寸動態調整視角距離
  const camDistance = sceneData
    ? Math.max(sceneData.floorWidth, sceneData.floorDepth) * 0.9
    : 30

  const camX = sceneData ? sceneData.floorWidth / 2 : 15
  const camZ = sceneData ? sceneData.floorDepth / 2 : 15

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      {/* HUD 覆蓋層 (即時資訊顯示) */}
      <SceneHud sceneData={sceneData} source={source} loading={loading} />

      {/* Three.js Canvas */}
      <Canvas
        camera={{
          position: [camX + camDistance * 0.6, camDistance * 0.7, camZ + camDistance * 0.7],
          fov: 45,
          near: 0.1,
          far: 500,
        }}
        shadows
        gl={{ antialias: true }}
        style={{ background: '#05102b' }}
      >
        <Suspense fallback={null}>
          {sceneData && <SceneContent sceneData={sceneData} />}
        </Suspense>
      </Canvas>
    </div>
  )
}
