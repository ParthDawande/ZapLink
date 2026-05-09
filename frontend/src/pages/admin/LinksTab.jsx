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
    <div className="zl-page">
      <div className="zl-page-header">
        <h2 className="zl-page-title" style={{ fontSize: '1.25rem' }}>Links</h2>
        <p className="zl-page-subtitle mb-0">{totalElements} total</p>
      </div>

      <section className="zl-links-section">
        <div className="zl-filter-bar">
          <input
            type="search"
            className="form-control"
            placeholder="Search short code, URL, or owner…"
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
          />
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

        {error && <div className="alert alert-danger m-3">{error}</div>}

        {loading ? (
          <div className="text-center py-5">
            <div className="spinner-border" role="status">
              <span className="visually-hidden">Loading…</span>
            </div>
          </div>
        ) : links.length === 0 ? (
          <div className="zl-empty">
            <p className="zl-empty-title">No links found.</p>
          </div>
        ) : (
          <>
            <div className="table-responsive">
              <table className="table table-hover align-middle">
                <thead>
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
                            <a href={link.shortUrl} target="_blank" rel="noreferrer"
                              className="zl-short-code">
                              {link.shortCode}
                            </a>
                          ) : (
                            <span className="zl-short-code" style={{ color: 'var(--zl-text-dim)', cursor: 'default' }}>
                              {link.shortCode}
                            </span>
                          )}
                        </td>
                        <td>
                          <span className="zl-long-url" title={link.longUrl}>
                            {truncate(link.longUrl)}
                          </span>
                        </td>
                        <td>
                          {link.owner
                            ? <span style={{ fontSize: '0.875rem' }}>{link.owner.username}</span>
                            : <span className="fst-italic" style={{ fontSize: '0.875rem', color: 'var(--zl-text-dim)' }}>(orphaned)</span>}
                        </td>
                        <td><LinkStatusBadge link={link} /></td>
                        <td>{link.clickCount}</td>
                        <td className="text-nowrap">{formatDate(link.createdAt)}</td>
                        <td>
                          <div className="zl-row-actions">
                            <button
                              className={`btn btn-sm ${link.isActive ? 'btn-outline-warning' : 'btn-outline-success'}`}
                              disabled={!canToggle}
                              title={toggleTitle}
                              onClick={() => handleToggleDisable(link)}>
                              {toggleLabel}
                            </button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {totalPages > 1 && (
              <nav aria-label="Links pagination" className="p-3">
                <ul className="pagination justify-content-center mb-0">
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
      </section>
    </div>
  )
}
