import { useState, useEffect } from 'react'
import api from '../services/api'

export default function QrCodeModal({ linkId, shortUrl, isOpen, onClose }) {
  const [blobUrl, setBlobUrl] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!isOpen) return

    let cancelled = false
    let objectUrl = null

    setLoading(true)
    setError('')
    setBlobUrl(null)

    ;(async () => {
      try {
        const response = await api.get(`/api/links/${linkId}/qr`, { responseType: 'blob' })
        if (cancelled) return
        objectUrl = URL.createObjectURL(response.data)
        setBlobUrl(objectUrl)
      } catch (err) {
        if (cancelled) return
        let message = 'Failed to load QR code.'
        try {
          // When responseType:'blob', axios wraps error body as a Blob even for non-2xx.
          if (err.response?.data instanceof Blob) {
            const text = await err.response.data.text()
            message = JSON.parse(text).message ?? message
          } else if (err.response?.data?.message) {
            message = err.response.data.message
          }
        } catch {
          // keep default message
        }
        setError(message)
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()

    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [isOpen, linkId])

  if (!isOpen) return null

  return (
    <>
      {/* Backdrop */}
      <div className="modal-backdrop show" onClick={onClose} />

      {/* Modal */}
      <div
        className="modal show d-block"
        tabIndex="-1"
        role="dialog"
        style={{ zIndex: 1055 }}
        onClick={onClose}
      >
        <div
          className="modal-dialog modal-dialog-centered"
          onClick={e => e.stopPropagation()}
        >
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">QR Code</h5>
              <button type="button" className="btn-close" aria-label="Close" onClick={onClose} />
            </div>

            <div className="modal-body text-center">
              {loading && (
                <div className="py-4">
                  <div className="spinner-border text-primary" role="status">
                    <span className="visually-hidden">Loading…</span>
                  </div>
                </div>
              )}

              {error && (
                <div className="alert alert-danger mb-0" role="alert">{error}</div>
              )}

              {blobUrl && (
                <>
                  <img
                    src={blobUrl}
                    alt="QR code"
                    width={300}
                    height={300}
                    className="img-fluid"
                  />
                  <p className="text-muted small mt-2 mb-0 text-break">{shortUrl}</p>
                </>
              )}
            </div>

            <div className="modal-footer">
              {blobUrl && (
                <a
                  href={blobUrl}
                  download={`qr-${linkId}.png`}
                  className="btn btn-primary"
                >
                  Download
                </a>
              )}
              <button type="button" className="btn btn-secondary" onClick={onClose}>
                Close
              </button>
            </div>
          </div>
        </div>
      </div>
    </>
  )
}
