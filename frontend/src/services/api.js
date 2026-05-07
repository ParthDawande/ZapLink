import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('zaplink_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Only force-redirect if a token was present (expired/revoked session).
      // If there was no token, the 401 came from a public endpoint (e.g. wrong
      // login password) and should bubble up to the page's own error handler.
      const hadToken = !!localStorage.getItem('zaplink_token')
      localStorage.removeItem('zaplink_token')
      localStorage.removeItem('zaplink_user')
      if (hadToken) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default api
