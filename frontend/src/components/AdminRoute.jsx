import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// Requires authenticated + ADMIN role; non-admins bounce to /dashboard.
export default function AdminRoute({ children }) {
  const { isAuthenticated, user } = useAuth()
  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (user?.role !== 'ADMIN') return <Navigate to="/dashboard" replace />
  return children
}
