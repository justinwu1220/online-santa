import { useEffect } from 'react'
import { Navigate } from 'react-router-dom'
import { useToast } from './toastContext'

/**
 * 願望目前無法顯示（被管理員暫時凍結或已不存在）：回到願望牆並提示。
 * 凍結的願望對外就像不存在，所以後端回 404；直接開網址、書籤或舊連結都走這裡。
 */
export function Unavailable() {
  const toast = useToast()
  useEffect(() => { toast.show('這個願望目前無法顯示', 'danger') }, [toast])
  return <Navigate to="/" replace />
}
