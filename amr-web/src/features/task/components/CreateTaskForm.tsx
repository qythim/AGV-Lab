// ============================================================================
// 檔案名稱：CreateTaskForm.tsx
// 模組說明：建立搬運任務表單元件 (支援下拉搜尋與 3D 場景直接點選取貨/卸貨點)
// ============================================================================

import { useMemo, useState } from 'react'
import { Button, InputNumber, Select, Space, Tag, Tooltip } from 'antd'
import { AimOutlined, RocketOutlined, SwapOutlined } from '@ant-design/icons'
import { useNotify } from '../../../shared/hooks/useNotify'
import { createTask } from '../api/taskApi'
import type {
  TaskAgvOption,
  TaskPointOption,
  TaskPointPickField,
  TaskResponse,
} from '../types/taskDto'

interface CreateTaskFormProps {
  mapCode: string
  points: TaskPointOption[]
  agvs: TaskAgvOption[]
  startPoint: string | null
  targetPoint: string | null
  pickingField: TaskPointPickField
  onChangeStartPoint: (code: string | null) => void
  onChangeTargetPoint: (code: string | null) => void
  onChangePickingField: (field: TaskPointPickField) => void
  onTaskCreated: (task: TaskResponse) => void
}

const AUTO_AGV_VALUE = '__AUTO__'

/** 依點位類型給予對應標籤顏色 */
function getPointTypeColor(type: string): string {
  switch (type) {
    case 'RACK': return 'blue'
    case 'STATION': return 'orange'
    case 'CHARGER': return 'green'
    default: return 'default'
  }
}

/** 依 AGV 狀態給予對應標籤顏色 */
function getAgvStatusColor(status: string): string {
  switch (status) {
    case 'IDLE': return 'success'
    case 'RUNNING': return 'processing'
    case 'CHARGING': return 'warning'
    case 'ERROR': return 'error'
    default: return 'default'
  }
}

