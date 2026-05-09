import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="container mt-4" style={{ maxWidth: '1100px' }}>
      {/* Top bar */}
      <div className="d-flex justify-content-between align-items-center mb-3">
        <div>
          <h1 className="mb-0">⚡ ZapLink <span className="text-muted fs-4">Admin</span></h1>
          <span className="text-muted small">
            Logged in as <strong>{user?.username}</strong>
          </span>
        </div>
        <div className="d-flex gap-2">
          <NavLink to="/dashboard" className="btn btn-outline-secondary btn-sm">
            My Dashboard
          </NavLink>
          <button className="btn btn-outline-secondary btn-sm" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </div>

      {/* Tab nav */}
      <ul className="nav nav-tabs mb-4">
        <li className="nav-item">
          <NavLink
            to="/admin/reports"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Reports
          </NavLink>
        </li>
        <li className="nav-item">
          <NavLink
            to="/admin/users"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Users
          </NavLink>
        </li>
        <li className="nav-item">
          <NavLink
            to="/admin/links"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
            Links
          </NavLink>
        </li>
      </ul>

      <Outlet />
    </div>
  )
}
