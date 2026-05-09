import { useState, useEffect, useCallback } from 'react'
import api from '../../services/api'

function truncate(str, n = 45) {
  return str && str.length > n ? str.slice(0, n) + '…' : str
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString()
}

function LinkStatusBadge({ link }) {
  const map = {
    active:   'bg-success',
    expired:  'bg-secondary',
    disabled: 'bg-danger',
    deleted:  'bg-dark',
  }
  const sub = link.disabledReason === 'USER_BANNED'    ? ' · banned'
            : link.disabledReason === 'ADMIN_DISABLED' ? ' · admin'
            : ''
  return (
    <span
      className={`badge ${map[link.status] ?? 'bg-secondary'}`}
      title={link.disabledReason ?? ''}>
      {link.status}{sub}
    </span>
  )
}

export default function LinksTab() {
  const [links, setLinks]               = useState([])
  const [loading, setLoading]           = useState(false)
  const [error, setError]               = useState('')
  const [searchInput, setSearchInput]   = useState('')
  const [search, setSearch]             = useState('')
  const [statusFilter, setStatusFilter] = useState('all')
  const [page, setPage]                 = useState(0)
  const [totalPages, setTotalPages]     = useState(0)
  const [totalElements, setTotalElements] = useState(0)

  // Debounce search and reset page
  useEffect(() => {
    const t = setTimeout(() => {
      setSearch(searchInput)
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [searchInput])

  const fetchLinks = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const params = { page, size: 20, status: statusFilter }
      if (search) params.search = search
      const { data } = await api.get('/api/admin/links', { params })
      setLinks(data.content)
      setTotalPages(data.totalPages)
      setTotalElements(data.totalElements)
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to load links.')
    } finally {
      setLoading(false)
    }
  }, [page, search, statusFilter])

  useEffect(() => { fetchLinks() }, [fetchLinks])

  const handleStatusFilterChange = (val) => {
    setStatusFilter(val)
    setPage(0)
  }

  const handleToggleDisable = async (link) => {
    const disabled = link.isActive // disabling if currently active
    try {
      await api.patch(`/api/admin/links/${link.id}/disable`, { disabled })
      fetchLinks()
    } catch (err) {
      alert(err.response?.data?.message ?? 'Failed to update link.')
    }
  }

  return (
    <div>
      {/* Controls */}
      <div className="row g-2 mb-3">
        <div className="col-sm-5">
          <input
            type="search"
            className="form-control"
            placeholder="Search short code, URL, or owner…"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
          />
        </div>
        <div className="col-sm-3">
          <select
            className="form-select"
            value={statusFilter}
            onChange={e => handleStatusFilterChange(e.target.value)}>
            <option value="all">All statuses</option>
            <option value="active">Active</option>
            <option value="expired">Expired</option>
            <option value="disabled">Disabled</option>
            <option value="deleted">Deleted</option>
          </select>
        </div>
        <div className="col-sm-4 d-flex align-items-center">
          <span className="text-muted small">{totalElements} link{totalElements !== 1 ? 's' : ''}</span>
        </div>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {loading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading…</span>
          </div>
        </div>
      ) : links.length === 0 ? (
        <p className="text-center text-muted py-5">No links found.</p>
      ) : (
        <>
          <div className="table-responsive">
            <table className="table table-hover align-middle">
              <thead className="table-light">
                <tr>
                  <th>Short Code</th>
                  <th>Long URL</th>
                  <th>Owner</th>
                  <th>Status</th>
                  <th>Clicks</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {links.map(link => {
                  const isDeleted    = link.status === 'deleted'
                  const isUserBanned = link.disabledReason === 'USER_BANNED'
                  const canToggle    = !isDeleted && !isUserBanned
                  const toggleLabel  = link.isActive ? 'Disable' : 'Enable'
                  const toggleTitle  = isUserBanned
                    ? 'Owner is banned — unban the user to manage this link'
                    : isDeleted ? 'Cannot modify a deleted link' : ''

                  return (
                    <tr key={link.id}>
                      <td>
                        {link.status === 'active' ? (
                          <a
                            href={link.shortUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="text-decoration-none fw-medium font-monospace">
                            {link.shortCode}
                          </a>
                        ) : (
                          <span className="fw-medium font-monospace text-muted">
                            {link.shortCode}
                          </span>
                        )}
                      </td>
                      <td>
                        <span title={link.longUrl} className="text-muted">
                          {truncate(link.longUrl)}
                        </span>
                      </td>
                      <td>
                        {link.owner
                          ? <span className="text-muted small">{link.owner.username}</span>
                          : <span className="text-muted small fst-italic">(orphaned)</span>}
                      </td>
                      <td><LinkStatusBadge link={link} /></td>
                      <td>{link.clickCount}</td>
                      <td className="text-nowrap">{formatDate(link.createdAt)}</td>
                      <td>
                        <button
                          className={`btn btn-sm ${link.isActive ? 'btn-outline-warning' : 'btn-outline-success'}`}
                          disabled={!canToggle}
                          title={toggleTitle}
                          onClick={() => handleToggleDisable(link)}>
                          {toggleLabel}
                        </button>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <nav aria-label="Links pagination">
              <ul className="pagination justify-content-center">
                <li className={`page-item ${page === 0 ? 'disabled' : ''}`}>
                  <button className="page-link" onClick={() => setPage(p => p - 1)}
                    disabled={page === 0}>
                    Previous
                  </button>
                </li>
                <li className="page-item disabled">
                  <span className="page-link">Page {page + 1} of {totalPages}</span>
                </li>
                <li className={`page-item ${page >= totalPages - 1 ? 'disabled' : ''}`}>
                  <button className="page-link" onClick={() => setPage(p => p + 1)}
                    disabled={page >= totalPages - 1}>
                    Next
                  </button>
                </li>
              </ul>
            </nav>
          )}
        </>
      )}
    </div>
  )
}
