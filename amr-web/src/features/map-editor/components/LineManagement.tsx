import { useState } from 'react'
import { Button, Drawer, Form, InputNumber, Select, Space, Switch, Table, Popconfirm } from 'antd'
import type { LineDto } from '../../map-management/types/mapDto'

interface Props {
  edges: LineDto[]
  onChange: (edges: LineDto[]) => void
}

/** 有向路線的條件編輯；只有明確新增反向路線才開放反向通行。 */
export default function LineManagement({ edges, onChange }: Props) {
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState<string | null>(null)
  const [form] = Form.useForm<LineDto>()
  const line = edges.find(edge => edge.lineCode === selected)
  const select = (edge: LineDto) => { setSelected(edge.lineCode); form.setFieldsValue(edge) }

  const reverse = (edge: LineDto) => {
    const codes = new Set(edges.map(item => item.lineCode))
    let index = 1
    while (codes.has(`L_${String(index).padStart(3, '0')}`)) index++
    const copy: LineDto = {
      ...edge,
      lineCode: `L_${String(index).padStart(3, '0')}`,
      startPointCode: edge.endPointCode,
      endPointCode: edge.startPointCode,
      robotCarrierState: [...edge.robotCarrierState],
      allowedTravelModes: [...edge.allowedTravelModes],
    }
    onChange([...edges, copy])
    select(copy)
  }

  return <>
    <Button style={{ position: 'absolute', right: 20, top: 76, zIndex: 20 }} onClick={() => setOpen(true)}>
      路線設定 ({edges.length})
    </Button>
    <Drawer title="有向路線設定" open={open} onClose={() => setOpen(false)} size={560}>
      <p>先在地圖使用連線工具，依序選擇起點與終點。箭頭表示通行方向。</p>
      <Table<LineDto> rowKey="lineCode" size="small" dataSource={edges} pagination={{ pageSize: 6 }}
        columns={[
          { title: '路線', dataIndex: 'lineCode' },
          { title: '方向', render: (_, edge) => `${edge.startPointCode} → ${edge.endPointCode}` },
          { title: '操作', render: (_, edge) => <Button onClick={() => select(edge)}>編輯</Button> },
        ]} />
      {line && <Form form={form} layout="vertical" onFinish={values => {
        onChange(edges.map(edge => edge.lineCode === line.lineCode ? { ...edge, ...values } : edge))
      }}>
        <h3>{line.lineCode}：{line.startPointCode} → {line.endPointCode}</h3>
        <Form.Item name="robotType" label="允許車型" rules={[{ required: true }]}>
          <Select options={['ALL', 'STANDARD', 'TELESCOPIC'].map(value => ({ value, label: value }))} />
        </Form.Item>
        <Form.Item name="robotCarrierState" label="允許載貨狀態" rules={[{ required: true, type: 'array', min: 1, message: '至少選擇一種狀態' }]}>
          <Select mode="multiple" options={[{ value: 'EMPTY', label: '空載' }, { value: 'LOADED', label: '載貨' }]} />
        </Form.Item>
        <Form.Item name="allowedTravelModes" label="允許行進方式" rules={[{ required: true, type: 'array', min: 1, message: '至少選擇一種行進方式' }]}>
          <Select mode="multiple" options={[{ value: 'FORWARD', label: '前進' }, { value: 'REVERSE', label: '倒車' }]} />
        </Form.Item>
        <Form.Item name="maxSpeed" label="最高速度 (m/s)" rules={[{ required: true, type: 'number', min: 0.01 }]}>
          <InputNumber min={0.01} step={0.1} />
        </Form.Item>
        <Form.Item name="stoppable" label="允許規劃中途停等" valuePropName="checked"><Switch /></Form.Item>
        <Space wrap>
          <Button type="primary" htmlType="submit">套用條件</Button>
          <Button disabled={edges.some(edge => edge.startPointCode === line.endPointCode && edge.endPointCode === line.startPointCode)}
            onClick={() => reverse(line)}>新增反向路線</Button>
          <Popconfirm title="刪除此方向的路線？" onConfirm={() => {
            onChange(edges.filter(edge => edge.lineCode !== line.lineCode)); setSelected(null)
          }}><Button danger>刪除路線</Button></Popconfirm>
        </Space>
        <p>套用後請按工具列「儲存」寫入後端。倒車仍遵守此路線的起點 → 終點。</p>
      </Form>}
    </Drawer>
  </>
}
