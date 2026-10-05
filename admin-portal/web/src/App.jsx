import { Navigate, Route, Routes, useParams } from 'react-router-dom';
import RequireAdmin from './auth/RequireAdmin.jsx';
import AccessDenied from './screens/AccessDenied.jsx';
import Dashboard from './screens/Dashboard.jsx';
import BookingList from './screens/bookings/BookingList.jsx';
import CheckIn from './screens/bookings/CheckIn.jsx';
import CinemaForm from './screens/cinemas/CinemaForm.jsx';
import CinemaList from './screens/cinemas/CinemaList.jsx';
import FoodList from './screens/concessions/FoodList.jsx';
import FoodOrders from './screens/concessions/FoodOrders.jsx';
import Notifications from './screens/people/Notifications.jsx';
import Settings from './screens/people/Settings.jsx';
import UserDetail from './screens/people/UserDetail.jsx';
import UserList from './screens/people/UserList.jsx';
import PromoList from './screens/concessions/PromoList.jsx';
import HallList from './screens/halls/HallList.jsx';
import Login from './screens/Login.jsx';
import MovieForm from './screens/movies/MovieForm.jsx';
import MovieList from './screens/movies/MovieList.jsx';
import ShowtimeForm from './screens/showtimes/ShowtimeForm.jsx';
import ShowtimeList from './screens/showtimes/ShowtimeList.jsx';
import Reports from './screens/Reports.jsx';
import Shell from './screens/Shell.jsx';

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/access-denied" element={<AccessDenied />} />
      <Route path="/" element={<RequireAdmin><Shell /></RequireAdmin>}>
        <Route index element={<Dashboard />} />
        <Route path="dashboard" element={<Navigate to="/" replace />} />
        <Route path="movies" element={<MovieList />} />
        <Route path="movies/:id" element={<ByParam component={MovieForm} />} />
        <Route path="cinemas" element={<CinemaList />} />
        <Route path="cinemas/:id" element={<ByParam component={CinemaForm} />} />
        <Route path="halls" element={<HallList />} />
        <Route path="showtimes" element={<ShowtimeList />} />
        <Route path="showtimes/:id" element={<ByParam component={ShowtimeForm} />} />
        <Route path="bookings" element={<BookingList />} />
        <Route path="checkin" element={<CheckIn />} />
        <Route path="food" element={<FoodList />} />
        <Route path="food-orders" element={<FoodOrders />} />
        <Route path="promos" element={<PromoList />} />
        <Route path="users" element={<UserList />} />
        <Route path="users/:id" element={<ByParam component={UserDetail} />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="settings" element={<Settings />} />
        <Route path="reports" element={<Reports />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

// Remounts a detail screen when :id changes (e.g. /movies/new → /movies/47 after create),
// so no state from the previous record survives.
function ByParam({ component: Component }) {
  const { id } = useParams();
  return <Component key={id} />;
}
