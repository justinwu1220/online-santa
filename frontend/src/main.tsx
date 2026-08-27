import { QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import './index.css'
import { AuthProvider } from './lib/auth'
import { AuthHeaderBridge } from './lib/AuthHeaderBridge'
import { queryClient } from './lib/queryClient'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {/* AuthProvider 在外層，AuthHeaderBridge 需要同時用到它與 QueryClient */}
    <AuthProvider>
      <QueryClientProvider client={queryClient}>
        <AuthHeaderBridge />
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </AuthProvider>
  </StrictMode>,
)
