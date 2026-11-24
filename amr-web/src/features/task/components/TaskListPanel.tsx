// ============================================================================
// 檔案名稱：TaskListPanel.tsx
// 模組說明：搬運任務監控清單元件 (狀態過濾、取消任務、展開 rcs_task_detail 階段時間軸)
// ============================================================================

import { useCallback, useEffect, useMemo, useState } from 'react'
import { Button, Empty, Popconfirm, Segmented, Spin, Tag, Tooltip } from 'antd'
import {
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  DownOutlined,
  ReloadOutlined,
  StopOutlined,
  SyncOutlined,
  UpOutlined,
} from '@ant-design/icons'
import { useNotify } from '../../../shared/hooks/useNotify'
import { cancelTask, fetchTaskDetails } from '../api/taskApi'
import type { TaskDetailResponse, TaskResponse, TaskStatus } from '../types/taskDto'

interface TaskListPanelProps {
  tasks: TaskResponse[]
  loading: boolean
  error: string | null
  onRefresh: () => Promise<void>
}

type FilterKey = 'ALL' | TaskStatus

/** 格式化時間為 HH:mm:ss，方便於緊湊側邊欄檢視 */
function formatTime(iso: string | null): string {
  if (!iso) return '--:--:--'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleTimeString('zh-TW', { hour12: false })
}

/** 依任務或階段狀態回傳標籤配色與文字 */
function renderStatusTag(status: TaskStatus) {
  switch (status) {
    case 'RUNNING':
      return <Tag color="processing" icon={<SyncOutlined spin />} style={{ margin: 0 }}>進行中</Tag>
    case 'PENDING':
      return <Tag color="warning" icon={<ClockCircleOutlined />} style={{ margin: 0 }}>排隊中</Tag>
    case 'COMPLETED':
      return <Tag color="success" icon={<CheckCircleOutlined />} style={{ margin: 0 }}>已完成</Tag>
    case 'FAILED':
      return <Tag color="error" icon={<CloseCircleOutlined />} style={{ margin: 0 }}>已失敗</Tag>
  }
}

