/**
 * Lightweight HTTP client for OrderFlow API calls.
 *
 * Reads auth state from sessionStorage (same key as AuthContext) so the
 * API layer stays decoupled from React — no hooks required.
 */

const STORAGE_KEY = 'orderflow_auth'

interface AuthState {
  token: string
  tenantId: string
}

function getAuth(): AuthState | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed.token && parsed.tenantId ? parsed : null
  } catch {
    return null
  }
}

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly detail: string,
  ) {
    super(detail)
    this.name = 'ApiError'
  }
}

/**
 * Authenticated fetch wrapper.
 *
 * - Attaches `Authorization: Bearer <token>` and `X-Tenant-Id` headers.
 * - Throws {@link ApiError} for non-2xx responses.
 * - Returns parsed JSON for 2xx, or `null` for 204 No Content.
 */
export async function apiFetch<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const auth = getAuth()

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  }

  if (auth) {
    headers['Authorization'] = `Bearer ${auth.token}`
    headers['X-Tenant-Id'] = auth.tenantId
  }

  const response = await fetch(path, {
    ...options,
    headers,
  })

  if (response.status === 204) {
    return null as T
  }

  if (!response.ok) {
    let detail = `Request failed with status ${response.status}`
    try {
      const body = await response.json()
      if (body.detail) detail = body.detail
    } catch {
      // body not JSON — keep generic message
    }
    throw new ApiError(response.status, detail)
  }

  return response.json() as Promise<T>
}
