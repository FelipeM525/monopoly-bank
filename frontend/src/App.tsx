import { Link, Route, Routes } from 'react-router-dom';
import { AppShell } from '@/components/AppShell';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { GameDashboardPage } from '@/pages/GameDashboardPage';
import { NewGamePage } from '@/pages/NewGamePage';

function HomePage() {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">
          The Bank of Banco Imobiliário
        </CardTitle>
        <CardDescription>
          Fast, accurate table banking for your board game session.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Button asChild>
          <Link to="/games/new">Start a New Game</Link>
        </Button>
      </CardContent>
    </Card>
  );
}

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
