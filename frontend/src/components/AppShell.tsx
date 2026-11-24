import { Link, Outlet } from 'react-router-dom';
import { Landmark, Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';

export function AppShell() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3 sm:px-6">
          <Link to="/" className="flex items-center gap-2.5">
            <span
              aria-hidden="true"
              className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground shadow-xs"
            >
              <Landmark className="h-5 w-5" />
            </span>
            <div>
              <span className="text-base font-bold tracking-tight">
                The Bank of Banco Imobiliário
              </span>
              <span className="block text-xs text-muted-foreground">
                Table Banker Assistant
              </span>
            </div>
          </Link>

          <nav aria-label="Main navigation" className="flex items-center gap-2">
            <Button variant="ghost" asChild>
              <Link to="/">Games</Link>
            </Button>
            <Button asChild>
              <Link to="/games/new">
                <Plus className="h-4 w-4" />
                New Game
              </Link>
            </Button>
          </nav>
        </div>
      </header>

      <main className="mx-auto max-w-5xl px-4 py-6 sm:px-6">
        <Outlet />
      </main>
    </div>
  );
}
