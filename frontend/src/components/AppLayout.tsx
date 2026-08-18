import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import './AppLayout.css'

interface AppLayoutProps {
  children: React.ReactNode
}

export default function AppLayout({ children }: AppLayoutProps) {
  const { auth, logout } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-layout">
      <aside className="sidebar" role="navigation" aria-label="Main navigation">
        <div className="sidebar-logo">
          <div className="sidebar-logo-icon" aria-hidden="true">⚡</div>
          OrderFlow
        </div>

        <nav className="sidebar-nav">
          <NavLink
            to="/orders"
            className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}
            id="nav-orders"
          >
            <span className="nav-icon" aria-hidden="true">📦</span>
            Orders
          </NavLink>
        </nav>

        <div className="sidebar-footer">
          {auth && (
            <div className="sidebar-tenant">
              <span className="tenant-label">Tenant</span>
              <span className="tenant-id font-mono">{auth.tenantId}</span>
            </div>
          )}
          <button
            className="nav-link logout-btn"
            id="nav-logout"
            onClick={handleLogout}
          >
            <span className="nav-icon" aria-hidden="true">🚪</span>
            Sign Out
          </button>
        </div>
      </aside>

      <main className="main-content">
        {children}
      </main>
    </div>
  )
}
