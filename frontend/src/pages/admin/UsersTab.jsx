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
  return (
    <span className={`badge ${role === 'ADMIN' ? 'bg-primary' : 'bg-secondary'}`}>
      {role}
    </span>
  )
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
    <div>
      {/* Controls */}
      <div className="row g-2 mb-3">
        <div className="col-sm-6">
          <input
            type="search"
            className="form-control"
            placeholder="Search username or email…"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
          />
        </div>
        <div className="col-sm-3">
          <select
            className="form-select"
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value)}>
            <option value="">All statuses</option>
            <option value="active">Active</option>
            <option value="disabled">Banned</option>
          </select>
        </div>
        <div className="col-sm-3 d-flex align-items-center">
          <span className="text-muted small">{users.length} result{users.length !== 1 ? 's' : ''}</span>
        </div>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading…</span>
          </div>
        </div>
      ) : users.length === 0 ? (
        <p className="text-center text-muted py-5">No users found.</p>
      ) : (
        <div className="table-responsive">
          <table className="table table-hover align-middle">
            <thead className="table-light">
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
                  <tr key={row.id} className={isSelf ? 'table-active' : ''}>
                    <td className="fw-medium">
                      {row.username}
                      {isSelf && <span className="badge bg-secondary ms-1 small">you</span>}
                    </td>
                    <td className="text-muted">{row.email}</td>
                    <td><RoleBadge role={row.role} /></td>
                    <td><UserStatusBadge isActive={row.isActive} /></td>
                    <td>{row.linkCount}</td>
                    <td className="text-nowrap">{formatDate(row.createdAt)}</td>
                    <td>
                      <div className="d-flex gap-1">
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
    </div>
  )
}
