import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import api from '../services/api'
import { useAuth } from '../context/AuthContext'

export default function LoginPage() {
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
      const { data } = await api.post('/api/auth/login', { email, password })
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
      <p className="text-muted mb-4">Sign in to your account</p>

      {error && <div className="alert alert-danger" role="alert">{error}</div>}

      <form onSubmit={handleSubmit}>
        <div className="mb-3">
          <label className="form-label" htmlFor="email">Email</label>
          <input id="email" type="email" className="form-control"
            value={email} onChange={e => setEmail(e.target.value)} required autoFocus />
        </div>
        <div className="mb-3">
          <label className="form-label" htmlFor="password">Password</label>
          <input id="password" type="password" className="form-control"
            value={password} onChange={e => setPassword(e.target.value)} required />
        </div>
        <button type="submit" className="btn btn-primary w-100" disabled={loading}>
          {loading ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p className="mt-3 text-center text-muted small">
        No account? <Link to="/register">Register</Link>
      </p>
    </div>
  )
}