export default function TaskListPanel({
  tasks,
  loading,
  error,
  onRefresh,
}: TaskListPanelProps) {
  const notify = useNotify()
  const [filter, setFilter] = useState<FilterKey>('ALL')
  const [expandedTaskCode, setExpandedTaskCode] = useState<string | null>(null)
  const [detailsMap, setDetailsMap] = useState<Record<string, TaskDetailResponse[]>>({})
  const [detailsLoadingCode, setDetailsLoadingCode] = useState<string | null>(null)
  const [cancellingCode, setCancellingCode] = useState<string | null>(null)

  const filteredTasks = useMemo(() => {
    if (filter === 'ALL') return tasks
    return tasks.filter((task) => task.status === filter)
  }, [tasks, filter])

  // 載入單筆任務的 rcs_task_detail 階段明細
  const loadDetails = useCallback(async (taskCode: string, silent = false) => {
    if (!silent) setDetailsLoadingCode(taskCode)
    try {
      const details = await fetchTaskDetails(taskCode)
      setDetailsMap((prev) => ({ ...prev, [taskCode]: details }))
    } catch (err) {
      if (!silent) {
        notify.error(err instanceof Error ? err.message : '無法載入任務階段明細')
      }
    } finally {
      if (!silent) {
        setDetailsLoadingCode((prev) => (prev === taskCode ? null : prev))
      }
    }
  }, [notify])

  // 當展開中的任務清單被輪詢更新時，同步刷新其明細時間軸
  useEffect(() => {
    if (!expandedTaskCode) return
    let cancelled = false
    fetchTaskDetails(expandedTaskCode)
      .then((details) => {
        if (!cancelled) {
          setDetailsMap((prev) => ({ ...prev, [expandedTaskCode]: details }))
        }
      })
      .catch(() => {
        // 背景靜默刷新失敗時保留既有明細畫面
      })
    return () => {
      cancelled = true
    }
  }, [expandedTaskCode, tasks])

  const handleToggleExpand = (taskCode: string) => {
    if (expandedTaskCode === taskCode) {
      setExpandedTaskCode(null)
      return
    }
    setExpandedTaskCode(taskCode)
    void loadDetails(taskCode)
  }

  const handleCancel = async (taskCode: string) => {
    setCancellingCode(taskCode)
    try {
      await cancelTask(taskCode)
      notify.success(`已取消任務 ${taskCode}`)
      await onRefresh()
      if (expandedTaskCode === taskCode) {
        await loadDetails(taskCode, true)
      }
    } catch (err) {
      notify.error(err instanceof Error ? err.message : '取消任務失敗')
    } finally {
      setCancellingCode(null)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: 10 }}>
      {/* 狀態過濾列與重新整理按鈕 */}
      <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
        <Segmented
          size="small"
          block
          value={filter}
          onChange={(val) => setFilter(val as FilterKey)}
          options={[
            { label: '全部', value: 'ALL' },
            { label: '執行', value: 'RUNNING' },
            { label: '排隊', value: 'PENDING' },
            { label: '完成', value: 'COMPLETED' },
            { label: '失敗', value: 'FAILED' },
          ]}
          style={{ flex: 1, fontSize: 11 }}
        />
        <Tooltip title="立即重新整理">
          <Button
            size="small"
            icon={<ReloadOutlined spin={loading} />}
            onClick={() => void onRefresh()}
          />
        </Tooltip>
      </div>

      {error && (
        <div role="alert" style={{
          fontSize: 12,
          color: '#f87171',
          background: 'rgba(127, 29, 29, 0.25)',
          border: '1px solid rgba(248, 113, 113, 0.4)',
          borderRadius: 6,
          padding: '6px 10px',
        }}>
          {error}
        </div>
      )}

      {/* 任務卡片清單 */}
      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 8, paddingRight: 2 }}>
        {loading && tasks.length === 0 ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '40px 0' }}>
            <Spin />
          </div>
        ) : filteredTasks.length === 0 ? (
          <Empty
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            description={<span style={{ color: '#64748b', fontSize: 12 }}>目前沒有符合條件的任務</span>}
            style={{ margin: '32px 0' }}
          />
        ) : (
          filteredTasks.map((task) => {
            const isExpanded = expandedTaskCode === task.taskCode
            const details = detailsMap[task.taskCode]
            const isLoadingDetails = detailsLoadingCode === task.taskCode
            const canCancel = task.status === 'PENDING' || task.status === 'RUNNING'

            return (
              <div
                key={task.taskCode}
                style={{
                  background: 'rgba(15, 34, 70, 0.78)',
                  border: `1px solid ${isExpanded ? '#FF8C00' : '#1e3a5f'}`,
                  borderRadius: 8,
                  padding: '10px 12px',
                  transition: 'border-color 0.2s',
                }}
              >
                {/* 第一列：任務代碼 + 狀態標籤 */}
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
                  <span style={{
                    fontFamily: 'monospace',
                    fontSize: 12,
                    fontWeight: 700,
                    color: '#f8fafc',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                  }}>
                    {task.taskCode}
                  </span>
                  {renderStatusTag(task.status)}
                </div>

                {/* 第二列：起訖點路徑與車輛 */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginTop: 6,
                  fontSize: 12,
                  color: '#cbd5e1',
                  fontFamily: 'monospace',
                }}>
                  <span>
                    <span style={{ color: '#10b981' }}>{task.startPoint}</span>
                    <span style={{ color: '#64748b', margin: '0 6px' }}>→</span>
                    <span style={{ color: '#38bdf8' }}>{task.targetPoint}</span>
                  </span>
                  <span style={{ color: task.agvCode ? '#FF8C00' : '#94a3b8', fontSize: 11 }}>
                    🚗 {task.agvCode ?? '待指派'}
                  </span>
                </div>

                {/* 第三列：時間資訊與操作按鈕 */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  marginTop: 8,
                  paddingTop: 6,
                  borderTop: '1px solid rgba(30, 58, 95, 0.7)',
                }}>
                  <span style={{ fontSize: 11, color: '#64748b', fontFamily: 'monospace' }}>
                    建立 {formatTime(task.createdAt)}
                    {task.finishedAt ? ` · 結束 ${formatTime(task.finishedAt)}` : ''}
                  </span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Button
                      size="small"
                      type="text"
                      icon={isExpanded ? <UpOutlined /> : <DownOutlined />}
                      onClick={() => handleToggleExpand(task.taskCode)}
                      style={{ fontSize: 11, color: '#94a3b8', padding: '0 6px' }}
                    >
                      明細
                    </Button>
                    {canCancel && (
                      <Popconfirm
                        title="確認取消此任務？"
                        description="取消後將停止派發並釋放佔用的路徑點。"
                        okText="確認取消"
                        cancelText="返回"
                        onConfirm={() => void handleCancel(task.taskCode)}
                      >
                        <Button
                          size="small"
                          danger
                          type="text"
                          icon={<StopOutlined />}
                          loading={cancellingCode === task.taskCode}
                          style={{ fontSize: 11, padding: '0 6px' }}
                        >
                          取消
                        </Button>
                      </Popconfirm>
                    )}
                  </div>
                </div>

                {/* 展開區塊：rcs_task_detail 兩階段執行時間軸 */}
                {isExpanded && (
                  <div style={{
                    marginTop: 8,
                    paddingTop: 8,
                    borderTop: '1px dashed #1e3a5f',
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 6,
                  }}>
                    {isLoadingDetails ? (
                      <div style={{ textAlign: 'center', padding: '8px 0' }}><Spin size="small" /></div>
                    ) : !details || details.length === 0 ? (
                      <div style={{ fontSize: 11, color: '#64748b', textAlign: 'center', padding: '4px 0' }}>
                        尚無階段執行明細（任務尚未指派車輛）
                      </div>
                    ) : (
                      details.map((step) => (
                        <div
                          key={step.id}
                          style={{
                            background: 'rgba(5, 16, 43, 0.7)',
                            border: '1px solid #1e3a5f',
                            borderRadius: 6,
                            padding: '6px 8px',
                            fontSize: 11,
                          }}
                        >
                          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                            <span style={{ fontWeight: 600, color: step.stepType === 'PICKUP' ? '#10b981' : '#38bdf8' }}>
                              Step {step.stepSeq} · {step.stepType}
                            </span>
                            {renderStatusTag(step.status)}
                          </div>
                          <div style={{ color: '#e2e8f0', fontFamily: 'monospace', marginTop: 3 }}>
                            {step.fromPoint} → {step.toPoint}
                          </div>
                          <div style={{ color: '#64748b', fontFamily: 'monospace', fontSize: 10, marginTop: 2 }}>
                            開始 {formatTime(step.startedAt)} · 完成 {formatTime(step.finishedAt)}
                          </div>
                        </div>
                      ))
                    )}
                  </div>
                )}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}
