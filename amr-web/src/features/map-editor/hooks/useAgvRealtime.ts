// ============================================================================
// 檔案名稱：useAgvRealtime.ts
// 模組說明：AGV 即時狀態與點位鎖定 Hook
//           • 透過 Mutable Ref 儲存 20Hz 高頻座標，供 Three.js useFrame 線性插值讀取，
//             避免每次座標微調都觸發整個 3D 場景 React 重新渲染
//           • 僅於初始快照、車輛新增/狀態改變或點位鎖定變動時更新 React State
// ============================================================================

import { useEffect, useMemo, useRef, useState } from 'react'
import type { RefObject } from 'react'
import { fetchAgvList } from '../../map-management/api/agvApi'
import type { AgvResponse } from '../../map-management/types/agvDto'
import { createAgvWebSocketClient } from '../../../lib/agvWebSocket'
import type { AgvRealtimeMessage } from '../../../lib/agvWebSocket'

/** 供 Three.js 60fps 渲染迴圈直接讀取之高頻車姿資料 */
export interface AgvLivePose {
  posX: number
  posY: number
  angle: number
  speed: number
  status: string
  currentPoint: string | null
  currentMapCode: string | null
}

export interface UseAgvRealtimeResult {
  agvs: AgvResponse[]
  agvLoading: boolean
  agvError: string | null
  wsConnected: boolean
  /** 高頻座標參照表 (Key = agvCode)，供 Agv 3D 元件於 useFrame 中讀取並進行線性插值 */
  poseMapRef: RefObject<Map<string, AgvLivePose>>
  /** 目前被鎖定的點位對應表 (Key = pointCode, Value = 持有該點位的 agvCode) */
  lockedPointOwnerMap: ReadonlyMap<string, string>
}

export function useAgvRealtime(mapCode?: string): UseAgvRealtimeResult {
  const [agvs, setAgvs] = useState<AgvResponse[]>([])
  const [agvLoading, setAgvLoading] = useState(true)
  const [agvError, setAgvError] = useState<string | null>(null)
  const [wsConnected, setWsConnected] = useState(false)
  const [locksByAgv, setLocksByAgv] = useState<Record<string, string[]>>({})

  const poseMapRef = useRef<Map<string, AgvLivePose>>(new Map())

  // 1. 先透過 REST API 載入初始車輛清單作為保底 (若 WebSocket 尚未就緒仍可顯示靜態車輛)
  useEffect(() => {
    let cancelled = false
    fetchAgvList()
      .then((list) => {
        if (cancelled) return
        setAgvs(list)
        for (const agv of list) {
          if (agv.posX != null && agv.posY != null) {
            poseMapRef.current.set(agv.agvCode, {
              posX: agv.posX,
              posY: agv.posY,
              angle: agv.angle ?? 0,
              speed: 0,
              status: agv.status,
              currentPoint: agv.currentPoint,
              currentMapCode: agv.currentMapCode,
            })
          }
        }
        setAgvError(null)
      })
      .catch(() => {
        if (cancelled) return
        setAgvError('無法載入 AGV，地圖仍可使用。')
      })
      .finally(() => {
        if (!cancelled) setAgvLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [mapCode])

  // 2. 建立 WebSocket 長連線，接收 FULL_SNAPSHOT、AGV_POSE 與 AGV_LOCKS
  useEffect(() => {
    const handleMessage = (msg: AgvRealtimeMessage) => {
      if (msg.type === 'FULL_SNAPSHOT') {
        const snapshotAgvs = Array.isArray(msg.agvs) ? msg.agvs : []
        for (const agv of snapshotAgvs) {
          const existingPose = poseMapRef.current.get(agv.agvCode)
          if (agv.posX != null && agv.posY != null) {
            poseMapRef.current.set(agv.agvCode, {
              // 若已有即時高頻座標且地圖相同，保留最新即時座標避免跳回資料庫舊點
              posX: existingPose?.currentMapCode === agv.currentMapCode ? existingPose.posX : agv.posX,
              posY: existingPose?.currentMapCode === agv.currentMapCode ? existingPose.posY : agv.posY,
              angle: existingPose?.currentMapCode === agv.currentMapCode ? existingPose.angle : (agv.angle ?? 0),
              speed: existingPose?.currentMapCode === agv.currentMapCode ? existingPose.speed : 0,
              status: existingPose?.status ?? agv.status,
              currentPoint: existingPose?.currentPoint ?? agv.currentPoint,
              currentMapCode: agv.currentMapCode,
            })
          }
        }
        setAgvs(snapshotAgvs)
        setLocksByAgv(msg.locks ?? {})
        setAgvLoading(false)
        setAgvError(null)
        return
      }

      if (msg.type === 'AGV_POSE') {
        // 立即更新 Mutable Ref 供 Three.js useFrame 60fps 線性插值讀取 (零 React 重繪)
        poseMapRef.current.set(msg.agvCode, {
          posX: msg.posX,
          posY: msg.posY,
          angle: msg.angle,
          speed: msg.speed ?? 0,
          status: msg.status,
          currentPoint: msg.currentPoint,
          currentMapCode: msg.currentMapCode,
        })

        // 僅當車輛尚未在清單中、尚未有初始座標、地圖改變或狀態 (IDLE/RUNNING) 改變時才更新 React State
        setAgvs((prev) => {
          const idx = prev.findIndex((item) => item.agvCode === msg.agvCode)
          if (idx === -1) {
            return [
              ...prev,
              {
                agvCode: msg.agvCode,
                agvName: msg.agvCode,
                agvType: 'STANDARD',
                status: msg.status,
                currentMapCode: msg.currentMapCode,
                currentPoint: msg.currentPoint,
                posX: msg.posX,
                posY: msg.posY,
                angle: msg.angle,
              },
            ]
          }
          const current = prev[idx]
          const needsStateUpdate =
            current.posX == null ||
            current.posY == null ||
            current.currentMapCode !== msg.currentMapCode ||
            current.status !== msg.status
          if (!needsStateUpdate) {
            return prev
          }
          const next = [...prev]
          next[idx] = {
            ...current,
            status: msg.status,
            currentMapCode: msg.currentMapCode,
            currentPoint: msg.currentPoint,
            posX: current.posX ?? msg.posX,
            posY: current.posY ?? msg.posY,
            angle: current.angle ?? msg.angle,
          }
          return next
        })
        return
      }

      if (msg.type === 'AGV_LOCKS') {
        setLocksByAgv((prev) => ({
          ...prev,
          [msg.agvCode]: Array.isArray(msg.lockPoints) ? msg.lockPoints : [],
        }))
      }
    }

    const client = createAgvWebSocketClient({
      onMessage: handleMessage,
      onConnectionChange: setWsConnected,
    })

    return () => {
      client.disconnect()
    }
  }, [])

  // 將 { agvCode: [pointCode...] } 轉為 Map<pointCode, agvCode> 供 PathTopology O(1) 查詢渲染
  const lockedPointOwnerMap = useMemo(() => {
    const map = new Map<string, string>()
    for (const [agvCode, pointCodes] of Object.entries(locksByAgv)) {
      if (!Array.isArray(pointCodes)) continue
      for (const code of pointCodes) {
        map.set(code, agvCode)
      }
    }
    return map
  }, [locksByAgv])

  return {
    agvs,
    agvLoading,
    agvError,
    wsConnected,
    poseMapRef,
    lockedPointOwnerMap,
  }
}

