import { useMemo } from 'react'
import { App } from 'antd'
import type { ReactNode } from 'react'

// 全系統共用通知：右上角顯示、倒數進度條、滑鼠懸停暫停。
export function useNotify() {
  const { notification } = App.useApp()

  return useMemo(() => ({
    success: (description: ReactNode) => notification.success({
      title: '操作成功', description, duration: 4, role: 'status',
    }),
    info: (description: ReactNode) => notification.info({
      title: '操作提示', description, duration: 4, role: 'status',
    }),
    warning: (description: ReactNode) => notification.warning({
      title: '請注意', description, duration: 6, role: 'alert',
    }),
    error: (description: ReactNode) => notification.error({
      title: '操作失敗', description, duration: 8, role: 'alert',
    }),
  }), [notification])
}