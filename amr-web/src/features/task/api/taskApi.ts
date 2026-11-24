// ============================================================================
// 檔案名稱：taskApi.ts
// 模組說明：RCS 後端任務管理 REST API 呼叫封裝 (/api/tasks)
// ============================================================================

import type {
  CreateTaskRequest,
  TaskDetailResponse,
  TaskResponse,
  TaskStatus,
} from '../types/taskDto'

const API_BASE = 'http://localhost:8080/api/tasks'

/** 解析 RCS 回應；若請求失敗則優先顯示後端回傳的錯誤訊息 */
async function readResponse<T>(response: Response, action: string): Promise<T> {
  if (!response.ok) {
    const problem: unknown = await response.json().catch(() => null)
    let reason: string | null = null
    if (problem && typeof problem === 'object') {
      if ('detail' in problem && typeof problem.detail === 'string' && problem.detail.trim()) {
        reason = problem.detail
      } else if ('message' in problem && typeof problem.message === 'string' && problem.message.trim()) {
        reason = problem.message
      }
    }
    throw new Error(reason ?? `${action}失敗：HTTP ${response.status}`)
  }

  return await response.json() as T
}

/** 取得搬運任務清單 (可選傳入 status 過濾條件) */
export async function fetchTasks(status?: TaskStatus): Promise<TaskResponse[]> {
  const url = status ? `${API_BASE}?status=${encodeURIComponent(status)}` : API_BASE
  const response = await fetch(url, { signal: AbortSignal.timeout(5000) })
  const data = await readResponse<unknown>(response, '載入任務清單')
  if (!Array.isArray(data)) {
    throw new Error('任務清單格式錯誤，預期為陣列')
  }
  return data as TaskResponse[]
}

/** 取得單筆任務的階段執行明細 (rcs_task_detail) */
export async function fetchTaskDetails(taskCode: string): Promise<TaskDetailResponse[]> {
  const response = await fetch(
    `${API_BASE}/${encodeURIComponent(taskCode)}/details`,
    { signal: AbortSignal.timeout(5000) },
  )
  const data = await readResponse<unknown>(response, '載入任務階段明細')
  if (!Array.isArray(data)) {
    throw new Error('任務階段明細格式錯誤，預期為陣列')
  }
  return data as TaskDetailResponse[]
}

/** 建立新的搬運任務 (agvCode 為 null 時由後端自動指派) */
export async function createTask(request: CreateTaskRequest): Promise<TaskResponse> {
  const response = await fetch(API_BASE, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
  return await readResponse<TaskResponse>(response, '建立搬運任務')
}

/** 取消指定的 PENDING 或 RUNNING 任務 */
export async function cancelTask(taskCode: string): Promise<TaskResponse> {
  const response = await fetch(
    `${API_BASE}/${encodeURIComponent(taskCode)}/cancel`,
    { method: 'POST' },
  )
  return await readResponse<TaskResponse>(response, '取消任務')
}
