// ============================================================================
// 檔案名稱：TaskSidebar.tsx
// 模組說明：3D 檢視器右側可收合任務調度側邊欄 (建立任務 + 任務監控 + 階段明細)
//           任務清單狀態獨立維護於此元件內，輪詢更新時不會觸發 Three.js Canvas 重繪
// ============================================================================

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Badge, Button, ConfigProvider, Tabs, Tooltip, theme } from 'antd'
import {
  DoubleRightOutlined,
  PlusCircleOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import { fetchTasks } from '../api/taskApi'
import type {
  TaskAgvOption,
  TaskPointOption,
  TaskPointPickField,
  TaskResponse,
} from '../types/taskDto'
import CreateTaskForm from './CreateTaskForm'
import TaskListPanel from './TaskListPanel'

interface TaskSidebarProps {
  mapCode: string
  points: TaskPointOption[]
  agvs: TaskAgvOption[]
  startPoint: string | null
  targetPoint: string | null
  pickingField: TaskPointPickField
  onChangeStartPoint: (code: string | null) => void
  onChangeTargetPoint: (code: string | null) => void
  onChangePickingField: (field: TaskPointPickField) => void
}

const SIDEBAR_WIDTH = 368
const POLL_INTERVAL_MS = 3000

export default function TaskSidebar({
  mapCode,
  points,
  agvs,
  startPoint,
  targetPoint,
  pickingField,
  onChangeStartPoint,
  onChangeTargetPoint,
  onChangePickingField,
}: TaskSidebarProps) {
  const [open, setOpen] = useState(true)
  const [activeTab, setActiveTab] = useState<'create' | 'monitor'>('create')
  const [tasks, setTasks] = useState<TaskResponse[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 載入目前地圖之任務清單
  const refreshTasks = useCallback(async (silent = false) => {
    if (!silent) setLoading(true)
    try {
      const allTasks = await fetchTasks()
      setTasks(allTasks.filter((task) => task.mapCode === mapCode))
      setError(null)
    } catch (err) {
      setError(err instanceof Error ? err.message : '無法載入任務清單')
    } finally {
      if (!silent) setLoading(false)
    }
  }, [mapCode])

  // 初次掛載與開啟側邊欄時定期輪詢任務狀態，讓 PENDING -> RUNNING -> COMPLETED 即時更新
  useEffect(() => {
    void Promise.resolve().then(() => refreshTasks())
    const timer = window.setInterval(() => {
      void refreshTasks(true)
    }, POLL_INTERVAL_MS)
    return () => window.clearInterval(timer)
  }, [refreshTasks])

  // 收合側邊欄時自動結束 3D 點選模式，避免誤點地圖
  const handleCollapse = () => {
    setOpen(false)
    if (pickingField !== null) {
      onChangePickingField(null)
    }
  }

  // 進行中與排隊中任務總數 (用於顯示提醒角標)
  const activeCount = useMemo(
    () => tasks.filter((task) => task.status === 'RUNNING' || task.status === 'PENDING').length,
    [tasks],
  )

  const handleTaskCreated = () => {
    setActiveTab('monitor')
    void refreshTasks()
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#FF8C00',
          colorBgContainer: '#0b1a36',
          colorBgElevated: '#0f2246',
          colorBorder: '#1e3a5f',
          borderRadius: 6,
        },
      }}
    >
      {/* 收合狀態：右側邊緣懸浮展開按鈕 */}
      {!open && (
        <div style={{ position: 'absolute', top: 16, right: 16, zIndex: 50 }}>
          <Badge count={activeCount} offset={[-4, 4]} color="#FF8C00">
            <Button
              type="primary"
              onClick={() => setOpen(true)}
              style={{
                background: 'rgba(5, 16, 43, 0.92)',
                border: '1px solid #FF8C00',
                color: '#FF8C00',
                fontWeight: 700,
                height: 38,
                padding: '0 16px',
                boxShadow: '0 4px 16px rgba(0,0,0,0.45)',
              }}
            >
              📋 任務調度
            </Button>
          </Badge>
        </div>
      )}

      {/* 展開狀態：右側玻璃質感可滑動面板 */}
      <aside
        aria-label="任務調度側邊欄"
        style={{
          position: 'absolute',
          top: 0,
          right: 0,
          bottom: 0,
          width: SIDEBAR_WIDTH,
          zIndex: 50,
          display: 'flex',
          flexDirection: 'column',
          background: 'rgba(5, 16, 43, 0.93)',
          backdropFilter: 'blur(10px)',
          borderLeft: '1px solid #1e3a5f',
          boxShadow: '-6px 0 24px rgba(0, 0, 0, 0.45)',
          color: '#e2e8f0',
          transform: open ? 'translateX(0)' : `translateX(${SIDEBAR_WIDTH + 16}px)`,
          transition: 'transform 0.25s ease',
          pointerEvents: open ? 'auto' : 'none',
        }}
      >
        {/* 標題列 */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 16px',
          borderBottom: '1px solid #1e3a5f',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 14, fontWeight: 700, color: '#FF8C00' }}>
              📋 任務調度中心
            </span>
            <span style={{ fontSize: 11, color: '#64748b', fontFamily: 'monospace' }}>
              {mapCode}
            </span>
          </div>
          <Tooltip title="收合側邊欄">
            <Button
              size="small"
              type="text"
              icon={<DoubleRightOutlined />}
              onClick={handleCollapse}
              style={{ color: '#94a3b8' }}
            />
          </Tooltip>
        </div>

        {/* 雙頁籤內容區 */}
        <div style={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', padding: '0 14px 14px' }}>
          <Tabs
            activeKey={activeTab}
            onChange={(key) => {
              const nextTab = key as 'create' | 'monitor'
              setActiveTab(nextTab)
              if (nextTab === 'monitor') {
                onChangePickingField(null)
                void refreshTasks()
              }
            }}
            style={{ height: '100%' }}
            items={[
              {
                key: 'create',
                label: (
                  <span>
                    <PlusCircleOutlined style={{ marginRight: 6 }} />
                    建立任務
                  </span>
                ),
                children: (
                  <CreateTaskForm
                    mapCode={mapCode}
                    points={points}
                    agvs={agvs}
                    startPoint={startPoint}
                    targetPoint={targetPoint}
                    pickingField={pickingField}
                    onChangeStartPoint={onChangeStartPoint}
                    onChangeTargetPoint={onChangeTargetPoint}
                    onChangePickingField={onChangePickingField}
                    onTaskCreated={handleTaskCreated}
                  />
                ),
              },
              {
                key: 'monitor',
                label: (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <UnorderedListOutlined />
                    任務監控
                    {activeCount > 0 && (
                      <Badge count={activeCount} size="small" color="#FF8C00" />
                    )}
                  </span>
                ),
                children: (
                  <div style={{ height: 'calc(100vh - 130px)' }}>
                    <TaskListPanel
                      tasks={tasks}
                      loading={loading}
                      error={error}
                      onRefresh={() => refreshTasks()}
                    />
                  </div>
                ),
              },
            ]}
          />
        </div>
      </aside>
    </ConfigProvider>
  )
}
