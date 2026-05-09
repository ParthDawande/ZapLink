import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../../context/AuthContext'

export default function AdminLayout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <header className="zl-navbar">
        <div className="zl-navbar-inner">
          <Link to="/dashboard" className="zl-brand">
            <span>⚡</span>
            <span>ZapLink</span>
          </Link>
          <div className="zl-navbar-right">
            <NavLink to="/dashboard" className="zl-nav-link">My Dashboard</NavLink>
            <span className="zl-nav-user">{user?.username}</span>
            <button className="btn btn-secondary btn-sm" onClick={handleLogout}>Logout</button>
          </div>
        </div>
      </header>

      <div className="zl-admin-top">
        <div className="zl-page-header">
          <h1 className="zl-page-title">Admin</h1>
          <p className="zl-page-subtitle">Manage users, links, and view system reports.</p>
        </div>
        <ul className="nav nav-tabs">
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
      </div>

      <Outlet />
    </>
  )
}
