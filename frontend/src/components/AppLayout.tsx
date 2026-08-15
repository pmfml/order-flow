import { NavLink } from 'react-router-dom'
import './AppLayout.css'

interface AppLayoutProps {
  children: React.ReactNode
}

export default function AppLayout({ children }: AppLayoutProps) {
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
          <button className="nav-link logout-btn" id="nav-logout">
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
