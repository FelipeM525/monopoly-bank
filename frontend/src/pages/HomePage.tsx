import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  AlertCircle,
  ArrowRight,
  ArrowRightLeft,
  Clock,
  Gamepad2,
  Play,
  Plus,
  Users,
} from 'lucide-react';
import { ApiError, apiClient } from '@/api/client';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { LAST_GAME_STORAGE_KEY } from '@/pages/NewGamePage';
import type { GameSummaryDto } from '@/types/api';

export function HomePage() {
  const {
    data: games = [],
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['games'],
    queryFn: () => apiClient.listGames(),
  });

  const storedLastId = Number(localStorage.getItem(LAST_GAME_STORAGE_KEY));
  const lastActiveGame =
    games.find((g) => g.id === storedLastId) ?? games[0] ?? null;

  return (
    <div className="space-y-6">
      <HeroBanner lastActiveGame={lastActiveGame} />

      <section aria-labelledby="saved-games-heading" className="space-y-3">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <h2
              id="saved-games-heading"
              className="text-sm font-semibold uppercase tracking-wider text-muted-foreground"
            >
              Active & Saved Games
            </h2>
            {!isLoading && !isError && (
              <Badge variant="secondary" className="text-xs">
                {games.length}
              </Badge>
            )}
          </div>
        </div>

        {isLoading && (
          <Card aria-busy="true">
            <CardContent className="py-8 text-center text-sm text-muted-foreground">
              Loading saved games...
            </CardContent>
          </Card>
        )}

        {isError && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Could not load games</AlertTitle>
            <AlertDescription>
              {error instanceof ApiError
                ? error.detail
                : 'Failed to load saved games from the server.'}
            </AlertDescription>
          </Alert>
        )}

        {!isLoading && !isError && games.length === 0 && (
          <Card data-testid="empty-games-state">
            <CardContent className="flex flex-col items-center justify-center py-10 text-center">
              <Gamepad2 className="h-9 w-9 text-muted-foreground/60" />
              <p className="mt-3 text-base font-semibold text-foreground">
                No games started yet
              </p>
              <p className="mt-1 max-w-md text-xs text-muted-foreground">
                Create your first table session with 2 to 6 players to start
                tracking balances and transactions.
              </p>
              <Button asChild className="mt-5">
                <Link to="/games/new">
                  <Plus className="h-4 w-4" />
                  Create First Game
                </Link>
              </Button>
            </CardContent>
          </Card>
        )}

        {!isLoading && !isError && games.length > 0 && (
          <div className="grid gap-4 sm:grid-cols-2">
            {games.map((game) => (
              <GameSummaryCard
                key={game.id}
                game={game}
                isLastActive={lastActiveGame?.id === game.id}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

interface HeroBannerProps {
  lastActiveGame: GameSummaryDto | null;
}

function HeroBanner({ lastActiveGame }: HeroBannerProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">
          The Bank of Banco Imobiliário
        </CardTitle>
        <CardDescription>
          Fast, accurate table banking for your board game session. All games
          are saved automatically so you can close the browser or restart the
          server without losing state.
        </CardDescription>
      </CardHeader>

      <CardContent className="flex flex-wrap items-center gap-3">
        {lastActiveGame && (
          <Button asChild size="lg">
            <Link
              to={`/games/${lastActiveGame.id}`}
              data-testid="continue-last-game-link"
            >
              <Play className="h-4 w-4" />
              Continue {lastActiveGame.name} (#{lastActiveGame.id})
            </Link>
          </Button>
        )}

        <Button
          asChild
          variant={lastActiveGame ? 'outline' : 'default'}
          size="lg"
        >
          <Link to="/games/new">
            <Plus className="h-4 w-4" />
            Start a New Game
          </Link>
        </Button>
      </CardContent>
    </Card>
  );
}

interface GameSummaryCardProps {
  game: GameSummaryDto;
  isLastActive: boolean;
}

function GameSummaryCard({ game, isLastActive }: GameSummaryCardProps) {
  return (
    <Card
      data-testid={`game-summary-card-${game.id}`}
      className="flex flex-col justify-between transition-shadow hover:shadow-sm"
    >
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div>
            <CardTitle className="text-lg">{game.name}</CardTitle>
            <CardDescription className="mt-1 flex items-center gap-1 text-xs">
              <Clock className="h-3.5 w-3.5" />
              Game #{game.id} · {formatGameDate(game.createdAt)}
            </CardDescription>
          </div>
          {isLastActive && (
            <Badge variant="default" className="text-xs">
              Recent
            </Badge>
          )}
        </div>
      </CardHeader>

      <CardContent className="pb-3">
        <div className="flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
          <span className="inline-flex items-center gap-1">
            <Users className="h-3.5 w-3.5" />
            {game.playerCount} Players
          </span>
          <span className="inline-flex items-center gap-1">
            <ArrowRightLeft className="h-3.5 w-3.5" />
            {game.transactionCount}{' '}
            {game.transactionCount === 1 ? 'Transaction' : 'Transactions'}
          </span>
        </div>
      </CardContent>

      <CardFooter className="border-t border-border pt-3">
        <Button asChild variant="secondary" size="sm" className="w-full">
          <Link to={`/games/${game.id}`}>
            Open Game
            <ArrowRight className="h-4 w-4" />
          </Link>
        </Button>
      </CardFooter>
    </Card>
  );
}

function formatGameDate(isoTimestamp: string): string {
  const date = new Date(isoTimestamp);
  if (Number.isNaN(date.getTime())) {
    return isoTimestamp;
  }
  return date.toLocaleString('pt-BR', {
    dateStyle: 'short',
    timeStyle: 'short',
  });
}
