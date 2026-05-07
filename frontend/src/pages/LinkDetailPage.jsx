import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../services/api'

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

  const [link, setLink]       = useState(null)
  const [loading, setLoading] = useState(true)
  const [notFound, setNotFound] = useState(false)
  const [error, setError]     = useState('')
  const [copied, setCopied]   = useState(false)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api.get(`/api/links/${id}`)
      .then(({ data }) => { if (!cancelled) setLink(data) })
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
      <div className="container mt-5 text-center">
        <div className="spinner-border text-primary" role="status">
          <span className="visually-hidden">Loading…</span>
        </div>
      </div>
    )
  }

  if (notFound) {
    return (
      <div className="container mt-5" style={{ maxWidth: '600px' }}>
        <div className="alert alert-warning" role="alert">Link not found.</div>
        <button className="btn btn-outline-secondary" onClick={() => navigate('/dashboard')}>
          ← Back to Dashboard
        </button>
      </div>
    )
  }

  if (error && !link) {
    return (
      <div className="container mt-5" style={{ maxWidth: '600px' }}>
        <div className="alert alert-danger" role="alert">{error}</div>
        <button className="btn btn-outline-secondary" onClick={() => navigate('/dashboard')}>
          ← Back to Dashboard
        </button>
      </div>
    )
  }

  return (
    <div className="container mt-5" style={{ maxWidth: '640px' }}>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <h2 className="mb-0">Link Details</h2>
        <button className="btn btn-outline-secondary btn-sm" onClick={() => navigate('/dashboard')}>
          ← Back to Dashboard
        </button>
      </div>

      {error && (
        <div className="alert alert-danger" role="alert">{error}</div>
      )}

      <div className="card">
        <div className="card-body">
          <dl className="row mb-0">
            <dt className="col-sm-4">Short URL</dt>
            <dd className="col-sm-8">
              <div className="d-flex align-items-center gap-2 flex-wrap">
                <a href={link.shortUrl} target="_blank" rel="noreferrer">{link.shortUrl}</a>
                <button
                  className={`btn btn-sm ${copied ? 'btn-success' : 'btn-outline-secondary'}`}
                  onClick={handleCopy}>
                  {copied ? '✓ Copied!' : 'Copy'}
                </button>
              </div>
            </dd>

            <dt className="col-sm-4">Original URL</dt>
            <dd className="col-sm-8 text-break">
              <a href={link.longUrl} target="_blank" rel="noreferrer">{link.longUrl}</a>
            </dd>

            <dt className="col-sm-4">Status</dt>
            <dd className="col-sm-8"><StatusBadge status={link.status} /></dd>

            <dt className="col-sm-4">Clicks</dt>
            <dd className="col-sm-8">{link.clickCount}</dd>

            <dt className="col-sm-4">Created</dt>
            <dd className="col-sm-8">{formatDate(link.createdAt)}</dd>

            <dt className="col-sm-4">Expires</dt>
            <dd className="col-sm-8">
              {link.expiresAt ? formatDate(link.expiresAt) : 'Never expires'}
            </dd>
          </dl>
        </div>
      </div>

      <div className="mt-4">
        <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
          {deleting ? 'Deleting…' : 'Delete Link'}
        </button>
      </div>
    </div>
  )
}
