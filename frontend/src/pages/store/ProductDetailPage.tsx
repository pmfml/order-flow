import { useParams } from 'react-router-dom'

export default function ProductDetailPage() {
  const { id } = useParams()
  return (
    <div>
      <h1>Product Detail: {id}</h1>
      <p style={{ color: '#888', marginTop: '1rem' }}>Detail view will be implemented in Micro-Passo 4.</p>
    </div>
  )
}
