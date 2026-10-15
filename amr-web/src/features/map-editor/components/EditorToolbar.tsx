// ============================================================================
// 檔案名稱：EditorToolbar.tsx
// 模組說明：地圖編輯器懸浮工具列元件 (Ant Design 現代風格)
//           功能：切換編輯工具 (選取/平移/旋轉/新增/連線)、網格吸附開關、新增物件選單、儲存地圖
// ============================================================================

import { Button, Tooltip, Divider, Space, Badge, Dropdown, message } from 'antd'
import type { MenuProps } from 'antd'
import {
  AimOutlined,
  ArrowsAltOutlined,
  RotateRightOutlined,
  PlusCircleOutlined,
  BranchesOutlined,
  AppstoreAddOutlined,
  DeleteOutlined,
  SaveOutlined,
  ArrowLeftOutlined,
  AppstoreOutlined,
  TableOutlined,
  ThunderboltOutlined,
  DesktopOutlined,
  BorderOutlined,
  SwapOutlined,
} from '@ant-design/icons'
import type { EditorTool, EditorSettings, SelectedTarget } from '../types/editor'
import type { MapObjectType } from '../../map-management/types/mapDto'

interface EditorToolbarProps {
  currentTool: EditorTool
  onSelectTool: (tool: EditorTool) => void
  selectedTarget: SelectedTarget | null
  settings: EditorSettings
  onToggleSnap: () => void
  hasUnsavedChanges: boolean
  onSave: () => void
  onBack: () => void
  onAddObject: (type: MapObjectType) => void
  onDeleteSelected: () => void
  isSaving?: boolean
}

/** 主題色彩常數 */
const COLOR_PRIMARY = '#FF8C00'
const COLOR_BG_BAR  = 'rgba(5, 16, 43, 0.92)'
const COLOR_BORDER  = '#1e3a5f'

