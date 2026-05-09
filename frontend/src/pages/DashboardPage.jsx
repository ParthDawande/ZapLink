import { useState, useEffect, useCallback } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'
import QrCodeModal from '../components/QrCodeModal'

function StatusBadge({ status }) {
  const cls = { active: 'bg-success', expired: 'bg-secondary', disabled: 'bg-danger' }
  return <span className={`badge ${cls[status] ?? 'bg-secondary'}`}>{status}</span>
}

function truncate(str, n = 40) {
  return str.length > n ? str.slice(0, n) + '…' : str
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString()
}

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  // ── Shorten form ─────────────────────────────────────────────────────────
  const [longUrl, setLongUrl]         = useState('')
  const [expiresAt, setExpiresAt]     = useState('')
  const [result, setResult]           = useState(null)
  const [shortenError, setShortenError] = useState('')
  const [shortening, setShortening]   = useState(false)
  const [resultCopied, setResultCopied] = useState(false)

  // ── Links table ───────────────────────────────────────────────────────────
  const [qrModalLink, setQrModalLink] = useState(null)

  const [links, setLinks]             = useState([])
  const [page, setPage]               = useState(0)
  const [totalPages, setTotalPages]   = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [linksLoading, setLinksLoading] = useState(false)
  const [linksError, setLinksError]   = useState('')
  const [copiedId, setCopiedId]       = useState(null)

  const fetchLinks = useCallback(async (p) => {
    setLinksLoading(true)
    setLinksError('')
    try {
      const { data } = await api.get(`/api/links?page=${p}&size=20`)
      setLinks(data.content)
      setTotalPages(data.totalPages)
      setTotalElements(data.totalElements)
    } catch (err) {
      setLinksError(err.response?.data?.message ?? 'Failed to load links.')
    } finally {
      setLinksLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchLinks(page)
  }, [fetchLinks, page])

  // ── Handlers ──────────────────────────────────────────────────────────────
  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setShortenError('')
    setResult(null)
    setResultCopied(false)
    setShortening(true)
    try {
      const { data } = await api.post('/api/links', {
        longUrl,
        expiresAt: expiresAt ? expiresAt + ':00' : null,
      })
      setResult(data)
      setPage(0)
      fetchLinks(0)
    } catch (err) {
      setShortenError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setShortening(false)
    }
  }

  const handleResultCopy = () => {
    navigator.clipboard.writeText(result.shortUrl).then(() => {
      setResultCopied(true)
      setTimeout(() => setResultCopied(false), 1500)
    })
  }

  const handleCopyLink = (shortUrl, id) => {
    navigator.clipboard.writeText(shortUrl).then(() => {
      setCopiedId(id)
      setTimeout(() => setCopiedId(null), 1500)
    })
  }

  const handleDelete = async (id) => {
    if (!confirm('Delete this link? This cannot be undone.')) return
    try {
      await api.delete(`/api/links/${id}`)
      fetchLinks(page)
    } catch (err) {
      alert(err.response?.data?.message ?? 'Failed to delete link.')
    }
  }

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <div className="container mt-5" style={{ maxWidth: '960px' }}>

      {/* Top bar */}
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h1 className="mb-0">⚡ ZapLink</h1>
          <span className="text-muted small">
            Logged in as <strong>{user?.username}</strong>
          </span>
        </div>
        <div className="d-flex gap-2">
          {user?.role === 'ADMIN' && (
            <Link to="/admin" className="btn btn-sm btn-outline-primary">Admin</Link>
          )}
          <button className="btn btn-outline-secondary btn-sm" onClick={handleLogout}>
            Logout
          </button>
        </div>
      </div>

      {/* Shorten form */}
      <form onSubmit={handleSubmit}>
        <div className="mb-3">
          <label htmlFor="longUrl" className="form-label fw-semibold">Long URL</label>
          <input id="longUrl" type="url" className="form-control"
            placeholder="https://example.com/very/long/url"
            value={longUrl} onChange={e => setLongUrl(e.target.value)} required />
        </div>
        <div className="mb-3">
          <label htmlFor="expiresAt" className="form-label fw-semibold">
            Expiry <span className="text-muted fw-normal">(optional)</span>
          </label>
          <input id="expiresAt" type="datetime-local" className="form-control"
            value={expiresAt} onChange={e => setExpiresAt(e.target.value)} />
        </div>
        <button type="submit" className="btn btn-primary" disabled={shortening}>
          {shortening ? 'Shortening…' : 'Shorten'}
        </button>
      </form>

      {shortenError && (
        <div className="alert alert-danger mt-4" role="alert">{shortenError}</div>
      )}

      {result && (
        <div className="mt-4 p-3 border rounded bg-light">
          <p className="mb-2 text-muted small">Your short URL:</p>
          <div className="d-flex align-items-center gap-2 flex-wrap">
            <a href={result.shortUrl} target="_blank" rel="noreferrer" className="fs-5 text-break">
              {result.shortUrl}
            </a>
            <button type="button"
              className={`btn btn-sm ${resultCopied ? 'btn-success' : 'btn-outline-secondary'}`}
              onClick={handleResultCopy}>
              {resultCopied ? '✓ Copied!' : 'Copy'}
            </button>
          </div>
        </div>
      )}

      {/* Your Links section */}
      <hr className="my-4" />
      <h4 className="mb-3">Your Links</h4>

      {linksError && (
        <div className="alert alert-danger" role="alert">{linksError}</div>
      )}

      {linksLoading ? (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading…</span>
          </div>
        </div>
      ) : totalElements === 0 ? (
        <p className="text-center text-muted py-5">No links yet — shorten one above!</p>
      ) : (
        <>
          <div className="table-responsive">
            <table className="table table-hover align-middle">
              <thead className="table-light">
                <tr>
                  <th>Short URL</th>
                  <th>Original URL</th>
                  <th>Clicks</th>
                  <th>Status</th>
                  <th>Created</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {links.map(link => (
                  <tr key={link.id}>
                    <td>
                      <a href={link.shortUrl} target="_blank" rel="noreferrer"
                        className="text-decoration-none fw-medium">
                        {link.shortCode}
                      </a>
                    </td>
                    <td>
                      <span title={link.longUrl} className="text-muted">
                        {truncate(link.longUrl)}
                      </span>
                    </td>
                    <td
                      style={{ cursor: 'pointer' }}
                      onClick={() => navigate(`/links/${link.id}`)}
                      title="View analytics">
                      {link.clickCount}
                    </td>
                    <td><StatusBadge status={link.status} /></td>
                    <td className="text-nowrap">{formatDate(link.createdAt)}</td>
                    <td>
                      <div className="d-flex gap-1">
                        <button
                          className={`btn btn-sm ${copiedId === link.id ? 'btn-success' : 'btn-outline-secondary'}`}
                          onClick={() => handleCopyLink(link.shortUrl, link.id)}>
                          {copiedId === link.id ? 'Copied!' : 'Copy'}
                        </button>
                        <Link to={`/links/${link.id}`} className="btn btn-sm btn-outline-primary">
                          View
                        </Link>
                        <button
                          className="btn btn-sm btn-outline-secondary"
                          onClick={() => setQrModalLink(link)}
                          disabled={link.status !== 'active'}
                          title={link.status !== 'active' ? 'Not available for expired/disabled links' : 'Show QR code'}>
                          QR
                        </button>
                        <button
                          className="btn btn-sm btn-outline-danger"
                          onClick={() => handleDelete(link.id)}>
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
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

      <QrCodeModal
        linkId={qrModalLink?.id}
        shortUrl={qrModalLink?.shortUrl}
        isOpen={qrModalLink !== null}
        onClose={() => setQrModalLink(null)}
      />
    </div>
  )
}
