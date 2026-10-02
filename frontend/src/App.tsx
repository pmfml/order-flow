import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import { CartProvider } from './store/CartContext'
import ProtectedRoute from './auth/ProtectedRoute'
import AppLayout from './components/AppLayout'
import StorefrontLayout from './components/StorefrontLayout'
import LoginPage from './pages/LoginPage'
import OrdersPage from './pages/OrdersPage'
import OrderDetailPage from './pages/OrderDetailPage'

// Storefront placeholders
import StorefrontPage from './pages/store/StorefrontPage'
import ProductDetailPage from './pages/store/ProductDetailPage'
import CartPage from './pages/store/CartPage'
import CheckoutPage from './pages/store/CheckoutPage'

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CartProvider>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            
            {/* Admin Routes */}
            <Route
              path="/orders"
              element={
                <ProtectedRoute>
                  <AppLayout>
                    <OrdersPage />
                  </AppLayout>
                </ProtectedRoute>
              }
            />
            <Route
              path="/orders/:id"
              element={
                <ProtectedRoute>
                  <AppLayout>
                    <OrderDetailPage />
                  </AppLayout>
                </ProtectedRoute>
              }
            />

            {/* Storefront Routes */}
            <Route
              path="/store"
              element={
                <ProtectedRoute>
                  <StorefrontLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<StorefrontPage />} />
              <Route path="products/:id" element={<ProductDetailPage />} />
              <Route path="cart" element={<CartPage />} />
              <Route path="checkout" element={<CheckoutPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </CartProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
