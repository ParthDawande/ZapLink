import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'

export default function RegisterPage() {
  const [username, setUsername] = useState('')
  const [email, setEmail]       = useState('')
  const [password, setPassword] = useState('')
  const [error, setError]       = useState('')
  const [loading, setLoading]   = useState(false)
  const auth     = useAuth()
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data } = await api.post('/api/auth/register', { username, email, password })
      auth.login(data.token, {
        id: data.userId, username: data.username, email: data.email, role: data.role,
      })
      navigate('/dashboard', { replace: true })
    } catch (err) {
      setError(err.response?.data?.message ?? 'Something went wrong. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container mt-5" style={{ maxWidth: '420px' }}>
      <h1 className="mb-1">⚡ ZapLink</h1>
      <p className="text-muted mb-4">Create an account</p>

      {error && <div className="alert alert-danger" role="alert">{error}</div>}

      <form onSubmit={handleSubmit}>
        <div className="mb-3">
          <label className="form-label" htmlFor="username">Username</label>
          <input id="username" type="text" className="form-control"
            value={username} onChange={e => setUsername(e.target.value)} required autoFocus />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="email">Email</label>
          <input id="email" type="email" className="form-control"
            value={email} onChange={e => setEmail(e.target.value)} required />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="password">Password</label>
          <input id="password" type="password" className="form-control"
            value={password} onChange={e => setPassword(e.target.value)} required />
        </div>
        <button type="submit" className="btn btn-primary w-100" disabled={loading}>
          {loading ? 'Creating account…' : 'Create account'}
        </button>
      </form>

      <p className="mt-3 text-center text-muted small">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </div>
  )
}
