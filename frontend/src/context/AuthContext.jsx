import { createContext, useContext, useState } from 'react'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('zaplink_token'))
  const [user, setUser] = useState(() => {
    try {
      const raw = localStorage.getItem('zaplink_user')
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  })

  const login = (newToken, newUser) => {
    localStorage.setItem('zaplink_token', newToken)
    localStorage.setItem('zaplink_user', JSON.stringify(newUser))
    setToken(newToken)
    setUser(newUser)
  }

  const logout = () => {
    localStorage.removeItem('zaplink_token')
    localStorage.removeItem('zaplink_user')
    setToken(null)
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, token, login, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
