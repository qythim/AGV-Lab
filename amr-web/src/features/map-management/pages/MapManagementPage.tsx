import { useCallback, useEffect, useMemo, useState } from 'react'
import { App, Badge, Button, ConfigProvider, Descriptions, Drawer, Input, Space, Table, Tag, Upload, theme } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { CheckCircleOutlined, EditOutlined, EyeOutlined, ImportOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import { useNotify } from '../../../shared/hooks/useNotify'
import { fetchMapList, importMap, activateMap } from '../api/mapApi'
import AgvManagement from '../components/AgvManagement'
import type { MapDataDto } from '../types/mapDto'
import './MapManagementPage.css'

interface MapManagementPageProps {
  onOpenViewer: (mapCode?: string) => void
  onOpenEditor: (mapCode: string) => void
}

export default function MapManagementPage({ onOpenViewer, onOpenEditor }: MapManagementPageProps) {
  const notify = useNotify()
  const { modal } = App.useApp()
  const [mapList, setMapList] = useState<MapDataDto[]>([])
  const [loading, setLoading] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selectedMap, setSelectedMap] = useState<MapDataDto | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [codeInput, setCodeInput] = useState('')
  const [nameInput, setNameInput] = useState('')
  const [filters, setFilters] = useState({ code: '', name: '' })
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([])
  const [section, setSection] = useState<'maps' | 'agvs'>('maps')

  const loadMaps = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    try {
      setMapList(await fetchMapList())
    } catch {
      setMapList([])
      setLoadError('無法載入地圖清單，請確認 RCS 服務與網路連線。')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { void Promise.resolve().then(loadMaps) }, [loadMaps])

  const activeMap = mapList.find(item => item.map?.isActive)
  const filteredMaps = useMemo(() => mapList.filter(item => (
    item.map.mapCode.toLowerCase().includes(filters.code.toLowerCase()) &&
    item.map.mapName.toLowerCase().includes(filters.name.toLowerCase())
  )), [mapList, filters])

  const handleSearch = () => {
    setFilters({ code: codeInput.trim(), name: nameInput.trim() })
    setSelectedRowKeys([])
  }

  const handleReset = () => {
    setCodeInput('')
    setNameInput('')
    setFilters({ code: '', name: '' })
    setSelectedRowKeys([])
  }

  const handleActivate = (record: MapDataDto) => {
    modal.confirm({
      title: '確認啟用地圖？',
      content: `將「${record.map.mapName}」設為目前啟用的地圖。`,
      okText: '確認啟用',
      cancelText: '取消',
      onOk: async () => {
        try {
          // 後端儲存成功後才更新清單與詳細資訊。
          const savedMap = await activateMap(record.map.mapCode, record.map.version)
          setMapList(previous => previous.map(item => (
            item.map.mapCode === savedMap.map.mapCode
              ? savedMap
              : { ...item, map: { ...item.map, isActive: false } }
          )))
          setSelectedMap(previous => {
            if (!previous) return previous
            return previous.map.mapCode === savedMap.map.mapCode
              ? savedMap
              : { ...previous, map: { ...previous.map, isActive: false } }
          })
          notify.success(`已啟用地圖「${savedMap.map.mapName}」`)
        } catch (error) {
          notify.error(error instanceof Error ? error.message : '啟用地圖失敗')
          throw error
        }
      },
    })
  }

  const handleImport = (file: File) => {
    const uploadMap = async () => {
      try {
        await importMap(await file.text())
        await loadMaps()
        notify.success('地圖已成功匯入並儲存至後端')
      } catch (error) {
        const message = error instanceof Error ? error.message : '未知的檔案錯誤'
        notify.error(`匯入失敗：${message}`)
      }
    }
    void uploadMap()
    return false
  }

  const columns: ColumnsType<MapDataDto> = [
    { title: '地圖編碼', dataIndex: ['map', 'mapCode'], key: 'mapCode', render: (code: string) => <span className="map-code">{code}</span> },
    { title: '地圖名稱', dataIndex: ['map', 'mapName'], key: 'mapName', render: (name: string) => <span className="map-name">{name}</span> },
    { title: '地圖版本', dataIndex: ['map', 'version'], key: 'version', width: 120, render: (version: number) => `v${version}` },
    { title: '地圖尺寸', key: 'size', width: 150, render: (_, record) => `${record.map.width} × ${record.map.height} m` },
    { title: '路徑點', key: 'points', width: 100, render: (_, record) => record.pointList?.length ?? 0 },
    { title: '場景物件', key: 'objects', width: 110, render: (_, record) => record.objects?.length ?? 0 },
    { title: '狀態', dataIndex: ['map', 'isActive'], key: 'isActive', width: 100, render: (active: boolean) => <Badge status={active ? 'success' : 'default'} text={active ? '啟用中' : '未啟用'} /> },
    {
      title: '操作', key: 'actions', width: 325,
      render: (_, record) => <Space size={0} split={<span className="map-action-divider" />}>
        <Button type="link" size="small" onClick={() => { setSelectedMap(record); setDrawerOpen(true) }}>詳細資訊</Button>
        <Button type="link" size="small" onClick={() => onOpenViewer(record.map.mapCode)}>開啟 3D</Button>
        <Button type="link" size="small" onClick={() => onOpenEditor(record.map.mapCode)}>編輯地圖</Button>
        {!record.map.isActive && <Button type="link" size="small" onClick={() => handleActivate(record)}>啟用</Button>}
      </Space>,
    },
  ]

  return (
    <ConfigProvider theme={{ algorithm: theme.defaultAlgorithm, token: {
      colorPrimary: '#ee970d', colorText: '#28374b', colorBorder: '#d9dde2',
      colorBgContainer: '#ffffff', colorBgElevated: '#ffffff', borderRadius: 4,
      fontFamily: "'Segoe UI', 'Microsoft JhengHei', sans-serif",
    } }}>
      <div className="map-management-page">
        <header className="map-management-header">
          <div className="map-management-brand" aria-label="AGV-Lab">AGV<span>Lab</span></div>
          <nav className="map-management-nav" aria-label="主選單">
            <button type="button" className={`map-management-nav-item ${section === 'maps' ? 'selected' : ''}`} onClick={() => setSection('maps')}>地圖管理</button>
            <button type="button" className={`map-management-nav-item ${section === 'agvs' ? 'selected' : ''}`} onClick={() => setSection('agvs')}>AGV 管理</button>
            <button type="button" className="map-management-nav-item" onClick={() => onOpenViewer()}>3D 地圖</button>
          </nav>
          <div className="map-management-header-right">
            {activeMap && <span className="map-management-active">啟用地圖：{activeMap.map.mapName}</span>}
            <Button icon={<ReloadOutlined />} onClick={() => void loadMaps()}>重新整理</Button>
          </div>
        </header>

        <div className="map-management-breadcrumb">首頁 <span>/</span> 地圖管理 <span>/</span> {section === 'maps' ? '地圖清單' : 'AGV 管理'}</div>

        <main className="map-management-content">
          {section === 'maps' ? (
          <section className="map-management-panel" aria-label="地圖清單">
            <form className="map-management-filters" onSubmit={event => { event.preventDefault(); handleSearch() }}>
              <label htmlFor="map-code-search">地圖編碼：</label>
              <Input id="map-code-search" value={codeInput} onChange={event => setCodeInput(event.target.value)} placeholder="請輸入地圖編碼" allowClear />
              <label htmlFor="map-name-search">地圖名稱：</label>
              <Input id="map-name-search" value={nameInput} onChange={event => setNameInput(event.target.value)} placeholder="請輸入地圖名稱" allowClear />
              <div className="map-management-filter-actions">
                <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>查詢</Button>
                <Button onClick={handleReset}>重置</Button>
              </div>
            </form>

            <div className="map-management-toolbar">
              <Upload accept=".json,application/json" showUploadList={false} beforeUpload={handleImport}>
                <Button icon={<ImportOutlined />}>匯入地圖</Button>
              </Upload>
              <span>共 {filteredMaps.length} 筆地圖</span>
            </div>

            <div className="map-management-selection">
              <span className="map-management-info-icon">i</span>
              已選擇 <strong>{selectedRowKeys.length}</strong> 項
              {selectedRowKeys.length > 0 && <Button type="link" size="small" onClick={() => setSelectedRowKeys([])}>清空</Button>}
            </div>

            <Table<MapDataDto>
              className="map-management-table"
              dataSource={filteredMaps}
              columns={columns}
              rowKey={record => record.map.mapCode}
              rowSelection={{ selectedRowKeys, onChange: keys => setSelectedRowKeys(keys) }}
              loading={loading}
              locale={{ emptyText: loadError ?? '目前沒有地圖資料' }}
              pagination={{ pageSize: 10, showSizeChanger: true, showTotal: total => `共 ${total} 筆結果` }}
              scroll={{ x: 900 }}
            />
          </section>
          ) : <AgvManagement maps={mapList} mapsLoading={loading} mapLoadError={loadError} />}
        </main>

        <footer className="map-management-footer">AGV-Lab · Map Management</footer>

        <Drawer
          rootClassName="map-management-drawer"
          title="地圖詳細資訊"
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          width={420}
          styles={{
            content: { background: '#ffffff', color: '#28374b' },
            header: { background: '#ffffff', borderBottom: '1px solid #e8edf2' },
            body: { background: '#ffffff', color: '#28374b' },
          }}
        >
          {selectedMap && <>
            <Descriptions column={1} size="small" styles={{ label: { color: '#7b8794' }, content: { color: '#28374b' } }} items={[
              { key: 'code', label: '地圖編碼', children: selectedMap.map.mapCode },
              { key: 'name', label: '地圖名稱', children: selectedMap.map.mapName },
              { key: 'version', label: '地圖版本', children: `v${selectedMap.map.version}` },
              { key: 'status', label: '狀態', children: selectedMap.map.isActive ? <Tag color="success">啟用中</Tag> : <Tag>未啟用</Tag> },
              { key: 'width', label: '寬度', children: `${selectedMap.map.width} 公尺` },
              { key: 'height', label: '長度', children: `${selectedMap.map.height} 公尺` },
              { key: 'points', label: '路徑點', children: `${selectedMap.pointList?.length ?? 0} 個` },
              { key: 'objects', label: '場景物件', children: `${selectedMap.objects?.length ?? 0} 個` },
            ]} />
            <Space className="map-management-drawer-actions" wrap>
              <Button type="primary" icon={<EyeOutlined />} onClick={() => { setDrawerOpen(false); onOpenViewer(selectedMap.map.mapCode) }}>開啟 3D</Button>
              <Button icon={<EditOutlined />} onClick={() => { setDrawerOpen(false); onOpenEditor(selectedMap.map.mapCode) }}>編輯地圖</Button>
              {!selectedMap.map.isActive && <Button icon={<CheckCircleOutlined />} onClick={() => { setDrawerOpen(false); handleActivate(selectedMap) }}>啟用地圖</Button>}
            </Space>
          </>}
        </Drawer>
      </div>
    </ConfigProvider>
  )
}
