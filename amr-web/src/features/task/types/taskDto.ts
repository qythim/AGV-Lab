// ============================================================================
// 檔案名稱：taskDto.ts
// 模組說明：搬運任務模組的 API DTO 與 UI 選項型別定義
// ============================================================================

/** 任務與明細共用狀態列舉 */
export type TaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'

/** 任務階段類型 */
export type TaskStepType = 'PICKUP' | 'DELIVERY'

/** 建立搬運任務請求 DTO (對應後端 CreateTaskRequest) */
export interface CreateTaskRequest {
  mapCode: string
  agvCode: string | null
  startPoint: string
  targetPoint: string
  targetLevel?: number
}

/** 搬運任務主檔回應 DTO (對應後端 TaskResponse) */
export interface TaskResponse {
  taskCode: string
  agvCode: string | null
  mapCode: string
  startPoint: string
  targetPoint: string
  targetLevel: number
  status: TaskStatus
  createdAt: string
  updatedAt: string
  finishedAt: string | null
}

/** 搬運任務階段明細 DTO (對應後端 TaskDetailResponse) */
export interface TaskDetailResponse {
  id: number
  taskCode: string
  stepSeq: number
  stepType: TaskStepType
  agvCode: string
  fromPoint: string
  toPoint: string
  status: TaskStatus
  startedAt: string | null
  finishedAt: string | null
}

/** 傳遞給任務側邊欄的點位下拉選項 (避免直接耦合 map-editor 內部型別) */
export interface TaskPointOption {
  code: string
  name?: string
  type: string
}

/** 傳遞給任務側邊欄的 AGV 下拉選項 */
export interface TaskAgvOption {
  agvCode: string
  status: string
  currentPoint: string | null
}

/** 3D 場景點選模式：目前正在選取取貨點或卸貨點 */
export type TaskPointPickField = 'start' | 'target' | null
