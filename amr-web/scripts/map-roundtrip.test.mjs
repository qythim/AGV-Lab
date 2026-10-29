import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import ts from 'typescript'

// 使用專案 TypeScript 編譯器載入純 mapper，無需新增測試框架。
async function moduleAt(path) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8')
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext } })
  return import('data:text/javascript;base64,' + Buffer.from(outputText).toString('base64'))
}
const { mapDtoToSceneData } = await moduleAt('../src/features/map-editor/utils/mapCoordinateMapper.ts')
const { sceneDataToMapDto } = await moduleAt('../src/features/map-editor/utils/sceneDataToMapDto.ts')
const data = {
  map: { mapCode: 'M', mapName: '測試', version: 3, isActive: false, width: 20, height: 15 },
  pointList: [
    { pointCode: 'A', pointName: '起點', pointType: 'NORMAL', x: 1, y: 2 },
    { pointCode: 'B', pointName: '終點', pointType: 'NORMAL', x: 3, y: 4 },
  ],
  lineList: [{
    lineCode: 'L_001', startPointCode: 'A', endPointCode: 'B', robotType: 'TELESCOPIC',
    robotCarrierState: ['EMPTY'], maxSpeed: 0.5, stoppable: false, allowedTravelModes: ['REVERSE'],
  }],
  objects: [],
}
test('有向路線經載入、移動節點、儲存後仍保留方向與條件', () => {
  const scene = mapDtoToSceneData(data)
  scene.points[0].position = [8, 0.05, 9]
  const saved = sceneDataToMapDto(scene, data)
  assert.deepEqual(saved.lineList, data.lineList)
  assert.equal(saved.pointList[0].x, 8)
  assert.equal(saved.pointList[0].y, 9)
  assert.equal(saved.pointList[0].pointName, '起點')
  assert.equal(saved.map.version, 3)
  assert.equal(saved.lineList.length, 1)
})
test('沒有 lineList 的連線時不推測或自動建立路線', () => {
  const scene = mapDtoToSceneData({ ...data, lineList: [] })
  assert.equal(scene.edges.length, 0)
  assert.equal(sceneDataToMapDto(scene, data).lineList.length, 0)
})
test('雙向路線保持兩筆獨立資料', () => {
  const reverse = { ...data.lineList[0], lineCode: 'L_002', startPointCode: 'B', endPointCode: 'A', maxSpeed: 1.2 }
  const input = { ...data, lineList: [...data.lineList, reverse] }
  assert.deepEqual(sceneDataToMapDto(mapDtoToSceneData(input), input).lineList, input.lineList)
})
test('不存在的端點直接報錯，不靜默捨棄路線', () => {
  assert.throws(() => mapDtoToSceneData({ ...data, pointList: [] }), /路線參照不存在的點位/)
})
