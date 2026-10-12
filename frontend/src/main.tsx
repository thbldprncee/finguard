import React from 'react'
import ReactDOM from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import App from './App'
import { initAuth } from './auth/keycloak'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
})

const root = ReactDOM.createRoot(document.getElementById('root')!)

// login-required: unauthenticated visitors are redirected to the Keycloak login page
// (which also offers self-registration, always with role USER).
initAuth()
  .then(() => {
    root.render(
      <React.StrictMode>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </React.StrictMode>
    )
  })
  .catch(() => {
    root.render(
      <div className="min-h-screen bg-gray-950 flex items-center justify-center text-gray-300">
        Could not reach the sign-in service. Please try again later.
      </div>
    )
  })
