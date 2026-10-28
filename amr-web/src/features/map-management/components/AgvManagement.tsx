import { useCallback, useEffect, useMemo, useState } from 'react'
import { Alert, Badge, Button, Form, Input, Modal, Select, Space, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons'
import { useNotify } from '../../../shared/hooks/useNotify'
import { createAgv, fetchAgvList, placeAgv } from '../api/agvApi'
import type { AgvResponse, CreateAgvRequest, PlaceAgvRequest } from '../types/agvDto'
import type { MapDataDto } from '../types/mapDto'

interface AgvManagementProps {
  maps: MapDataDto[]
  mapsLoading: boolean
  mapLoadError: string | null
}

export default function AgvManagement({ maps, mapsLoading, mapLoadError }: AgvManagementProps) {
  const notify = useNotify()
  const [agvs, setAgvs] = useState<AgvResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [placeTarget, setPlaceTarget] = useState<AgvResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [placementMapCode, setPlacementMapCode] = useState<string | undefined>()
  const [createForm] = Form.useForm<CreateAgvRequest>()
  const [placeForm] = Form.useForm<PlaceAgvRequest>()

  const loadAgvs = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      setAgvs(await fetchAgvList())
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : '無法載入 AGV 清單')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void Promise.resolve().then(loadAgvs) }, [loadAgvs])

  const selectedMap = maps.find(item => item.map.mapCode === placementMapCode)
  const availablePoints = useMemo(() => selectedMap?.points.filter(point => (
    !point.isDisabled && !agvs.some(agv => (
      agv.currentMapCode === selectedMap.map.mapCode && agv.currentPoint === point.pointCode
    ))
  )) ?? [], [selectedMap, agvs])

  const handleCreate = async (values: CreateAgvRequest) => {
    setSubmitting(true)
    try {
      const saved = await createAgv({
        agvCode: values.agvCode.trim(),
        agvName: values.agvName?.trim() || null,
        agvType: values.agvType,
      })
      setAgvs(previous => [...previous, saved])
      setCreateOpen(false)
      createForm.resetFields()
      notify.success(`AGV「${saved.agvCode}」已登錄`)
    } catch (error) {
      notify.error(error instanceof Error ? error.message : '登錄 AGV 失敗')
    } finally {
      setSubmitting(false)
    }
  }

  const openPlaceDialog = (agv: AgvResponse) => {
    const activeMapCode = maps.find(item => item.map.isActive)?.map.mapCode
    setPlaceTarget(agv)
    setPlacementMapCode(activeMapCode)
    placeForm.resetFields()
    placeForm.setFieldsValue({ mapCode: activeMapCode, pointCode: undefined })
  }

  const handlePlace = async (values: PlaceAgvRequest) => {
    if (!placeTarget) return
    setSubmitting(true)
    try {
      const saved = await placeAgv(placeTarget.agvCode, values)
      setAgvs(previous => previous.map(agv => agv.agvCode === saved.agvCode ? saved : agv))
      setPlaceTarget(null)
      placeForm.resetFields()
      notify.success(`AGV「${saved.agvCode}」已放置於 ${saved.currentPoint}`)
    } catch (error) {
      notify.error(error instanceof Error ? error.message : '放置 AGV 失敗')
      void loadAgvs()
    } finally {
      setSubmitting(false)
    }
  }

  const columns: ColumnsType<AgvResponse> = [
    { title: 'AGV 編碼', dataIndex: 'agvCode', key: 'agvCode', render: (code: string) => <span className="map-code">{code}</span> },
    { title: '名稱', dataIndex: 'agvName', key: 'agvName', render: (name: string | null) => name || '—' },
    { title: '車型', dataIndex: 'agvType', key: 'agvType', render: (type: string) => type === 'STANDARD' ? '標準型' : type === 'TELESCOPIC' ? '伸縮型' : type },
    { title: '狀態', dataIndex: 'status', key: 'status', render: (status: string) => <Badge status={status === 'ERROR' ? 'error' : status === 'IDLE' ? 'success' : 'processing'} text={status} /> },
    { title: '目前地圖', dataIndex: 'currentMapCode', key: 'currentMapCode', render: (code: string | null) => code || <Tag>未放置</Tag> },
    { title: '目前點位', dataIndex: 'currentPoint', key: 'currentPoint', render: (code: string | null) => code || '—' },
    { title: '操作', key: 'actions', width: 120, render: (_, agv) => agv.currentMapCode
      ? <span className="agv-placed-label">已放置</span>
      : <Button type="link" size="small" disabled={mapsLoading || !!mapLoadError || maps.length === 0} onClick={() => openPlaceDialog(agv)}>放置於點位</Button> },
  ]

  return (
    <section className="map-management-panel" aria-label="AGV 管理">
      <div className="agv-management-toolbar">
        <div>
          <h1>AGV 管理</h1>
          <p>登錄車輛後，再選擇地圖與點位進行初始放置。</p>
        </div>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => void loadAgvs()}>重新整理</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>新增 AGV</Button>
        </Space>
      </div>

      {loadError && <Alert className="agv-management-alert" type="error" showIcon message={loadError} />}
      {mapLoadError && <Alert className="agv-management-alert" type="warning" showIcon message="地圖載入失敗，暫時無法選擇放置點位。" />}
      <Table<AgvResponse>
        className="map-management-table"
        rowKey="agvCode"
        dataSource={agvs}
        columns={columns}
        loading={loading}
        locale={{ emptyText: loadError ? '無法載入 AGV 資料' : '目前尚無 AGV' }}
        pagination={{ pageSize: 10, showTotal: total => `共 ${total} 台 AGV` }}
        scroll={{ x: 850 }}
      />

      <Modal
        title="新增 AGV"
        open={createOpen}
        okText="確認登錄"
        cancelText="取消"
        confirmLoading={submitting}
        onOk={() => void createForm.submit()}
        onCancel={() => { if (!submitting) { setCreateOpen(false); createForm.resetFields() } }}
      >
        <Form form={createForm} layout="vertical" onFinish={values => void handleCreate(values)} initialValues={{ agvType: 'STANDARD' }}>
          <Form.Item label="AGV 編碼" name="agvCode" rules={[{ required: true, whitespace: true, message: '請輸入 AGV 編碼' }, { max: 64, message: '編碼不可超過 64 個字元' }]}>
            <Input placeholder="例如 AGV_01" maxLength={64} />
          </Form.Item>
          <Form.Item label="AGV 名稱" name="agvName" rules={[{ max: 128, message: '名稱不可超過 128 個字元' }]}>
            <Input placeholder="例如 倉庫搬運車" maxLength={128} />
          </Form.Item>
          <Form.Item label="車型" name="agvType" rules={[{ required: true, message: '請選擇車型' }]}>
            <Select options={[{ value: 'STANDARD', label: '標準型（STANDARD）' }, { value: 'TELESCOPIC', label: '伸縮型（TELESCOPIC）' }]} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`放置 AGV：${placeTarget?.agvCode ?? ''}`}
        open={!!placeTarget}
        okText="確認放置"
        cancelText="取消"
        confirmLoading={submitting}
        onOk={() => void placeForm.submit()}
        onCancel={() => { if (!submitting) { setPlaceTarget(null); placeForm.resetFields() } }}
      >
        <Form form={placeForm} layout="vertical" onFinish={values => void handlePlace(values)}>
          <Form.Item label="地圖" name="mapCode" rules={[{ required: true, message: '請選擇地圖' }]}>
            <Select
              placeholder="選擇地圖"
              showSearch
              optionFilterProp="label"
              onChange={code => { setPlacementMapCode(code); placeForm.setFieldValue('pointCode', undefined) }}
              options={maps.map(item => ({ value: item.map.mapCode, label: `${item.map.mapName} (${item.map.mapCode})${item.map.isActive ? ' · 啟用中' : ''}` }))}
            />
          </Form.Item>
          <Form.Item label="點位" name="pointCode" rules={[{ required: true, message: '請選擇點位' }]} extra="僅顯示未停用且未被其他 AGV 佔用的點位。">
            <Select
              placeholder="選擇點位"
              showSearch
              disabled={!placementMapCode}
              options={availablePoints.map(point => ({ value: point.pointCode, label: `${point.pointCode} · ${point.pointType} (${point.x}, ${point.y})` }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </section>
  )
}
