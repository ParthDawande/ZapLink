import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../services/api'
import ClickChart from '../components/ClickChart'
import QrCodeModal from '../components/QrCodeModal'

function StatusBadge({ status }) {
  const cls = { active: 'bg-success', expired: 'bg-secondary', disabled: 'bg-danger' }
  return <span className={`badge ${cls[status] ?? 'bg-secondary'}`}>{status}</span>
}

function formatDate(iso) {
  if (!iso) return null
  return new Date(iso).toLocaleString()
}

export default function LinkDetailPage() {
  const { id } = useParams()
  const navigate = useNavigate()

  const [link, setLink]           = useState(null)
  const [analytics, setAnalytics] = useState(null)
  const [loading, setLoading]     = useState(true)
  const [notFound, setNotFound]   = useState(false)
  const [error, setError]         = useState('')
  const [copied, setCopied]       = useState(false)
  const [deleting, setDeleting]   = useState(false)
  const [qrOpen, setQrOpen]       = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')

    Promise.all([
      api.get(`/api/links/${id}`),
      api.get(`/api/links/${id}/analytics`),
    ])
      .then(([linkRes, analyticsRes]) => {
        if (!cancelled) {
          setLink(linkRes.data)
          setAnalytics(analyticsRes.data)
        }
      })
      .catch(err => {
        if (!cancelled) {
          if (err.response?.status === 404) {
            setNotFound(true)
          } else {
            setError(err.response?.data?.message ?? 'Failed to load link.')
          }
        }
      })
      .finally(() => { if (!cancelled) setLoading(false) })

    return () => { cancelled = true }
  }, [id])

  const handleCopy = () => {
    navigator.clipboard.writeText(link.shortUrl).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    })
  }

  const handleDelete = async () => {
    if (!confirm('Delete this link? This cannot be undone.')) return
    setDeleting(true)
    try {
      await api.delete(`/api/links/${id}`)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to delete link.')
      setDeleting(false)
    }
  }

  if (loading) {
    return (
      <div className="zl-page text-center pt-5">
        <div className="spinner-border" role="status">
          <span className="visually-hidden">Loading…</span>
        </div>
      </div>
    )
  }

  if (notFound) {
    return (
      <div className="zl-page" style={{ maxWidth: '700px' }}>
        <div className="alert alert-warning" role="alert">Link not found.</div>
        <button className="btn btn-outline-secondary" onClick={() => navigate('/dashboard')}>
          ← Back to Dashboard
        </button>
      </div>
    )
  }

  if (error && !link) {
    return (
      <div className="zl-page" style={{ maxWidth: '700px' }}>
        <div className="alert alert-danger" role="alert">{error}</div>
        <button className="btn btn-outline-secondary" onClick={() => navigate('/dashboard')}>
          ← Back to Dashboard
        </button>
      </div>
    )
  }

  return (
    <div className="zl-page">
      {/* Header */}
      <div className="zl-page-header d-flex justify-content-between align-items-start">
        <div>
          <h1 className="zl-page-title font-monospace">{link.shortCode}</h1>
          <p className="zl-page-subtitle text-truncate" title={link.longUrl}
            style={{ maxWidth: '600px' }}>
            {link.longUrl}
          </p>
        </div>
        <button className="btn btn-outline-secondary btn-sm flex-shrink-0"
          onClick={() => navigate('/dashboard')}>
          ← Back
        </button>
      </div>

      {error && (
        <div className="alert alert-danger" role="alert">{error}</div>
      )}

      {/* Stat cards */}
      {analytics && (
        <div className="row g-3 mb-4">
          <div className="col-md-4">
            <div className="zl-stat">
              <div className="zl-stat-label">Total Clicks</div>
              <div className="zl-stat-value zl-stat-accent">{analytics.totalClicks}</div>
            </div>
          </div>
          <div className="col-md-4">
            <div className="zl-stat">
              <div className="zl-stat-label">Last 30 Days</div>
              <div className="zl-stat-value">{analytics.last30DaysClicks}</div>
            </div>
          </div>
          <div className="col-md-4">
            <div className="zl-stat">
              <div className="zl-stat-label">Status</div>
              <div className="zl-stat-value">
                <StatusBadge status={link.status} />
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Chart */}
      {analytics && (
        <div className="zl-stat mb-4">
          <div className="zl-stat-label" style={{ marginBottom: '1rem' }}>
            Click activity — last 30 days
          </div>
          <ClickChart dailyClicks={analytics.dailyClicks} />
        </div>
      )}

      {/* Link details */}
      <div className="zl-stat mb-4">
        <dl className="row mb-0">
          <dt className="col-sm-3 text-muted" style={{ fontSize: '0.875rem' }}>Short URL</dt>
          <dd className="col-sm-9">
            <div className="d-flex align-items-center gap-2 flex-wrap">
              <a href={link.shortUrl} target="_blank" rel="noreferrer"
                className="zl-short-code">{link.shortUrl}</a>
              <button
                className={`btn btn-sm ${copied ? 'btn-success' : 'btn-outline-secondary'}`}
                onClick={handleCopy}>
                {copied ? '✓ Copied!' : 'Copy'}
              </button>
              {link.status === 'active' && (
                <button className="btn btn-sm btn-outline-secondary"
                  onClick={() => setQrOpen(true)}>
                  QR Code
                </button>
              )}
            </div>
          </dd>

          <dt className="col-sm-3 text-muted" style={{ fontSize: '0.875rem' }}>Original URL</dt>
          <dd className="col-sm-9 text-break">
            <a href={link.longUrl} target="_blank" rel="noreferrer"
              className="text-muted" style={{ fontSize: '0.875rem' }}>{link.longUrl}</a>
          </dd>

          <dt className="col-sm-3 text-muted" style={{ fontSize: '0.875rem' }}>Created</dt>
          <dd className="col-sm-9" style={{ fontSize: '0.875rem' }}>{formatDate(link.createdAt)}</dd>

          <dt className="col-sm-3 text-muted mb-0" style={{ fontSize: '0.875rem' }}>Expires</dt>
          <dd className="col-sm-9 mb-0" style={{ fontSize: '0.875rem' }}>
            {link.expiresAt ? formatDate(link.expiresAt) : 'Never expires'}
          </dd>
        </dl>
      </div>

      <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
        {deleting ? 'Deleting…' : 'Delete Link'}
      </button>

      {link && (
        <QrCodeModal
          linkId={link.id}
          shortUrl={link.shortUrl}
          isOpen={qrOpen}
          onClose={() => setQrOpen(false)}
        />
      )}
    </div>
  )
}
