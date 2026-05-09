import { useState, useEffect, useCallback } from 'react'
import api from '../../services/api'

function StatCard({ title, stats }) {
  return (
    <div className="card h-100">
      <div className="card-body">
        <h6 className="card-title text-uppercase text-muted fw-semibold small mb-3">{title}</h6>
        <div className="row g-3">
          {stats.map(({ label, value }) => (
            <div className="col-6" key={label}>
              <div className="display-6 fw-bold">{value ?? '—'}</div>
              <div className="text-muted small">{label}</div>
            </div>
          ))}
        </div>
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
    <div>
      <div className="d-flex justify-content-between align-items-center mb-3">
        <h5 className="mb-0">System Reports</h5>
        <button className="btn btn-outline-secondary btn-sm" onClick={fetchReports} disabled={loading}>
          {loading ? 'Refreshing…' : '↻ Refresh'}
        </button>
      </div>

      {error && <div className="alert alert-danger">{error}</div>}

      {loading && !reports && (
        <div className="text-center py-5">
          <div className="spinner-border text-primary" role="status">
            <span className="visually-hidden">Loading…</span>
          </div>
        </div>
      )}

      {reports && (
        <div className="row g-4">
          <div className="col-md-4">
            <StatCard
              title="Users"
              stats={[
                { label: 'Total',           value: reports.userStats.totalUsers },
                { label: 'Active',          value: reports.userStats.activeUsers },
                { label: 'Banned',          value: reports.userStats.bannedUsers },
                { label: 'New (30 days)',   value: reports.userStats.newUsersLast30Days },
              ]}
            />
          </div>
          <div className="col-md-4">
            <StatCard
              title="Links"
              stats={[
                { label: 'Total',           value: reports.linkStats.totalLinks },
                { label: 'Active',          value: reports.linkStats.activeLinks },
                { label: 'Expired',         value: reports.linkStats.expiredLinks },
                { label: 'Disabled',        value: reports.linkStats.disabledLinks },
                { label: 'Deleted',         value: reports.linkStats.deletedLinks },
                { label: 'Orphaned',        value: reports.linkStats.orphanedLinks },
                { label: 'New (30 days)',   value: reports.linkStats.newLinksLast30Days },
              ]}
            />
          </div>
          <div className="col-md-4">
            <StatCard
              title="Clicks"
              stats={[
                { label: 'Total',         value: reports.clickStats.totalClicks },
                { label: 'Last 30 days',  value: reports.clickStats.clicksLast30Days },
              ]}
            />
          </div>
        </div>
      )}
    </div>
  )
}
