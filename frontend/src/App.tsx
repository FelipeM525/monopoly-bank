import { Route, Routes } from 'react-router-dom';
import { AppShell } from '@/components/AppShell';
import { GameDashboardPage } from '@/pages/GameDashboardPage';
import { HomePage } from '@/pages/HomePage';
import { NewGamePage } from '@/pages/NewGamePage';

export default function App() {
  return (
    <Routes>
      <Route element={<AppShell />}>
        <Route path="/" element={<HomePage />} />
        <Route path="/games/new" element={<NewGamePage />} />
        <Route path="/games/:id" element={<GameDashboardPage />} />
      </Route>
    </Routes>
  );
}
