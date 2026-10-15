// ============================================================================
// 檔案名稱：FactoryScene.tsx
// 模組說明：Three.js 3D 倉庫地圖編輯器主場景元件
//           • 載入後端 RCS 地圖資料並提供 3D 即時視覺化
//           • 提供編輯工具列 (選取、平移 Gizmo、旋轉、吸附網格、新增點位、新增物件、刪除、連線)
//           • 點擊地板即可新增點位，支援 5 種 3D 物件快速放置
//           • 支援即時儲存至後端 RCS 資料庫
// ============================================================================

import { Suspense, useEffect, useState, useCallback, useMemo } from 'react'
import { Canvas } from '@react-three/fiber'
import { OrbitControls, Grid } from '@react-three/drei'
import { message } from 'antd'

import { fetchActiveMap, fetchMapByCode, saveMap, type DataSource } from '../../map-management/api/mapApi'
import type { MapDataDto, MapObjectType } from '../../map-management/types/mapDto'
import { mapDtoToSceneData } from '../utils/mapCoordinateMapper'
import { sceneDataToMapDto } from '../utils/sceneDataToMapDto'
import type { SceneData, ScenePoint, SceneObject, SceneEdge } from '../types/map3d'
import { useMapEditorState } from '../hooks/useMapEditorStore'
import EditorToolbar from '../components/EditorToolbar'
import EditorGizmo from '../components/EditorGizmo'

import Rack     from '../objects/Rack'
import Wall     from '../objects/Wall'
import Charger  from '../objects/Charger'
import Station  from '../objects/Station'
import Conveyor from '../objects/Conveyor'
import PathTopology from '../components/PathTopology'

