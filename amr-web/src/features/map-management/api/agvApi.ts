import type { AgvResponse, CreateAgvRequest, PlaceAgvRequest } from '../types/agvDto'

const API_BASE = 'http://localhost:8080/api/agvs'

/** 讀取 RCS 回應；失敗時優先顯示後端提供的錯誤原因。 */
async function readResponse<T>(response: Response, action: string): Promise<T> {
  if (!response.ok) {
    const problem: unknown = await response.json().catch(() => null)
    const detail = problem && typeof problem === 'object' && 'detail' in problem
      ? problem.detail
      : null
    throw new Error(typeof detail === 'string' && detail.trim()
      ? detail
      : `${action}失敗：HTTP ${response.status}`)
  }

  return await response.json() as T
}

/** 取得所有已登錄的 AGV。 */
export async function fetchAgvList(): Promise<AgvResponse[]> {
  const response = await fetch(API_BASE, { signal: AbortSignal.timeout(5000) })
  const data = await readResponse<unknown>(response, '載入 AGV 清單')
  if (!Array.isArray(data)) {
    throw new Error('AGV 清單格式錯誤，預期為陣列')
  }
  return data as AgvResponse[]
}

/** 登錄新 AGV；位置由後續的放置操作指定。 */
export async function createAgv(request: CreateAgvRequest): Promise<AgvResponse> {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return await readResponse<AgvResponse>(response, '登錄 AGV')
}

/** 將已登錄的 AGV 放到指定地圖點位。 */
export async function placeAgv(agvCode: string, request: PlaceAgvRequest): Promise<AgvResponse> {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(agvCode)}/place`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return await readResponse<AgvResponse>(response, '放置 AGV')
}

/** 移除 AGV 的地圖位置，保留車輛登錄資料。 */
export async function unplaceAgv(agvCode: string): Promise<AgvResponse> {
  const response = await fetch(`${API_BASE}/${encodeURIComponent(agvCode)}/place`, {
    method: 'DELETE',
  })
  return await readResponse<AgvResponse>(response, '移出 AGV')
}