export default function EditorToolbar({
  currentTool,
  onSelectTool,
  selectedTarget,
  settings,
  onToggleSnap,
  hasUnsavedChanges,
  onSave,
  onBack,
  onAddObject,
  onDeleteSelected,
  isSaving = false,
}: EditorToolbarProps) {

  // 新增 3D 環境物件之下拉選單項目
  const objectMenuItems: MenuProps['items'] = [
    {
      key: 'RACK',
      icon: <TableOutlined style={{ color: '#3b82f6' }} />,
      label: '新增貨架 (RACK)',
      onClick: () => onAddObject('RACK'),
    },
    {
      key: 'CHARGER',
      icon: <ThunderboltOutlined style={{ color: '#10b981' }} />,
      label: '新增充電站 (CHARGER)',
      onClick: () => onAddObject('CHARGER'),
    },
    {
      key: 'STATION',
      icon: <DesktopOutlined style={{ color: COLOR_PRIMARY }} />,
      label: '新增工作站 (STATION)',
      onClick: () => onAddObject('STATION'),
    },
    {
      key: 'WALL',
      icon: <BorderOutlined style={{ color: '#64748b' }} />,
      label: '新增圍牆 (WALL)',
      onClick: () => onAddObject('WALL'),
    },
    {
      key: 'CONVEYOR',
      icon: <SwapOutlined style={{ color: '#a855f7' }} />,
      label: '新增輸送帶 (CONVEYOR)',
      onClick: () => onAddObject('CONVEYOR'),
    },
  ]

  // 處理工具切換並給予即時操作提示
  const handleToolClick = (tool: EditorTool) => {
    onSelectTool(tool)
    switch (tool) {
      case 'select':
        message.info('🎯 選取模式：點選地圖上的路徑點或 3D 物件即可檢視或調整')
        break
      case 'translate':
        if (!selectedTarget) {
          message.warning('💡 請先點選地圖上的路徑點或 3D 物件，即可顯示變形軸進行拖曳！')
        } else {
          message.info('↔️ 平移模式：拖曳紅色(X)或藍色(Z)箭頭即可移動位置')
        }
        break
      case 'rotate':
        if (!selectedTarget) {
          message.warning('💡 請先點選一個 3D 物件，即可顯示旋轉軸進行角度調整！')
        } else {
          message.info('🔄 旋轉模式：拖曳圓環即可旋轉物件方向')
        }
        break
      case 'add-node':
        message.info('📍 新增點位模式：請點擊地表網格任意位置，即可建立新路徑點')
        break
      case 'connect-edge':
        message.info('🔗 連線模式：請依序點擊兩個路徑點以建立雙向連線邊')
        break
      case 'delete':
        onDeleteSelected()
        break
    }
  }

  return (
    <div
      style={{
        position: 'absolute',
        top: 16,
        left: 20,
        zIndex: 20,
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        background: COLOR_BG_BAR,
        backdropFilter: 'blur(12px)',
        border: `1px solid ${COLOR_BORDER}`,
        borderRadius: 14,
        padding: '6px 12px',
        boxShadow: '0 8px 32px rgba(0, 0, 0, 0.45)',
      }}
    >
      {/* ── 返回地圖管理 ──────────────────────────────────────────────── */}
      <Tooltip title="返回地圖清單">
        <Button
          type="text"
          icon={<ArrowLeftOutlined style={{ color: '#94a3b8', fontSize: 16 }} />}
          onClick={onBack}
          style={{ width: 36, height: 36, borderRadius: 8 }}
        />
      </Tooltip>

      <Divider orientation="vertical" style={{ borderColor: COLOR_BORDER, height: 24, margin: '0 4px' }} />

      {/* ── 核心編輯工具群 ────────────────────────────────────────────── */}
      <Space size={6}>
        <Tooltip title="選取與檢視 (Select)">
          <Button
            type={currentTool === 'select' ? 'primary' : 'text'}
            icon={<AimOutlined />}
            onClick={() => handleToolClick('select')}
            style={{
              background: currentTool === 'select' ? COLOR_PRIMARY : 'transparent',
              borderColor: currentTool === 'select' ? COLOR_PRIMARY : 'transparent',
              color: currentTool === 'select' ? '#ffffff' : '#cbd5e1',
              borderRadius: 8,
              height: 36,
            }}
          />
        </Tooltip>

        <Tooltip title="平移拖曳 (Move Gizmo)">
          <Button
            type={currentTool === 'translate' ? 'primary' : 'text'}
            icon={<ArrowsAltOutlined />}
            onClick={() => handleToolClick('translate')}
            style={{
              background: currentTool === 'translate' ? COLOR_PRIMARY : 'transparent',
              borderColor: currentTool === 'translate' ? COLOR_PRIMARY : 'transparent',
              color: currentTool === 'translate' ? '#ffffff' : '#cbd5e1',
              borderRadius: 8,
              height: 36,
            }}
          />
        </Tooltip>

        <Tooltip title="旋轉物件 (Rotate Gizmo)">
          <Button
            type={currentTool === 'rotate' ? 'primary' : 'text'}
            icon={<RotateRightOutlined />}
            onClick={() => handleToolClick('rotate')}
            style={{
              background: currentTool === 'rotate' ? COLOR_PRIMARY : 'transparent',
              borderColor: currentTool === 'rotate' ? COLOR_PRIMARY : 'transparent',
              color: currentTool === 'rotate' ? '#ffffff' : '#cbd5e1',
              borderRadius: 8,
              height: 36,
            }}
          />
        </Tooltip>

        <Tooltip title="新增路徑點 (Add Node：點擊地面放置點位)">
          <Button
            type={currentTool === 'add-node' ? 'primary' : 'text'}
            icon={<PlusCircleOutlined />}
            onClick={() => handleToolClick('add-node')}
            style={{
              background: currentTool === 'add-node' ? COLOR_PRIMARY : 'transparent',
              borderColor: currentTool === 'add-node' ? COLOR_PRIMARY : 'transparent',
              color: currentTool === 'add-node' ? '#ffffff' : '#cbd5e1',
              borderRadius: 8,
              height: 36,
            }}
          />
        </Tooltip>

        <Tooltip title="建立連線邊 (Connect Edge：點選兩點連線)">
          <Button
            type={currentTool === 'connect-edge' ? 'primary' : 'text'}
            icon={<BranchesOutlined />}
            onClick={() => handleToolClick('connect-edge')}
            style={{
              background: currentTool === 'connect-edge' ? COLOR_PRIMARY : 'transparent',
              borderColor: currentTool === 'connect-edge' ? COLOR_PRIMARY : 'transparent',
              color: currentTool === 'connect-edge' ? '#ffffff' : '#cbd5e1',
              borderRadius: 8,
              height: 36,
            }}
          />
        </Tooltip>

        {/* ── 新增環境物件下拉選單 ── */}
        <Dropdown menu={{ items: objectMenuItems }} placement="bottomLeft" trigger={['click']}>
          <Tooltip title="新增 3D 環境物件 (貨架/充電站/工作站/牆體/輸送帶)">
            <Button
              type="text"
              icon={<AppstoreAddOutlined />}
              style={{
                color: '#38bdf8',
                borderRadius: 8,
                height: 36,
              }}
            >
              新增物件 ▾
            </Button>
          </Tooltip>
        </Dropdown>

        {/* ── 刪除選取項目 ── */}
        <Tooltip title="刪除當前選取之點位或物件 (Delete)">
          <Button
            type="text"
            icon={<DeleteOutlined />}
            danger
            onClick={() => handleToolClick('delete')}
            style={{
              borderRadius: 8,
              height: 36,
            }}
          />
        </Tooltip>
      </Space>

      <Divider orientation="vertical" style={{ borderColor: COLOR_BORDER, height: 24, margin: '0 4px' }} />

      {/* ── 輔助功能設定 ──────────────────────────────────────────────── */}
      <Tooltip title={`網格吸附 (Snap to Grid: ${settings.gridStep}m)`}>
        <Button
          type={settings.snapToGrid ? 'default' : 'text'}
          icon={<AppstoreOutlined />}
          onClick={onToggleSnap}
          style={{
            borderColor: settings.snapToGrid ? COLOR_PRIMARY : COLOR_BORDER,
            color: settings.snapToGrid ? COLOR_PRIMARY : '#64748b',
            background: settings.snapToGrid ? 'rgba(255, 140, 0, 0.15)' : 'transparent',
            borderRadius: 8,
            height: 36,
            fontSize: 12,
          }}
        >
          {settings.snapToGrid ? '吸附 0.5m ON' : '吸附 OFF'}
        </Button>
      </Tooltip>

      {/* ── 儲存按鈕 ──────────────────────────────────────────────────── */}
      <Badge dot={hasUnsavedChanges} color={COLOR_PRIMARY} offset={[-4, 4]}>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={isSaving}
          onClick={onSave}
          style={{
            background: COLOR_PRIMARY,
            borderColor: COLOR_PRIMARY,
            fontWeight: 600,
            borderRadius: 8,
            height: 36,
            padding: '0 16px',
            boxShadow: hasUnsavedChanges ? '0 0 12px rgba(255, 140, 0, 0.6)' : 'none',
          }}
        >
          儲存地圖
        </Button>
      </Badge>
    </div>
  )
}