export default function CreateTaskForm({
  mapCode,
  points,
  agvs,
  startPoint,
  targetPoint,
  pickingField,
  onChangeStartPoint,
  onChangeTargetPoint,
  onChangePickingField,
  onTaskCreated,
}: CreateTaskFormProps) {
  const notify = useNotify()
  const [selectedAgv, setSelectedAgv] = useState<string>(AUTO_AGV_VALUE)
  const [targetLevel, setTargetLevel] = useState<number>(0)
  const [submitting, setSubmitting] = useState(false)

  // 建立點位下拉選項清單
  const pointOptions = useMemo(() => points.map((pt) => {
    const displayName = pt.name && pt.name.trim() !== pt.code ? `${pt.code} (${pt.name.trim()})` : pt.code
    return {
      value: pt.code,
      searchText: `${pt.code} ${pt.name ?? ''} ${pt.type}`.toLowerCase(),
      label: (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
          <span style={{ fontFamily: 'monospace' }}>{displayName}</span>
          <Tag color={getPointTypeColor(pt.type)} style={{ margin: 0, fontSize: 10, lineHeight: '16px' }}>
            {pt.type}
          </Tag>
        </div>
      ),
    }
  }), [points])

  // 建立車輛下拉選項清單 (預設為自動指派)
  const agvOptions = useMemo(() => [
    {
      value: AUTO_AGV_VALUE,
      label: (
        <span style={{ color: '#FF8C00', fontWeight: 600 }}>
          ⚡ 自動指派 (選擇最近 IDLE 車輛)
        </span>
      ),
    },
    ...agvs.map((agv) => ({
      value: agv.agvCode,
      label: (
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
          <span style={{ fontFamily: 'monospace' }}>
            {agv.agvCode}{agv.currentPoint ? ` @ ${agv.currentPoint}` : ''}
          </span>
          <Tag color={getAgvStatusColor(agv.status)} style={{ margin: 0, fontSize: 10, lineHeight: '16px' }}>
            {agv.status}
          </Tag>
        </div>
      ),
    })),
  ], [agvs])

  const isSamePoint = Boolean(startPoint && targetPoint && startPoint === targetPoint)
  const canSubmit = Boolean(mapCode && startPoint && targetPoint && !isSamePoint && !submitting)

  const handleTogglePick = (field: 'start' | 'target') => {
    const next = pickingField === field ? null : field
    onChangePickingField(next)
    if (next === 'start') {
      notify.info('🎯 請點擊 3D 地圖上的路徑點作為【取貨點】', 'task-3d-pick')
    } else if (next === 'target') {
      notify.info('🎯 請點擊 3D 地圖上的路徑點作為【卸貨點】', 'task-3d-pick')
    }
  }

  const handleSwapPoints = () => {
    const prevStart = startPoint
    onChangeStartPoint(targetPoint)
    onChangeTargetPoint(prevStart)
  }

  const handleSubmit = async () => {
    if (!startPoint || !targetPoint) {
      notify.warning('請先選擇取貨點與卸貨點')
      return
    }
    if (startPoint === targetPoint) {
      notify.warning('取貨點與卸貨點不可為同一個點位')
      return
    }

    setSubmitting(true)
    try {
      const created = await createTask({
        mapCode,
        agvCode: selectedAgv === AUTO_AGV_VALUE ? null : selectedAgv,
        startPoint,
        targetPoint,
        targetLevel,
      })
      notify.success(`🚀 已建立任務 ${created.taskCode}（${startPoint} → ${targetPoint}）`)
      onChangePickingField(null)
      onChangeStartPoint(null)
      onChangeTargetPoint(null)
      onTaskCreated(created)
    } catch (error) {
      notify.error(error instanceof Error ? error.message : '建立任務失敗')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14, paddingTop: 4 }}>
      {/* 取貨點 (Start Point) */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
          <span style={{ fontSize: 12, color: '#cbd5e1', fontWeight: 600 }}>
            <span style={{ color: '#10b981', marginRight: 6 }}>●</span>取貨點 (Start)
          </span>
          <span style={{ fontSize: 11, color: '#64748b' }}>點擊 🎯 可從 3D 選點</span>
        </div>
        <Space.Compact style={{ width: '100%' }}>
          <Select
            showSearch
            allowClear
            placeholder="選擇取貨點..."
            value={startPoint}
            onChange={(value) => onChangeStartPoint(value ?? null)}
            options={pointOptions}
            filterOption={(input, option) =>
              (option?.searchText ?? '').includes(input.trim().toLowerCase())
            }
            style={{ width: '100%' }}
          />
          <Tooltip title={pickingField === 'start' ? '取消 3D 選點' : '從 3D 地圖點選取貨點'}>
            <Button
              type={pickingField === 'start' ? 'primary' : 'default'}
              icon={<AimOutlined />}
              onClick={() => handleTogglePick('start')}
              style={pickingField === 'start' ? { background: '#10b981', borderColor: '#10b981' } : undefined}
            />
          </Tooltip>
        </Space.Compact>
      </div>

      {/* 互換按鈕 */}
      <div style={{ display: 'flex', justifyContent: 'center', margin: '-6px 0' }}>
        <Tooltip title="對調取貨點與卸貨點">
          <Button
            size="small"
            type="text"
            icon={<SwapOutlined rotate={90} />}
            onClick={handleSwapPoints}
            disabled={!startPoint && !targetPoint}
            style={{ color: '#94a3b8' }}
          >
            對調起訖點
          </Button>
        </Tooltip>
      </div>

      {/* 卸貨點 (Target Point) */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
          <span style={{ fontSize: 12, color: '#cbd5e1', fontWeight: 600 }}>
            <span style={{ color: '#38bdf8', marginRight: 6 }}>●</span>卸貨點 (Target)
          </span>
        </div>
        <Space.Compact style={{ width: '100%' }}>
          <Select
            showSearch
            allowClear
            placeholder="選擇卸貨點..."
            value={targetPoint}
            onChange={(value) => onChangeTargetPoint(value ?? null)}
            options={pointOptions}
            filterOption={(input, option) =>
              (option?.searchText ?? '').includes(input.trim().toLowerCase())
            }
            style={{ width: '100%' }}
          />
          <Tooltip title={pickingField === 'target' ? '取消 3D 選點' : '從 3D 地圖點選卸貨點'}>
            <Button
              type={pickingField === 'target' ? 'primary' : 'default'}
              icon={<AimOutlined />}
              onClick={() => handleTogglePick('target')}
              style={pickingField === 'target' ? { background: '#0ea5e9', borderColor: '#0ea5e9' } : undefined}
            />
          </Tooltip>
        </Space.Compact>
      </div>

      {isSamePoint && (
        <div style={{ fontSize: 12, color: '#f87171', marginTop: -6 }}>
          ⚠️ 取貨點與卸貨點不可相同
        </div>
      )}

      {/* 指派車輛 (預設 Auto) */}
      <div>
        <div style={{ fontSize: 12, color: '#cbd5e1', fontWeight: 600, marginBottom: 6 }}>
          指派車輛 (AGV)
        </div>
        <Select
          value={selectedAgv}
          onChange={setSelectedAgv}
          options={agvOptions}
          style={{ width: '100%' }}
        />
      </div>

      {/* 目標貨架層級 (可選，預設 0) */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontSize: 12, color: '#cbd5e1', fontWeight: 600 }}>目標層級 (Level)</span>
        <InputNumber
          min={0}
          max={10}
          value={targetLevel}
          onChange={(val) => setTargetLevel(val ?? 0)}
          style={{ width: 100 }}
        />
      </div>

      {/* 派發按鈕 */}
      <Button
        type="primary"
        size="large"
        icon={<RocketOutlined />}
        loading={submitting}
        disabled={!canSubmit}
        onClick={handleSubmit}
        block
        style={{
          marginTop: 6,
          fontWeight: 700,
          height: 42,
        }}
      >
        派發搬運任務
      </Button>

      {/* 操作提示卡 */}
      <div style={{
        background: 'rgba(15, 34, 70, 0.7)',
        border: '1px solid #1e3a5f',
        borderRadius: 8,
        padding: '10px 12px',
        fontSize: 11,
        color: '#94a3b8',
        lineHeight: 1.6,
      }}>
        <div>• <strong style={{ color: '#e2e8f0' }}>兩階段調度</strong>：車輛會先自當前位置前往取貨點 (<span style={{ color: '#10b981' }}>PICKUP</span>)，抵達後再前往卸貨點 (<span style={{ color: '#38bdf8' }}>DELIVERY</span>)。</div>
        <div>• <strong style={{ color: '#e2e8f0' }}>無空閒車輛</strong>：任務會進入 <code style={{ color: '#f59e0b' }}>PENDING</code> 佇列，待任一車輛完成後自動接單。</div>
      </div>
    </div>
  )
}
