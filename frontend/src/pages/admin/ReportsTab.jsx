import { useState, useEffect, useCallback } from 'react'
import api from '../../services/api'

function StatCard({ title, stats }) {
  return (
    <div className="zl-admin-section">
      <h3 className="zl-admin-section-title">{title}</h3>
      <div className="zl-metric-list">
        {stats.map(({ label, value, accent }) => (
          <div className="zl-metric" key={label}>
            <span>{label}</span>
            <span className={`zl-metric-value${accent ? ' zl-metric-accent' : ''}`}>
              {value ?? '—'}
            </span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function ReportsTab() {
  const [reports, setReports] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const fetchReports = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get('/api/admin/reports')
      setReports(data)
    } catch (err) {
      setError(err.response?.data?.message ?? 'Failed to load reports.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => { fetchReports() }, [fetchReports])

  return (
    <div className="zl-page">
      <div className="zl-page-header d-flex justify-content-between align-items-center">
        <div>
          <h2 className="zl-page-title" style={{ fontSize: '1.25rem' }}>System Reports</h2>
          <p className="zl-page-subtitle mb-0">Live snapshot of platform activity.</p>
        </div>
        <button className="btn btn-outline-secondary btn-sm" onClick={fetchReports} disabled={loading}>
          {loading ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {loading && !reports && (
        <div className="text-center py-5">
          <div className="spinner-border" role="status">
            <span className="visually-hidden">Loading…</span>
          </div>
        </div>
      )}

      {reports && (
        <div className="row g-3">
          <div className="col-lg-4">
            <StatCard
              title="Users"
              stats={[
                { label: 'Total',         value: reports.userStats.totalUsers },
                { label: 'Active',        value: reports.userStats.activeUsers },
                { label: 'Banned',        value: reports.userStats.bannedUsers },
                { label: 'New (30d)',     value: reports.userStats.newUsersLast30Days, accent: true },
              ]}
            />
          </div>
          <div className="col-lg-4">
            <StatCard
              title="Links"
              stats={[
                { label: 'Total',         value: reports.linkStats.totalLinks },
                { label: 'Active',        value: reports.linkStats.activeLinks },
                { label: 'Expired',       value: reports.linkStats.expiredLinks },
                { label: 'Disabled',      value: reports.linkStats.disabledLinks },
                { label: 'Deleted',       value: reports.linkStats.deletedLinks },
                { label: 'Orphaned',      value: reports.linkStats.orphanedLinks },
                { label: 'New (30d)',     value: reports.linkStats.newLinksLast30Days, accent: true },
              ]}
            />
          </div>
          <div className="col-lg-4">
            <StatCard
              title="Clicks"
              stats={[
                { label: 'Total',         value: reports.clickStats.totalClicks },
                { label: 'Last 30 days',  value: reports.clickStats.clicksLast30Days, accent: true },
              ]}
            />
          </div>
        </div>
      )}
    </div>
  )
}
