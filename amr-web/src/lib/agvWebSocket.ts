// ============================================================================
// 檔案名稱：agvWebSocket.ts
// 模組說明：RCS 原生 WebSocket 傳輸層客戶端
//           • 負責與 ws://localhost:8080/ws/agv 建立長連線與斷線自動重連
//           • 解析 FULL_SNAPSHOT、AGV_POSE、AGV_LOCKS 三種即時訊息
//           • 支援主動發送 RESYNC 同步請求
// ============================================================================

import type { AgvResponse } from '../features/map-management/types/agvDto'

const DEFAULT_WS_URL = 'ws://localhost:8080/ws/agv'
const RECONNECT_DELAY_MS = 2000

/** 初始或重連時的全量快照訊息 */
export interface FullSnapshotMessage {
  type: 'FULL_SNAPSHOT'
  agvs: AgvResponse[]
  locks: Record<string, string[]>
}

/** 車輛即時位置與狀態差量更新訊息 (20Hz) */
export interface AgvPoseMessage {
  type: 'AGV_POSE'
  agvCode: string
  currentMapCode: string
  posX: number
  posY: number
  angle: number
  status: string
  currentPoint: string
}

/** 車輛點位鎖定集合更新訊息 (事件驅動) */
export interface AgvLocksMessage {
  type: 'AGV_LOCKS'
  agvCode: string
  lockPoints: string[]
}

export type AgvRealtimeMessage =
  | FullSnapshotMessage
  | AgvPoseMessage
  | AgvLocksMessage

export interface AgvWebSocketOptions {
  url?: string
  onMessage: (message: AgvRealtimeMessage) => void
  onConnectionChange?: (connected: boolean) => void
}

export interface AgvWebSocketClient {
  /** 主動請求後端重新推送 FULL_SNAPSHOT */
  resync: () => void
  /** 關閉連線並停止自動重連 */
  disconnect: () => void
}

/** 建立並啟動 AGV 即時 WebSocket 連線 */
export function createAgvWebSocketClient(options: AgvWebSocketOptions): AgvWebSocketClient {
  const url = options.url ?? DEFAULT_WS_URL
  let socket: WebSocket | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let disposed = false

  function clearReconnectTimer() {
    if (reconnectTimer != null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function scheduleReconnect() {
    if (disposed || reconnectTimer != null) return
    reconnectTimer = setTimeout(() => {
      reconnectTimer = null
      connect()
    }, RECONNECT_DELAY_MS)
  }

  function connect() {
    if (disposed) return
    try {
      socket = new WebSocket(url)
    } catch {
      options.onConnectionChange?.(false)
      scheduleReconnect()
      return
    }

    socket.onopen = () => {
      if (disposed) {
        socket?.close()
        return
      }
      options.onConnectionChange?.(true)
    }

    socket.onmessage = (event) => {
      if (typeof event.data !== 'string') return
      try {
        const parsed = JSON.parse(event.data) as Partial<AgvRealtimeMessage>
        if (
          parsed &&
          (parsed.type === 'FULL_SNAPSHOT' ||
            parsed.type === 'AGV_POSE' ||
            parsed.type === 'AGV_LOCKS')
        ) {
          options.onMessage(parsed as AgvRealtimeMessage)
        }
      } catch {
        // 忽略格式異常之封包，維持連線穩定
      }
    }

    socket.onerror = () => {
      // 錯誤發生後由 onclose 統一觸發重連機制
    }

    socket.onclose = () => {
      options.onConnectionChange?.(false)
      socket = null
      scheduleReconnect()
    }
  }

  connect()

  return {
    resync() {
      if (socket && socket.readyState === WebSocket.OPEN) {
        socket.send(JSON.stringify({ type: 'RESYNC' }))
      }
    },
    disconnect() {
      disposed = true
      clearReconnectTimer()
      if (socket) {
        socket.onclose = null
        socket.close()
        socket = null
      }
    },
  }
}

