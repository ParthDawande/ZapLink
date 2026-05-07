import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'

export default function DashboardPage() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const [longUrl, setLongUrl]     = useState('')
  const [expiresAt, setExpiresAt] = useState('')
  const [result, setResult]       = useState(null)
  const [error, setError]         = useState('')
  const [loading, setLoading]     = useState(false)
  const [copied, setCopied]       = useState(false)

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setResult(null)
    setCopied(false)
    setLoading(true)
    try {
      const { data } = await api.post('/api/links', {
        longUrl,
        expiresAt: expiresAt ? expiresAt + ':00' : null,
      })
      setResult(data)
    } catch (err) {
      setError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleCopy = () => {
    navigator.clipboard.writeText(result.shortUrl).then(() => {
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    })
  }

  return (
    <div className="container mt-5" style={{ maxWidth: '640px' }}>
      <div className="d-flex justify-content-between align-items-center mb-4">
        <div>
          <h1 className="mb-0">⚡ ZapLink</h1>
          <span className="text-muted small">
            Logged in as <strong>{user?.username}</strong>
          </span>
        </div>
        <button className="btn btn-outline-secondary btn-sm" onClick={handleLogout}>
          Logout
        </button>
      </div>

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
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? 'Shortening…' : 'Shorten'}
        </button>
      </form>

      {error && (
        <div className="alert alert-danger mt-4" role="alert">{error}</div>
      )}

      {result && (
        <div className="mt-4 p-3 border rounded bg-light">
          <p className="mb-2 text-muted small">Your short URL:</p>
          <div className="d-flex align-items-center gap-2 flex-wrap">
            <a href={result.shortUrl} target="_blank" rel="noreferrer" className="fs-5 text-break">
              {result.shortUrl}
            </a>
            <button type="button"
              className={`btn btn-sm ${copied ? 'btn-success' : 'btn-outline-secondary'}`}
              onClick={handleCopy}>
              {copied ? '✓ Copied!' : 'Copy'}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
