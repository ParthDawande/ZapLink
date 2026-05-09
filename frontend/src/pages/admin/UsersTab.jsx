import { useState, useEffect, useCallback } from 'react'
import api from '../../services/api'
import { useAuth } from '../../context/AuthContext'

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString()
}

function UserStatusBadge({ isActive }) {
  return (
    <span className={`badge ${isActive ? 'bg-success' : 'bg-danger'}`}>
      {isActive ? 'active' : 'banned'}
    </span>
  )
}

function RoleBadge({ role }) {
  if (role === 'ADMIN') return <span className="badge bg-success">ADMIN</span>
  return <span className="text-muted" style={{ fontSize: '0.875rem' }}>USER</span>
}

export default function UsersTab() {
  const { user: currentUser } = useAuth()

  const [users, setUsers]               = useState([])
  const [loading, setLoading]           = useState(false)
  const [error, setError]               = useState('')
  const [searchInput, setSearchInput]   = useState('')
  const [search, setSearch]             = useState('')
  const [statusFilter, setStatusFilter] = useState('')

  // Debounce search
  useEffect(() => {
    const t = setTimeout(() => setSearch(searchInput), 300)
    return () => clearTimeout(t)
  }, [searchInput])

  const fetchUsers = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const params = {}
      if (search) params.search = search
      if (statusFilter) params.status = statusFilter
      const { data } = await api.get('/api/admin/users', { params })
      setUsers(data)
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to load users.')
    } finally {
      setLoading(false)
    }
  }, [search, statusFilter])

  useEffect(() => { fetchUsers() }, [fetchUsers])

  const handleBan = async (userId, banned, username, linkCount) => {
    const msg = banned
      ? `Ban ${username}? Their ${linkCount} links will be disabled.`
      : `Unban ${username}? Their banned links will be re-enabled.`
    if (!confirm(msg)) return
    try {
      await api.patch(`/api/admin/users/${userId}/ban`, { banned })
      fetchUsers()
    } catch (err) {
      alert(err.response?.data?.message ?? 'Failed to update user.')
    }
  }

  const handleDelete = async (userId, username) => {
    if (!confirm(
      `Permanently delete ${username}? This cannot be undone.\n` +
      `Their links will be soft-deleted but click history will be preserved.`
    )) return
    try {
      await api.delete(`/api/admin/users/${userId}`)
      fetchUsers()
    } catch (err) {
      alert(err.response?.data?.message ?? 'Failed to delete user.')
    }
  }

  return (
    <div className="zl-page">
      <div className="zl-page-header">
        <h2 className="zl-page-title" style={{ fontSize: '1.25rem' }}>Users</h2>
        <p className="zl-page-subtitle mb-0">{users.length} total</p>
      </div>

      <section className="zl-links-section">
        <div className="zl-filter-bar">
          <input
            type="search"
            className="form-control"
            placeholder="Search username or email…"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
          />
          <select
            className="form-select"
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            <option value="active">Active</option>
            <option value="disabled">Banned</option>
          </select>
        </div>

        {error && <div className="alert alert-danger m-3">{error}</div>}

        {loading ? (
          <div className="text-center py-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading…</span>
            </div>
          </div>
        ) : users.length === 0 ? (
          <div className="zl-empty">
            <p className="zl-empty-title">No users found.</p>
          </div>
        ) : (
          <div className="table-responsive">
            <table className="table table-hover align-middle">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Links</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map(row => {
                  const isSelf = row.id === currentUser?.id
                  const isBanned = !row.isActive
                  return (
                    <tr key={row.id} className={isSelf ? 'zl-row-self' : ''}>
                      <td>
                        <span className="fw-semibold">{row.username}</span>
                        {isSelf && <span className="badge bg-secondary ms-2">you</span>}
                      </td>
                      <td className="text-muted" style={{ fontSize: '0.875rem' }}>{row.email}</td>
                      <td><RoleBadge role={row.role} /></td>
                      <td><UserStatusBadge isActive={row.isActive} /></td>
                      <td>{row.linkCount}</td>
                      <td className="text-nowrap">{formatDate(row.createdAt)}</td>
                      <td>
                        <div className="zl-row-actions">
                          <button
                            className={`btn btn-sm ${isBanned ? 'btn-outline-success' : 'btn-outline-warning'}`}
                            disabled={isSelf}
                            title={isSelf ? 'You cannot ban yourself' : ''}
                            onClick={() => handleBan(row.id, !isBanned, row.username, row.linkCount)}>
                            {isBanned ? 'Unban' : 'Ban'}
                          </button>
                          <button
                            className="btn btn-sm btn-outline-danger"
                            disabled={isSelf || !isBanned}
                            title={
                              isSelf ? 'You cannot delete yourself'
                              : !isBanned ? 'User must be banned before deletion'
                              : ''
                            }
                            onClick={() => handleDelete(row.id, row.username)}>
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>
  )
}
