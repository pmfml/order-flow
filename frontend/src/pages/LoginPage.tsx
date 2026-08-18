import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import './LoginPage.css'

/** mock-oauth2-server token endpoint (Docker Compose) */
const MOCK_TOKEN_URL = 'http://localhost:8099/orderflow/token'

/**
 * Simulated login page.
 *
 * Attempts to fetch a real JWT from the mock-oauth2-server. If the server
 * is not running, falls back to a synthetic token so the frontend can be
 * developed independently of the Docker infrastructure.
 */
export default function LoginPage() {
  const { login, isAuthenticated } = useAuth()
  const navigate = useNavigate()

  const [tenantId, setTenantId] = useState('dev-tenant')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Already logged in — redirect immediately
  if (isAuthenticated) {
    navigate('/orders', { replace: true })
    return null
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    setError(null)

    const trimmed = tenantId.trim()
    if (!trimmed) {
      setError('Tenant ID is required.')
      return
    }

    setLoading(true)

    try {
      const token = await fetchToken(trimmed)
      login(trimmed, token)
      navigate('/orders', { replace: true })
    } catch (err) {
      console.warn('[Login] Could not reach mock-oauth2-server, using fallback token.', err)
      // Fallback: synthetic JWT-shaped string so the app works without Docker
      const fallbackToken = btoa(JSON.stringify({ sub: 'dev-user', tenant_id: trimmed, iat: Date.now() }))
      login(trimmed, `eyJ.${fallbackToken}.dev`)
      navigate('/orders', { replace: true })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-bg" aria-hidden="true" />
      <form className="login-card glass-card animate-fade-in" onSubmit={handleSubmit}>
        <div className="login-logo">
          <div className="sidebar-logo-icon" aria-hidden="true">⚡</div>
          <h1>OrderFlow</h1>
        </div>
        <p className="text-secondary login-subtitle">Tenant Dashboard</p>

        <div className="login-form-group">
          <label htmlFor="tenant-id" className="login-label">Tenant ID</label>
          <input
            id="tenant-id"
            type="text"
            className="input"
            placeholder="e.g. dev-tenant"
            value={tenantId}
            onChange={(e) => setTenantId(e.target.value)}
            autoFocus
            autoComplete="off"
          />
        </div>

        {error && <p className="login-error">{error}</p>}

        <button
          type="submit"
          className="btn btn-primary login-submit"
          id="login-submit"
          disabled={loading}
        >
          {loading ? 'Connecting…' : 'Connect'}
        </button>

        <p className="login-hint text-muted">
          Uses mock-oauth2-server for JWT issuance in development.
        </p>
      </form>
    </div>
  )
}

/**
 * Fetches a JWT from the mock-oauth2-server using client_credentials grant.
 */
async function fetchToken(tenantId: string): Promise<string> {
  const body = new URLSearchParams({
    grant_type: 'client_credentials',
    client_id: 'orderflow-dashboard',
    client_secret: 'not-a-secret',
    scope: `tenant:${tenantId}`,
  })

  const response = await fetch(MOCK_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body,
  })

  if (!response.ok) {
    throw new Error(`Token endpoint returned ${response.status}`)
  }

  const data = await response.json()
  return data.access_token
}
