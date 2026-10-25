import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App as AntdApp, ConfigProvider, theme } from 'antd'
import zhTW from 'antd/locale/zh_TW'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ConfigProvider
      locale={zhTW}
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#FF8C00',
          colorBgElevated: '#0a1f4e',
          colorText: '#e2e8f0',
          borderRadius: 8,
        },
      }}
    >
      {/* 通知掛載於應用程式根層，切換頁面時仍維持相同設定。 */}
      <AntdApp notification={{
        placement: 'topRight',
        top: 72,
        duration: 4,
        showProgress: true,
        pauseOnHover: true,
        maxCount: 3,
      }}>
        <App />
      </AntdApp>
    </ConfigProvider>
  </StrictMode>,
)
