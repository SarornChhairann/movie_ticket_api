import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext.jsx';

export default function RequireAdmin({ children }) {
  const { session } = useAuth();
  const location = useLocation();

  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (session.role !== 'ADMIN') return <Navigate to="/access-denied" replace />;
  return children;
}
