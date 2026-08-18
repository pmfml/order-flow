import { createContext, useContext, useState, useCallback, type ReactNode } from 'react'

interface AuthState {
  token: string
  tenantId: string
}

interface AuthContextType {
  auth: AuthState | null
  login: (tenantId: string, token: string) => void
  logout: () => void
  isAuthenticated: boolean
}

const AuthContext = createContext<AuthContextType | null>(null)

const STORAGE_KEY = 'orderflow_auth'

function loadPersistedAuth(): AuthState | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    if (parsed.token && parsed.tenantId) return parsed
    return null
  } catch {
    return null
  }
}

function persistAuth(auth: AuthState | null): void {
  if (auth) {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(auth))
  } else {
    sessionStorage.removeItem(STORAGE_KEY)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [auth, setAuth] = useState<AuthState | null>(loadPersistedAuth)

  const login = useCallback((tenantId: string, token: string) => {
    const state: AuthState = { tenantId, token }
    setAuth(state)
    persistAuth(state)
  }, [])

  const logout = useCallback(() => {
    setAuth(null)
    persistAuth(null)
  }, [])

  const value: AuthContextType = {
    auth,
    login,
    logout,
    isAuthenticated: auth !== null,
  }

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  )
}

/**
 * Hook to access auth state. Throws if used outside AuthProvider.
 */
export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
