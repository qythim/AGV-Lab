import type { MapDataDto } from '../types/mapDto'

const API_BASE = 'http://localhost:8080/api/maps'

async function readMapResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const error = await response.json().catch(() => null)
    throw new Error(error?.detail ?? `地圖操作失敗：HTTP ${response.status}`)
  }
  return response.json() as Promise<T>
}

export async function fetchActiveMap(): Promise<MapDataDto> {
  return readMapResponse(await fetch(`${API_BASE}/active`, { signal: AbortSignal.timeout(10000) }))
}
export async function fetchMapByCode(mapCode: string): Promise<MapDataDto> {
  return readMapResponse(await fetch(`${API_BASE}/${encodeURIComponent(mapCode)}`, { signal: AbortSignal.timeout(10000) }))
}
export async function fetchMapList(): Promise<MapDataDto[]> {
  const data = await readMapResponse<unknown>(await fetch(API_BASE, { signal: AbortSignal.timeout(10000) }))
  if (!Array.isArray(data)) throw new Error('地圖清單格式錯誤')
  return data
}
export async function saveMap(mapData: MapDataDto): Promise<MapDataDto> {
  return readMapResponse(await fetch(API_BASE, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(mapData),
  }))
}
export async function importMap(jsonText: string): Promise<MapDataDto> {
  return readMapResponse(await fetch(`${API_BASE}/import`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: jsonText,
  }))
}
export async function activateMap(mapCode: string, version: number): Promise<MapDataDto> {
  return readMapResponse(await fetch(`${API_BASE}/${encodeURIComponent(mapCode)}/activate`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ version }),
  }))
}
