import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

// requireAuth=true  → redirect to /login if not authenticated
// requireAuth=false → redirect to /dashboard if already authenticated
export default function ProtectedRoute({ children, requireAuth = true }) {
  const { isAuthenticated } = useAuth()
  if (requireAuth && !isAuthenticated) return <Navigate to="/login" replace />
  if (!requireAuth && isAuthenticated) return <Navigate to="/dashboard" replace />
  return children
}
