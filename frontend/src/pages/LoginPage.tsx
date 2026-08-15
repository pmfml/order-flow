import './LoginPage.css'

export default function LoginPage() {
  return (
    <div className="login-page">
      <div className="login-bg" aria-hidden="true" />
      <div className="login-card glass-card animate-fade-in">
        <div className="login-logo">
          <div className="sidebar-logo-icon" aria-hidden="true">⚡</div>
          <h1>OrderFlow</h1>
        </div>
        <p className="text-secondary">Tenant Dashboard — Sign in to continue</p>
        <div className="login-placeholder">
          <p className="text-muted">Login form coming in Step 3</p>
        </div>
      </div>
    </div>
  )
}