// ─────────────────────────────────────────────────────────────────────────────
// 場景 HUD 覆蓋層 (顯示於畫面左下角，不遮蔽上方工具列)
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
        position: 'absolute', bottom: 16, left: 20, zIndex: 10,
        background: 'rgba(5,16,43,0.85)', border: '1px solid #FF8C00',
        borderRadius: 10, padding: '8px 16px', color: '#FF8C00',
        fontSize: 13, fontFamily: 'monospace',
      }}>
        ⏳ 正在載入地圖資料...
      </div>
    )
  }
  if (!sceneData) return null

  return (
    <div style={{
      position: 'absolute', bottom: 16, left: 20, zIndex: 10,
      background: 'rgba(5,16,43,0.85)', border: '1px solid #1e3a5f',
      borderRadius: 10, padding: '10px 16px', color: '#e2e8f0',
      fontSize: 12, fontFamily: 'monospace', lineHeight: 1.7,
      boxShadow: '0 4px 20px rgba(0,0,0,0.4)',
    }}>
      <div style={{ color: '#FF8C00', fontWeight: 700, fontSize: 13 }}>
        🗺 {sceneData.mapName} ({sceneData.mapCode})
      </div>
      <div>📐 尺寸：{sceneData.floorWidth} × {sceneData.floorDepth} m</div>
      <div>📍 點位：{sceneData.points.length} 個 | 📦 物件：{sceneData.objects.length} 個</div>
      <div style={{ fontSize: 11, marginTop: 2 }}>
        {source === 'api'
          ? <span style={{ color: '#10b981' }}>● RCS 後端資料 (即時同步)</span>
          : <span style={{ color: '#f59e0b' }}>⚠ 本地模擬資料 (離線展示)</span>
        }
      </div>
    </div>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 3D 場景內容 (渲染於 Canvas 內部)
// ─────────────────────────────────────────────────────────────────────────────
interface SceneContentProps {
  sceneData: SceneData
  currentTool: ReturnType<typeof useMapEditorState>['currentTool']
  settings: ReturnType<typeof useMapEditorState>['settings']
  selectedTarget: ReturnType<typeof useMapEditorState>['selectedTarget']
  onSelectTarget: (target: { type: 'POINT' | 'OBJECT'; id: string | number }) => void
  onFloorClick: (point3D: [number, number, number]) => void
  onPointClick: (code: string) => void
  onUpdatePointPosition: (code: string, newPos: [number, number, number]) => void
  onUpdateObjectTransform: (id: number, newPos: [number, number, number], rotY: number) => void
  onDraggingChange: (isDragging: boolean) => void
}

function SceneContent({
  sceneData,
  currentTool,
  settings,
  selectedTarget,
  onSelectTarget,
  onFloorClick,
  onPointClick,
  onUpdatePointPosition,
  onUpdateObjectTransform,
  onDraggingChange,
}: SceneContentProps) {
  const { floorWidth, floorDepth, points, edges, objects } = sceneData

  // 地板中心點
  const floorCenterX = floorWidth / 2
  const floorCenterZ = floorDepth / 2

  return (
    <>
      {/* ── 場景環境 ──────────────────────────────────────────────────── */}
      <color attach="background" args={['#05102b']} />
      <fogExp2 attach="fog" args={['#05102b', 0.018]} />

      {/* 環境光 */}
      <ambientLight intensity={0.65} />

      {/* 主方向光 */}
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

      {/* 主題色點光源 */}
      <pointLight color="#FF8C00" position={[floorCenterX, 8, floorCenterZ]} intensity={25} distance={40} />

      {/* ── 地板與網格 ────────────────────────────────────────────────── */}
      <mesh
        position={[floorCenterX, 0, floorCenterZ]}
        rotation={[-Math.PI / 2, 0, 0]}
        receiveShadow
        onClick={(e) => {
          e.stopPropagation()
          onFloorClick([e.point.x, 0.05, e.point.z])
        }}
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

      {/* ── 拓撲路徑 (支援點位點選) ─────────────────────────────────── */}
      <PathTopology
        points={points}
        edges={edges}
        selectedPointCode={selectedTarget?.type === 'POINT' ? (selectedTarget.id as string) : null}
        onSelectPoint={onPointClick}
      />

      {/* ── 3D 環境物件 (支援點擊選取與游標懸浮提示) ───────────────────── */}
      {objects.map((obj) => {
        const key = obj.id
        const isSelected = selectedTarget?.type === 'OBJECT' && selectedTarget.id === obj.id
        const commonProps = {
          position: obj.position,
          rotationY: obj.rotationY,
          size: obj.size,
        }

        return (
          <group
            key={key}
            onClick={(e) => {
              e.stopPropagation()
              onSelectTarget({ type: 'OBJECT', id: obj.id })
            }}
            onPointerOver={(e) => {
              e.stopPropagation()
              document.body.style.cursor = 'pointer'
            }}
            onPointerOut={() => {
              document.body.style.cursor = 'default'
            }}
          >
            {/* 物件本體渲染 */}
            {obj.type === 'RACK' && <Rack {...commonProps} rackLevels={obj.rackLevels} />}
            {obj.type === 'WALL' && <Wall {...commonProps} />}
            {obj.type === 'CHARGER' && <Charger {...commonProps} />}
            {obj.type === 'STATION' && <Station {...commonProps} />}
            {obj.type === 'CONVEYOR' && <Conveyor {...commonProps} />}

            {/* 選取光環外框 (選取中之物件顯示青藍色外框標記) */}
            {isSelected && (
              <mesh position={obj.position} rotation={[0, obj.rotationY, 0]}>
                <boxGeometry
                  args={[
                    obj.size.length + 0.15,
                    obj.size.height + 0.15,
                    obj.size.width + 0.15,
                  ]}
                />
                <meshBasicMaterial color="#38bdf8" wireframe />
              </mesh>
            )}
          </group>
        )
      })}

      {/* ── 3D 變形控制器 (Transform Gizmo) ────────────────────────────── */}
      <EditorGizmo
        currentTool={currentTool}
        settings={settings}
        selectedTarget={selectedTarget}
        sceneData={sceneData}
        onUpdatePointPosition={onUpdatePointPosition}
        onUpdateObjectTransform={onUpdateObjectTransform}
        onDraggingChange={onDraggingChange}
      />
    </>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// 主場景元件 (包含 Canvas、Toolbar、HUD)
// ─────────────────────────────────────────────────────────────────────────────
interface FactorySceneProps {
  /** 指定地圖編號；若未傳入則自動載入後端啟用中之地圖 */
  mapCode?: string
  /** 返回地圖清單頁面回呼 */
  onBack?: () => void
}

export default function FactoryScene({ mapCode, onBack }: FactorySceneProps) {
  const [sceneData, setSceneData] = useState<SceneData | null>(null)
  const [rawDto, setRawDto] = useState<MapDataDto | undefined>()
  const [source, setSource] = useState<DataSource | null>(null)
  const [loading, setLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [isDraggingGizmo, setIsDraggingGizmo] = useState(false)
  const [pendingEdgeStart, setPendingEdgeStart] = useState<string | null>(null)

  // 編輯器狀態管理
  const {
    currentTool,
    setCurrentTool,
    selectedTarget,
    setSelectedTarget,
    clearSelection,
    settings,
    updateSettings,
    hasUnsavedChanges,
    markDirty,
    markClean,
  } = useMapEditorState()

  // 載入地圖資料
  useEffect(() => {
    const loader = mapCode ? fetchMapByCode(mapCode) : fetchActiveMap()
    loader.then(({ data, source }) => {
      setRawDto(data)
      setSceneData(mapDtoToSceneData(data))
      setSource(source)
      setLoading(false)
      markClean()
    })
  }, [mapCode, markClean])

  // 選取點位或物件時，自動切換至平移工具顯示 Gizmo
  const handleSelectTarget = useCallback(
    (target: { type: 'POINT' | 'OBJECT'; id: string | number }) => {
      setSelectedTarget(target)
      if (currentTool === 'select') {
        setCurrentTool('translate')
      }
      message.info(`已選取 ${target.type === 'POINT' ? '路徑點' : '3D 物件'}：${target.id}，變形軸已就緒`)
    },
    [currentTool, setCurrentTool, setSelectedTarget]
  )

  // 點擊路徑點 (處理連線邊或選取)
  const handlePointClick = useCallback(
    (code: string) => {
      if (currentTool === 'connect-edge') {
        if (!pendingEdgeStart) {
          setPendingEdgeStart(code)
          message.info(`🔗 已鎖定連線起點：${code}，請點擊第二個點完成連線`)
        } else if (pendingEdgeStart === code) {
          message.warning('⚠️ 起點與終點不能為同一個路徑點')
        } else {
          // 建立連線邊
          setSceneData((prev) => {
            if (!prev) return prev
            const p1 = prev.points.find((p) => p.code === pendingEdgeStart)
            const p2 = prev.points.find((p) => p.code === code)
            if (!p1 || !p2) return prev

            // 檢查是否已存在連線
            const edgeExists = prev.edges.some(
              (e) =>
                (Math.hypot(e.from[0] - p1.position[0], e.from[2] - p1.position[2]) < 0.01 &&
                 Math.hypot(e.to[0] - p2.position[0], e.to[2] - p2.position[2]) < 0.01) ||
                (Math.hypot(e.from[0] - p2.position[0], e.from[2] - p2.position[2]) < 0.01 &&
                 Math.hypot(e.to[0] - p1.position[0], e.to[2] - p1.position[2]) < 0.01)
            )

            if (edgeExists) {
              message.warning(`路徑 ${pendingEdgeStart} ↔ ${code} 已經存在`)
              return prev
            }

            const newEdge: SceneEdge = { from: p1.position, to: p2.position }
            message.success(`🎉 成功建立路徑連線：${pendingEdgeStart} ↔ ${code}`)
            return { ...prev, edges: [...prev.edges, newEdge] }
          })
          setPendingEdgeStart(null)
          markDirty()
        }
        return
      }

      handleSelectTarget({ type: 'POINT', id: code })
    },
    [currentTool, pendingEdgeStart, handleSelectTarget, markDirty]
  )

  // 點擊地面 (新增點位或取消選取)
  const handleFloorClick = useCallback(
    (point3D: [number, number, number]) => {
      if (currentTool === 'add-node') {
        // 吸附網格計算
        const snapX = settings.snapToGrid
          ? Math.round(point3D[0] / settings.gridStep) * settings.gridStep
          : Number(point3D[0].toFixed(2))
        const snapZ = settings.snapToGrid
          ? Math.round(point3D[2] / settings.gridStep) * settings.gridStep
          : Number(point3D[2].toFixed(2))

        setSceneData((prev) => {
          if (!prev) return prev
          const nextIndex = prev.points.length + 1
          const newCode = `P_${String(nextIndex).padStart(2, '0')}`
          const newPoint: ScenePoint = {
            code: newCode,
            position: [snapX, 0.05, snapZ],
            type: 'NORMAL',
            headingRad: 0,
            isDisabled: false,
          }

          message.success(`📍 成功在 [${snapX}, ${snapZ}] 新增路徑點：${newCode}`)
          setSelectedTarget({ type: 'POINT', id: newCode })
          setCurrentTool('translate')
          return { ...prev, points: [...prev.points, newPoint] }
        })
        markDirty()
        return
      }

      // 一般選取模式下點擊空白地面取消選取
      if (currentTool === 'select' || currentTool === 'translate' || currentTool === 'rotate') {
        clearSelection()
      }
    },
    [currentTool, settings, setSelectedTarget, setCurrentTool, clearSelection, markDirty]
  )

  // 新增 3D 環境物件
  const handleAddObject = useCallback(
    (type: MapObjectType) => {
      if (!sceneData) return

      const floorCenterX = Number((sceneData.floorWidth / 2).toFixed(1))
      const floorCenterZ = Number((sceneData.floorDepth / 2).toFixed(1))
      const newId = Date.now()

      let defaultSize = { length: 3, width: 1.5, height: 3.5 }
      let rackLevels = 1

      switch (type) {
        case 'RACK':
          defaultSize = { length: 3, width: 1.5, height: 3.5 }
          rackLevels = 3
          break
        case 'CHARGER':
          defaultSize = { length: 1.2, width: 1.0, height: 1.8 }
          break
        case 'STATION':
          defaultSize = { length: 2.5, width: 1.5, height: 1.2 }
          break
        case 'WALL':
          defaultSize = { length: 8, width: 0.8, height: 3.5 }
          break
        case 'CONVEYOR':
          defaultSize = { length: 6, width: 1.2, height: 0.9 }
          break
      }

      const newObject: SceneObject = {
        id: newId,
        type,
        position: [floorCenterX, defaultSize.height / 2, floorCenterZ],
        rotationY: 0,
        size: defaultSize,
        rackLevels,
      }

      setSceneData((prev) => (prev ? { ...prev, objects: [...prev.objects, newObject] } : prev))
      setSelectedTarget({ type: 'OBJECT', id: newId })
      setCurrentTool('translate')
      markDirty()
      message.success(`📦 已在場景中央新增 ${type} 物件，並已開啟平移軸方便調整！`)
    },
    [sceneData, setSelectedTarget, setCurrentTool, markDirty]
  )

  // 刪除選取之目標
  const handleDeleteSelected = useCallback(() => {
    if (!selectedTarget) {
      message.warning('💡 請先點選地圖上欲刪除的路徑點或 3D 物件！')
      return
    }

    if (selectedTarget.type === 'POINT') {
      const code = selectedTarget.id as string
      setSceneData((prev) => {
        if (!prev) return prev
        const targetPt = prev.points.find((p) => p.code === code)
        if (!targetPt) return prev

        // 移除點位並同步移除相關的連線邊
        const updatedPoints = prev.points.filter((p) => p.code !== code)
        const updatedEdges = prev.edges.filter(
          (e) =>
            Math.hypot(e.from[0] - targetPt.position[0], e.from[2] - targetPt.position[2]) >= 0.01 &&
            Math.hypot(e.to[0] - targetPt.position[0], e.to[2] - targetPt.position[2]) >= 0.01
        )
        return { ...prev, points: updatedPoints, edges: updatedEdges }
      })
      clearSelection()
      markDirty()
      message.success(`🗑️ 已刪除路徑點 ${code}`)
    } else if (selectedTarget.type === 'OBJECT') {
      const id = selectedTarget.id as number
      setSceneData((prev) => (prev ? { ...prev, objects: prev.objects.filter((o) => o.id !== id) } : prev))
      clearSelection()
      markDirty()
      message.success('🗑️ 已刪除選取的 3D 環境物件')
    }
  }, [selectedTarget, clearSelection, markDirty])

  // 更新路徑點坐標 (即時同步連線邊)
  const handleUpdatePointPosition = useCallback(
    (code: string, newPos: [number, number, number]) => {
      setSceneData((prev) => {
        if (!prev) return prev
        const oldPt = prev.points.find((p) => p.code === code)
        if (!oldPt) return prev

        const updatedPoints = prev.points.map((p) =>
          p.code === code ? { ...p, position: newPos } : p
        )

        // 同步更新連線邊的頂點坐標
        const updatedEdges = prev.edges.map((edge) => {
          const isFrom = Math.hypot(edge.from[0] - oldPt.position[0], edge.from[2] - oldPt.position[2]) < 0.05
          const isTo   = Math.hypot(edge.to[0]   - oldPt.position[0], edge.to[2]   - oldPt.position[2]) < 0.05

          if (isFrom) return { ...edge, from: newPos }
          if (isTo)   return { ...edge, to:   newPos }
          return edge
        })

        return { ...prev, points: updatedPoints, edges: updatedEdges }
      })
      markDirty()
    },
    [markDirty]
  )

  // 更新 3D 環境物件位置與旋轉
  const handleUpdateObjectTransform = useCallback(
    (id: number, newPos: [number, number, number], rotY: number) => {
      setSceneData((prev) => {
        if (!prev) return prev
        const updatedObjects = prev.objects.map((o) =>
          o.id === id ? { ...o, position: newPos, rotationY: rotY } : o
        )
        return { ...prev, objects: updatedObjects }
      })
      markDirty()
    },
    [markDirty]
  )

  // 儲存地圖變更至後端 RCS
  const handleSaveMap = useCallback(async () => {
    if (!sceneData) return
    setIsSaving(true)
    try {
      const dto = sceneDataToMapDto(sceneData, rawDto)
      await saveMap(dto)
      setRawDto(dto)
      markClean()
      message.success(`🎉 地圖「${sceneData.mapName}」變更已成功永久儲存至後端 RCS 資料庫！`)
    } catch {
      message.error('儲存失敗：後端連線中斷或伺服器回應異常')
    } finally {
      setIsSaving(false)
    }
  }, [sceneData, rawDto, markClean])

  // 切換網格吸附
  const handleToggleSnap = useCallback(() => {
    updateSettings({ snapToGrid: !settings.snapToGrid })
    message.info(settings.snapToGrid ? '網格吸附已關閉' : '網格吸附已啟用 (步進 0.5m)')
  }, [settings.snapToGrid, updateSettings])

  // 計算初始相機位置
  const camDistance = useMemo(() => {
    return sceneData ? Math.max(sceneData.floorWidth, sceneData.floorDepth) * 0.9 : 30
  }, [sceneData])

  const camX = sceneData ? sceneData.floorWidth / 2 : 15
  const camZ = sceneData ? sceneData.floorDepth / 2 : 15

  return (
    <div style={{ position: 'relative', width: '100%', height: '100%' }}>
      {/* ── 懸浮編輯工具列 ────────────────────────────────────────────── */}
      <EditorToolbar
        currentTool={currentTool}
        onSelectTool={setCurrentTool}
        selectedTarget={selectedTarget}
        settings={settings}
        onToggleSnap={handleToggleSnap}
        hasUnsavedChanges={hasUnsavedChanges}
        onSave={handleSaveMap}
        onBack={onBack ?? (() => window.history.back())}
        onAddObject={handleAddObject}
        onDeleteSelected={handleDeleteSelected}
        isSaving={isSaving}
      />

      {/* ── HUD 覆蓋層 (畫面左下角) ──────────────────────────────────── */}
      <SceneHud sceneData={sceneData} source={source} loading={loading} />

      {/* ── Three.js Canvas ───────────────────────────────────────────── */}
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
          {sceneData && (
            <SceneContent
              sceneData={sceneData}
              currentTool={currentTool}
              settings={settings}
              selectedTarget={selectedTarget}
              onSelectTarget={handleSelectTarget}
              onFloorClick={handleFloorClick}
              onPointClick={handlePointClick}
              onUpdatePointPosition={handleUpdatePointPosition}
              onUpdateObjectTransform={handleUpdateObjectTransform}
              onDraggingChange={setIsDraggingGizmo}
            />
          )}
        </Suspense>

        {/* 攝影機控制器 (拖曳 Gizmo 時暫時停用，避免視角轉動衝突) */}
        <OrbitControls
          target={[camX, 0, camZ]}
          enableDamping
          dampingFactor={0.05}
          maxPolarAngle={Math.PI / 2.05}
          enabled={!isDraggingGizmo}
        />
      </Canvas>
    </div>
  )
}
