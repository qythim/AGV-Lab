// ============================================================================
// 檔案名稱：App.tsx
// 模組說明：應用程式根元件，負責頁面層級的路由切換
//           頁面：地圖管理 (Ant Design) ↔ 3D 場景檢視器 (Three.js)
// ============================================================================

import { useState } from 'react'
import MapManagementPage from './features/map-management/pages/MapManagementPage'
import FactoryScene from './features/map-editor/scenes/FactoryScene'

/** 應用程式頁面類型 */
type AppPage = 'management' | '3d-viewer'

export default function App() {
  // 當前顯示頁面 (預設進入地圖管理介面)
  const [page, setPage] = useState<AppPage>('management')
  // 傳遞至 3D 場景的指定地圖編號 (undefined 則載入 active 地圖)
  const [targetMapCode, setTargetMapCode] = useState<string | undefined>()

  // 切換至 3D 檢視器
  const handleOpenViewer = (mapCode?: string) => {
    setTargetMapCode(mapCode)
    setPage('3d-viewer')
  }

  // 返回地圖管理頁面
  const handleBackToManagement = () => {
    setPage('management')
  }

  if (page === '3d-viewer') {
    return (
      <div style={{ width: '100%', height: '100vh', position: 'relative' }}>
        {/* 返回按鈕 */}
        <button
          onClick={handleBackToManagement}
          style={{
            position: 'absolute',
            top: 12,
            right: 12,
            zIndex: 100,
            background: 'rgba(5,16,43,0.9)',
            border: '1px solid #FF8C00',
            color: '#FF8C00',
            padding: '6px 16px',
            borderRadius: 6,
            cursor: 'pointer',
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          ← 返回管理介面
        </button>
        {/* 3D 場景 */}
        <FactoryScene mapCode={targetMapCode} />
      </div>
    )
  }

  return (
    <MapManagementPage onOpenViewer={handleOpenViewer} />
  )
}