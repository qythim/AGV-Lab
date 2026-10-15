// ============================================================================
// 檔案名稱：useMapEditorStore.ts
// 模組說明：地圖編輯器全域狀態管理 Hook
//           負責管理當前作用工具、選取目標、網格吸附設定及未儲存變更狀態
// ============================================================================

import { useState, useCallback } from 'react'
import type { EditorTool, SelectedTarget, EditorSettings } from '../types/editor'

/** 預設編輯器設定 */
const DEFAULT_SETTINGS: EditorSettings = {
  snapToGrid: true,
  gridStep: 0.5,
  showLabels: true,
}

export function useMapEditorState() {
  // 當前啟用的工具模式 (預設為選取模式)
  const [currentTool, setCurrentTool] = useState<EditorTool>('select')

  // 當前選取的實體目標 (點位或 3D 物件)
  const [selectedTarget, setSelectedTarget] = useState<SelectedTarget | null>(null)

  // 編輯器環境設定 (網格吸附、標籤顯示等)
  const [settings, setSettings] = useState<EditorSettings>(DEFAULT_SETTINGS)

  // 是否有尚未儲存之修改變更
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState<boolean>(false)

  // 切換選取目標
  const handleSelect = useCallback((target: SelectedTarget | null) => {
    setSelectedTarget(target)
  }, [])

  // 清除當前選取
  const clearSelection = useCallback(() => {
    setSelectedTarget(null)
  }, [])

  // 更新設定選項
  const updateSettings = useCallback((partial: Partial<EditorSettings>) => {
    setSettings((prev) => ({ ...prev, ...partial }))
  }, [])

  // 標記內容已變更
  const markDirty = useCallback(() => {
    setHasUnsavedChanges(true)
  }, [])

  // 重置未儲存狀態 (儲存成功後呼叫)
  const markClean = useCallback(() => {
    setHasUnsavedChanges(false)
  }, [])

  return {
    currentTool,
    setCurrentTool,
    selectedTarget,
    setSelectedTarget: handleSelect,
    clearSelection,
    settings,
    updateSettings,
    hasUnsavedChanges,
    markDirty,
    markClean,
  }
}

export type MapEditorState = ReturnType<typeof useMapEditorState>
