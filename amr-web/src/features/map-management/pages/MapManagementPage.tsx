// ============================================================================
// 檔案名稱：MapManagementPage.tsx
// 模組說明：地圖管理介面 (Ant Design)
//           功能：地圖清單表格、地圖統計卡片、啟用/切換地圖、進入 3D 檢視
//           主題色：#05102b (深海軍藍) + #FF8C00 (科技橘)
// ============================================================================

import { useEffect, useState, useCallback } from 'react'
import {
  Badge,
  Button,
  Card,
  Col,
  ConfigProvider,
  Descriptions,
  Drawer,
  Layout,
  Menu,
  Modal,
  Row,
  Statistic,
  Table,
  Tag,
  Tooltip,
  Typography,
  Upload,
  message,
  theme,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  ApartmentOutlined,
  BarChartOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloudDownloadOutlined,
  DatabaseOutlined,
  EyeOutlined,
  ImportOutlined,
  PartitionOutlined,
  RadarChartOutlined,
  ReloadOutlined,
  RocketOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { fetchMapList, saveMap } from '../api/mapApi'
import type { MapDataDto } from '../types/mapDto'

const { Header, Content, Footer, Sider } = Layout
const { Text } = Typography

// ─────────────────────────────────────────────────────────────────────────────
// 主題色常數定義
// ─────────────────────────────────────────────────────────────────────────────
const COLOR_PRIMARY = '#FF8C00'   // 主題橘：按鈕、強調元素
const COLOR_BG = '#05102b'   // 深海軍藍：Header / Footer / 側欄
const COLOR_BG_CARD = '#0a1f4e'   // 卡片背景
const COLOR_BG_TABLE = '#071535'   // 表格背景
const COLOR_BORDER = '#1e3a5f'   // 邊框色
const COLOR_TEXT = '#e2e8f0'   // 主文字色
const COLOR_TEXT_MUTE = '#94a3b8'   // 次要文字色

// ─────────────────────────────────────────────────────────────────────────────
// 本地 Mock 地圖清單 (後端未上線時顯示示範資料)
// ─────────────────────────────────────────────────────────────────────────────
const MOCK_MAP_LIST: MapDataDto[] = [
  {
    map: { mapCode: 'FACTORY_01', mapName: 'A 廠區主倉庫', version: 5, isActive: true, width: 80, height: 60 },
    points: Array(24).fill(null),
    objects: Array(18).fill(null),
  },
  {
    map: { mapCode: 'FACTORY_02', mapName: 'B 廠區冷鏈倉庫', version: 3, isActive: false, width: 50, height: 40 },
    points: Array(16).fill(null),
    objects: Array(12).fill(null),
  },
  {
    map: { mapCode: 'STAGING_01', mapName: '暫存備用地圖 V1', version: 1, isActive: false, width: 30, height: 25 },
    points: Array(8).fill(null),
    objects: Array(5).fill(null),
  },
  {
    map: { mapCode: 'TEST_LAB', mapName: '測試實驗室地圖', version: 2, isActive: false, width: 20, height: 15 },
    points: Array(6).fill(null),
    objects: Array(4).fill(null),
  },
]

// ─────────────────────────────────────────────────────────────────────────────
// 地圖清單表格欄位定義
// ─────────────────────────────────────────────────────────────────────────────
function buildColumns(
  onView: (record: MapDataDto) => void,
  onActivate: (record: MapDataDto) => void,
  onOpen3D: (record: MapDataDto) => void,
): ColumnsType<MapDataDto> {
  return [
    {
      title: '地圖編號',
      dataIndex: ['map', 'mapCode'],
      key: 'mapCode',
      render: (code: string) => (
        <Text style={{ color: COLOR_PRIMARY, fontFamily: 'monospace', fontWeight: 600 }}>
          {code}
        </Text>
      ),
    },
    {
      title: '地圖名稱',
      dataIndex: ['map', 'mapName'],
      key: 'mapName',
      render: (name: string) => <Text style={{ color: COLOR_TEXT }}>{name}</Text>,
    },
    {
      title: '版本',
      dataIndex: ['map', 'version'],
      key: 'version',
      width: 70,
      align: 'center',
      render: (v: number) => (
        <Tag color="#1e3a5f" style={{ color: COLOR_TEXT_MUTE }}>v{v}</Tag>
      ),
    },
    {
      title: '狀態',
      dataIndex: ['map', 'isActive'],
      key: 'isActive',
      width: 100,
      align: 'center',
      render: (active: boolean) =>
        active ? (
          <Badge status="success" text={<Text style={{ color: '#10b981', fontSize: 12 }}>運行中</Text>} />
        ) : (
          <Badge status="default" text={<Text style={{ color: COLOR_TEXT_MUTE, fontSize: 12 }}>待機</Text>} />
        ),
    },
    {
      title: '場地尺寸',
      key: 'size',
      width: 120,
      align: 'center',
      render: (_: unknown, record: MapDataDto) => (
        <Text style={{ color: COLOR_TEXT_MUTE, fontSize: 12 }}>
          {record.map.width}m × {record.map.height}m
        </Text>
      ),
    },
    {
      title: '路徑點',
      key: 'points',
      width: 80,
      align: 'center',
      render: (_: unknown, record: MapDataDto) => (
        <Text style={{ color: '#60a5fa' }}>{record.points?.length ?? 0}</Text>
      ),
    },
    {
      title: '場景物件',
      key: 'objects',
      width: 90,
      align: 'center',
      render: (_: unknown, record: MapDataDto) => (
        <Text style={{ color: '#a78bfa' }}>{record.objects?.length ?? 0}</Text>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_: unknown, record: MapDataDto) => (
        <div style={{ display: 'flex', gap: 8 }}>
          <Tooltip title="查看詳細資訊">
            <Button
              size="small" icon={<EyeOutlined />}
              onClick={() => onView(record)}
              style={{ background: COLOR_BG_CARD, border: `1px solid ${COLOR_BORDER}`, color: COLOR_TEXT }}
            />
          </Tooltip>
          <Tooltip title="在 3D 檢視器中開啟">
            <Button
              size="small" icon={<RadarChartOutlined />}
              onClick={() => onOpen3D(record)}
              style={{ background: '#1e3a5f', border: `1px solid #3b82f6`, color: '#60a5fa' }}
            />
          </Tooltip>
          {!record.map.isActive && (
            <Tooltip title="設為啟用地圖">
              <Button
                size="small" icon={<CheckCircleOutlined />}
                onClick={() => onActivate(record)}
                style={{ background: '#1a2f1a', border: `1px solid #10b981`, color: '#10b981' }}
              >
                啟用
              </Button>
            </Tooltip>
          )}
          {record.map.isActive && (
            <Tag color="success" style={{ margin: 0, alignSelf: 'center', fontSize: 11 }}>
              ✓ 啟用中
            </Tag>
          )}
        </div>
      ),
    },
  ]
}

// ─────────────────────────────────────────────────────────────────────────────
// 地圖管理主頁面元件
// ─────────────────────────────────────────────────────────────────────────────
interface MapManagementPageProps {
  /** 切換至 3D 檢視器的回呼，傳入指定地圖編號 */
  onOpenViewer: (mapCode?: string) => void
}

export default function MapManagementPage({ onOpenViewer }: MapManagementPageProps) {
  const [mapList, setMapList] = useState<MapDataDto[]>([])
  const [loading, setLoading] = useState(true)
  const [selectedMap, setSelectedMap] = useState<MapDataDto | null>(null)
  const [drawerOpen, setDrawerOpen] = useState(false)
  const [menuKey, setMenuKey] = useState('maps')

  // 載入地圖清單
  const loadMaps = useCallback(async () => {
    setLoading(true)
    try {
      const list = await fetchMapList()
      // 後端無資料時顯示示範清單
      setMapList(list.length > 0 ? list : MOCK_MAP_LIST)
    } catch {
      setMapList(MOCK_MAP_LIST)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { loadMaps() }, [loadMaps])

  // 計算統計卡片資料 (加入可選鏈以防資料結構缺失)
  const activeMap   = mapList.find(m => m.map?.isActive)
  const totalPoints = mapList.reduce((s, m) => s + (m.points?.length || 0), 0)
  const totalObjs   = mapList.reduce((s, m) => s + (m.objects?.length || 0), 0)

  // 啟用地圖 (設定 isActive 後儲存)
  const handleActivate = async (record: MapDataDto) => {
    Modal.confirm({
      title: `確認啟用地圖？`,
      content: `將「${record.map.mapName}」設為系統當前使用地圖，其他地圖將自動停用。`,
      okText: '確認啟用',
      cancelText: '取消',
      okButtonProps: { style: { background: COLOR_PRIMARY, border: 'none' } },
      onOk: async () => {
        try {
          // 更新本地狀態 (生產環境應透過 API 更新後重新載入)
          const updatedList = mapList.map(m => ({
            ...m,
            map: { ...m.map, isActive: m.map.mapCode === record.map.mapCode },
          }))
          setMapList(updatedList)
          await saveMap({ ...record, map: { ...record.map, isActive: true } })
          message.success(`已啟用「${record.map.mapName}」`)
        } catch {
          message.warning('後端連線失敗，已更新本地狀態')
        }
      },
    })
  }

  // JSON 檔案匯入處理
  const handleImport = (file: File) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data: MapDataDto = JSON.parse(e.target?.result as string)
        if (!data.map?.mapCode) throw new Error('格式錯誤')
        setMapList(prev => {
          const exists = prev.findIndex(m => m.map.mapCode === data.map.mapCode)
          if (exists >= 0) {
            const updated = [...prev]
            updated[exists] = data
            return updated
          }
          return [...prev, data]
        })
        message.success(`成功匯入地圖：${data.map.mapName}`)
      } catch {
        message.error('JSON 格式無效，請檢查地圖資料結構')
      }
    }
    reader.readAsText(file)
    return false // 阻止 Upload 元件自動上傳
  }

  // 側邊選單項目
  const menuItems = [
    { key: 'maps', icon: <DatabaseOutlined />, label: '地圖清單' },
    { key: 'topology', icon: <ApartmentOutlined />, label: '拓撲管理' },
    { key: 'analytics', icon: <BarChartOutlined />, label: '統計分析' },
    { key: 'tasks', icon: <PartitionOutlined />, label: '任務配置' },
  ]

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: COLOR_PRIMARY,
          colorBgContainer: COLOR_BG_CARD,
          colorBgLayout: COLOR_BG,
          colorBorder: COLOR_BORDER,
          colorText: COLOR_TEXT,
          colorTextSecondary: COLOR_TEXT_MUTE,
          borderRadius: 8,
          fontFamily: "'Inter', 'Segoe UI', sans-serif",
        },
      }}
    >
      <Layout style={{ minHeight: '100vh', background: COLOR_BG }}>
        {/* ── Header ─────────────────────────────────────────────────── */}
        <Header style={{
          background: COLOR_BG,
          borderBottom: `1px solid ${COLOR_BORDER}`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 24px',
          height: 60,
          position: 'sticky',
          top: 0,
          zIndex: 100,
        }}>
          {/* Logo 區 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <RocketOutlined style={{ fontSize: 24, color: COLOR_PRIMARY }} />
            <span style={{ color: COLOR_PRIMARY, fontWeight: 800, fontSize: 18, letterSpacing: 1 }}>
              AGV-Lab
            </span>
            <span style={{ color: COLOR_TEXT_MUTE, fontSize: 13, marginLeft: 4 }}>
              / 地圖管理系統
            </span>
          </div>
          {/* 頂部操作區 */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            {activeMap && (
              <Tag color="#FF8C00" style={{ fontSize: 12 }}>
                <CheckCircleOutlined /> 啟用中：{activeMap.map.mapName}
              </Tag>
            )}
            <Button
              icon={<ReloadOutlined />}
              size="small"
              onClick={loadMaps}
              style={{ background: 'transparent', border: `1px solid ${COLOR_BORDER}`, color: COLOR_TEXT_MUTE }}
            >
              重新整理
            </Button>
            <Button
              type="primary"
              icon={<RadarChartOutlined />}
              onClick={() => onOpenViewer()}
              style={{ background: COLOR_PRIMARY, border: 'none', fontWeight: 600 }}
            >
              開啟 3D 檢視器
            </Button>
          </div>
        </Header>

        <Layout>
          {/* ── Sider (側邊選單) ─────────────────────────────────────── */}
          <Sider
            width={200}
            style={{
              background: COLOR_BG,
              borderRight: `1px solid ${COLOR_BORDER}`,
            }}
          >
            <Menu
              mode="inline"
              selectedKeys={[menuKey]}
              onSelect={({ key }) => setMenuKey(key)}
              items={menuItems}
              style={{
                background: 'transparent',
                border: 'none',
                color: COLOR_TEXT,
                paddingTop: 16,
              }}
            />
          </Sider>

          {/* ── Content ──────────────────────────────────────────────── */}
          <Content style={{ padding: 24, background: `${COLOR_BG}e0` }}>
            {/* 統計卡片區 */}
            <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
              {[
                {
                  title: '總地圖數',
                  value: mapList.length,
                  icon: <DatabaseOutlined style={{ color: COLOR_PRIMARY }} />,
                  color: COLOR_PRIMARY,
                },
                {
                  title: '啟用中地圖',
                  value: mapList.filter(m => m.map?.isActive).length,
                  icon: <CheckCircleOutlined style={{ color: '#10b981' }} />,
                  color: '#10b981',
                },
                {
                  title: '路徑點總數',
                  value: totalPoints,
                  icon: <PartitionOutlined style={{ color: '#60a5fa' }} />,
                  color: '#60a5fa',
                },
                {
                  title: '場景物件總數',
                  value: totalObjs,
                  icon: <ApartmentOutlined style={{ color: '#a78bfa' }} />,
                  color: '#a78bfa',
                },
              ].map((stat) => (
                <Col key={stat.title} xs={24} sm={12} lg={6}>
                  <Card
                    style={{
                      background: COLOR_BG_CARD,
                      border: `1px solid ${COLOR_BORDER}`,
                      borderRadius: 12,
                    }}
                    styles={{ body: { padding: '18px 20px' } }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                      <Statistic
                        title={<span style={{ color: COLOR_TEXT_MUTE, fontSize: 12 }}>{stat.title}</span>}
                        value={stat.value}
                        styles={{ content: { color: stat.color, fontSize: 28, fontWeight: 700 } }}
                      />
                      <div style={{ fontSize: 28 }}>{stat.icon}</div>
                    </div>
                  </Card>
                </Col>
              ))}
            </Row>

            {/* 地圖清單主表格 */}
            <Card
              style={{
                background: COLOR_BG_TABLE,
                border: `1px solid ${COLOR_BORDER}`,
                borderRadius: 12,
              }}
              styles={{ body: { padding: 0 } }}
              title={
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '4px 0' }}>
                  <DatabaseOutlined style={{ color: COLOR_PRIMARY }} />
                  <span style={{ color: COLOR_TEXT, fontWeight: 600 }}>地圖清單</span>
                  <Badge count={mapList.length} style={{ background: COLOR_PRIMARY }} />
                </div>
              }
              extra={
                <div style={{ display: 'flex', gap: 8 }}>
                  <Upload
                    accept=".json"
                    showUploadList={false}
                    beforeUpload={handleImport}
                  >
                    <Button
                      size="small"
                      icon={<ImportOutlined />}
                      style={{ background: 'transparent', border: `1px solid ${COLOR_BORDER}`, color: COLOR_TEXT_MUTE }}
                    >
                      匯入 JSON
                    </Button>
                  </Upload>
                  <Button
                    size="small"
                    icon={<CloudDownloadOutlined />}
                    style={{ background: 'transparent', border: `1px solid ${COLOR_BORDER}`, color: COLOR_TEXT_MUTE }}
                  >
                    匯出
                  </Button>
                </div>
              }
            >
              <Table<MapDataDto>
                dataSource={mapList}
                columns={buildColumns(
                  (r) => { setSelectedMap(r); setDrawerOpen(true) },
                  handleActivate,
                  (r) => onOpenViewer(r.map.mapCode),
                )}
                rowKey={(r) => r.map.mapCode}
                loading={loading}
                pagination={{ pageSize: 8, size: 'small' }}
                scroll={{ x: 900 }}
                rowClassName={(record) =>
                  record.map.isActive ? 'active-map-row' : ''
                }
                style={{ background: 'transparent' }}
              />
            </Card>
          </Content>
        </Layout>

        {/* ── Footer ─────────────────────────────────────────────────── */}
        <Footer style={{
          background: COLOR_BG,
          borderTop: `1px solid ${COLOR_BORDER}`,
          textAlign: 'center',
          padding: '14px 24px',
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Text style={{ color: COLOR_TEXT_MUTE, fontSize: 12 }}>
              <RocketOutlined style={{ color: COLOR_PRIMARY }} /> AGV-Lab Map Management System
            </Text>
            <Text style={{ color: COLOR_TEXT_MUTE, fontSize: 12 }}>
              <ClockCircleOutlined /> 最後更新：{new Date().toLocaleString('zh-TW')}
            </Text>
            <Text style={{ color: COLOR_TEXT_MUTE, fontSize: 12 }}>
              <WarningOutlined style={{ color: '#f59e0b' }} /> React 19 · Three.js · Ant Design · Vite
            </Text>
          </div>
        </Footer>
      </Layout>

      {/* ── 地圖詳情 Drawer ─────────────────────────────────────────── */}
      <Drawer
        title={
          <span style={{ color: COLOR_PRIMARY, fontWeight: 700 }}>
            📋 地圖詳細資訊
          </span>
        }
        placement="right"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        styles={{
          wrapper: { width: 420 },
          body: { background: COLOR_BG_CARD, padding: 24 },
          header: { background: COLOR_BG, borderBottom: `1px solid ${COLOR_BORDER}` },
        }}
      >
        {selectedMap && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
            <Descriptions
              column={1}
              size="small"
              styles={{ label: { color: COLOR_TEXT_MUTE, width: 100 }, content: { color: COLOR_TEXT } }}
              items={[
                { key: 'code', label: '地圖編號', children: <Text style={{ color: COLOR_PRIMARY, fontFamily: 'monospace' }}>{selectedMap.map.mapCode}</Text> },
                { key: 'name', label: '地圖名稱', children: selectedMap.map.mapName },
                { key: 'version', label: '版本號', children: `v${selectedMap.map.version}` },
                { key: 'status', label: '狀態', children: selectedMap.map.isActive ? <Tag color="success">啟用中</Tag> : <Tag color="default">待機</Tag> },
                { key: 'width', label: '場地寬度', children: `${selectedMap.map.width} 公尺` },
                { key: 'height', label: '場地長度', children: `${selectedMap.map.height} 公尺` },
                { key: 'points', label: '路徑點數', children: `${selectedMap.points?.length ?? 0} 個` },
                { key: 'objects', label: '場景物件', children: `${selectedMap.objects?.length ?? 0} 個` },
              ]}
            />
            <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
              <Button
                type="primary"
                icon={<RadarChartOutlined />}
                onClick={() => { setDrawerOpen(false); onOpenViewer(selectedMap.map.mapCode) }}
                style={{ background: COLOR_PRIMARY, border: 'none', flex: 1 }}
              >
                在 3D 中開啟
              </Button>
              {!selectedMap.map.isActive && (
                <Button
                  icon={<CheckCircleOutlined />}
                  onClick={() => { setDrawerOpen(false); handleActivate(selectedMap) }}
                  style={{ background: '#064e3b', border: `1px solid #10b981`, color: '#10b981', flex: 1 }}
                >
                  設為啟用
                </Button>
              )}
            </div>
          </div>
        )}
      </Drawer>

      {/* 啟用地圖列高亮樣式 */}
      <style>{`
        .active-map-row td {
          background: rgba(255, 140, 0, 0.06) !important;
          border-left: 2px solid #FF8C00;
        }
        .ant-table-wrapper .ant-table {
          background: transparent !important;
        }
        .ant-table-wrapper .ant-table-thead > tr > th {
          background: #071535 !important;
          color: #94a3b8 !important;
          border-bottom: 1px solid #1e3a5f !important;
          font-size: 12px;
          font-weight: 600;
          letter-spacing: 0.5px;
        }
        .ant-table-wrapper .ant-table-tbody > tr > td {
          background: transparent !important;
          border-bottom: 1px solid #0f2547 !important;
          padding: 10px 16px !important;
        }
        .ant-table-wrapper .ant-table-tbody > tr:hover > td {
          background: rgba(30, 58, 95, 0.5) !important;
        }
        .ant-menu-item-selected {
          background: rgba(255, 140, 0, 0.12) !important;
          border-right: 2px solid #FF8C00;
        }
      `}</style>
    </ConfigProvider>
  )
}
