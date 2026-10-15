// ============================================================================
// 檔案名稱：EditorGizmo.tsx
// 模組說明：3D 變形控制軸 (Transform Gizmo) 元件
//           功能：支援平移 (Translate) 與旋轉 (Rotate)，支援網格吸附 (Snap to Grid)
// ============================================================================

import { useEffect, useState, useCallback, useRef } from 'react'
import * as THREE from 'three'
import { TransformControls } from '@react-three/drei'
import type { EditorTool, EditorSettings, SelectedTarget } from '../types/editor'
import type { SceneData } from '../types/map3d'

interface EditorGizmoProps {
  currentTool: EditorTool
  settings: EditorSettings
  selectedTarget: SelectedTarget | null
  sceneData: SceneData
  onUpdatePointPosition: (code: string, newPos: [number, number, number]) => void
  onUpdateObjectTransform: (
    id: number,
    newPos: [number, number, number],
    rotationY: number
  ) => void
  onDraggingChange: (isDragging: boolean) => void
}

export default function EditorGizmo({
  currentTool,
  settings,
  selectedTarget,
  sceneData,
  onUpdatePointPosition,
  onUpdateObjectTransform,
  onDraggingChange,
}: EditorGizmoProps) {
  // 使用 State 作為 Callback Ref，確保 Group 掛載後能正確觸發 TransformControls 綁定
  const [targetGroup, setTargetGroup] = useState<THREE.Group | null>(null)
  const isDraggingRef = useRef(false)

  // 僅在平移或旋轉模式，且有選取目標時啟用 Gizmo
  const isEnabled =
    (currentTool === 'translate' || currentTool === 'rotate') &&
    selectedTarget !== null

  // 取得當前選取目標的實際空間坐標與角度
  const targetTransform = useCallback(() => {
    if (!selectedTarget) return null

    if (selectedTarget.type === 'POINT') {
      const p = sceneData.points.find((pt) => pt.code === selectedTarget.id)
      if (p) return { pos: p.position, rotY: p.headingRad }
    } else if (selectedTarget.type === 'OBJECT') {
      const o = sceneData.objects.find((obj) => obj.id === selectedTarget.id)
      if (o) return { pos: o.position, rotY: o.rotationY }
    }
    return null
  }, [selectedTarget, sceneData])

  // 當選取目標或其坐標變更時，同步到 Gizmo 錨點 (若正在拖曳中則不重設，以免卡頓)
  useEffect(() => {
    if (!targetGroup || isDraggingRef.current) return
    const tf = targetTransform()
    if (!tf) return

    targetGroup.position.set(tf.pos[0], tf.pos[1], tf.pos[2])
    targetGroup.rotation.set(0, tf.rotY, 0)
  }, [targetGroup, targetTransform])

  if (!isEnabled || !selectedTarget) return null

  // 判斷當前變形模式 (平移或旋轉)
  const mode = currentTool === 'rotate' ? 'rotate' : 'translate'

  // 吸附步進數值
  const translationSnap = settings.snapToGrid ? settings.gridStep : undefined
  const rotationSnap = settings.snapToGrid ? Math.PI / 12 : undefined // 15 度吸附

  return (
    <>
      {/* 變形錨點虛擬群組 */}
      <group ref={setTargetGroup} />

      {/* 當 targetGroup 成功建立後，動態掛載 TransformControls */}
      {targetGroup && (
        <TransformControls
          object={targetGroup}
          mode={mode}
          size={0.8}
          translationSnap={translationSnap}
          rotationSnap={rotationSnap}
          showY={mode === 'rotate'} // 平移時鎖定 Y 軸 (僅在地面 X-Z 軸移動)
          onMouseDown={() => {
            isDraggingRef.current = true
            onDraggingChange(true)
          }}
          onMouseUp={() => {
            isDraggingRef.current = false
            onDraggingChange(false)
          }}
          onChange={() => {
            if (!targetGroup || !isDraggingRef.current) return
            const pos = targetGroup.position
            const rot = targetGroup.rotation

            if (selectedTarget.type === 'POINT') {
              // 限制在地面移動，Y 軸固定於 0.05
              onUpdatePointPosition(selectedTarget.id as string, [pos.x, 0.05, pos.z])
            } else if (selectedTarget.type === 'OBJECT') {
              const o = sceneData.objects.find((obj) => obj.id === selectedTarget.id)
              if (o) {
                const fixedY = o.size.height / 2
                onUpdateObjectTransform(
                  selectedTarget.id as number,
                  [pos.x, fixedY, pos.z],
                  rot.y
                )
              }
            }
          }}
        />
      )}
    </>
  )
}
