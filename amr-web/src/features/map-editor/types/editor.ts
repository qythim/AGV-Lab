// ============================================================================
// 檔案名稱：editor.ts
// 模組說明：地圖編輯器 (Map Editor) 互動狀態與工具類型定義
// ============================================================================

/** 編輯器工作模式與工具列功能列舉 */
export type EditorTool =
  | 'select'        // 選取與檢視模式
  | 'translate'     // 平移拖曳模式 (Gizmo Move)
  | 'rotate'        // 旋轉模式 (Gizmo Rotate)
  | 'add-node'      // 新增路徑點模式 (點擊地面放置點位)
  | 'connect-edge'  // 建立連線邊模式 (依序點擊兩個點建立連線)
  | 'add-object'    // 放置 3D 環境物件模式
  | 'delete'        // 刪除選取物件模式

/** 當前選取的實體類型 */
export type SelectionType = 'POINT' | 'OBJECT' | null

/** 當前選取的目標資訊 */
export interface SelectedTarget {
  /** 目標種類：路徑點 (POINT) 或環境物件 (OBJECT) */
  type: 'POINT' | 'OBJECT'
  /** 點位代碼 (例：'P_01') 或物件資料庫編號 (例：5) */
  id: string | number
}

/** 編輯器設定選項 */
export interface EditorSettings {
  /** 是否開啟網格吸附 (Snap to Grid) */
  snapToGrid: boolean
  /** 網格吸附步進間距 (公尺，預設 0.5m) */
  gridStep: number
  /** 是否顯示路徑點標籤 */
  showLabels: boolean
}
