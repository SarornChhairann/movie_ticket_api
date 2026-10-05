import { Link } from 'react-router-dom';

export default function AccessDenied() {
  return (
    <div className="center-page">
      <div className="card">
        <h1>Access denied</h1>
        <p className="muted">
          This portal is for administrators only. Customer accounts can book tickets in the mobile app.
        </p>
        <Link className="button" to="/login">Sign in with another account</Link>
      </div>
    </div>
  );
}
