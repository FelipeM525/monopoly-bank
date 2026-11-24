import { useEffect } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import {
  AlertCircle,
  ArrowRightLeft,
  Coins,
  Landmark,
  RefreshCw,
  Users,
} from 'lucide-react';
import { ApiError, apiClient } from '@/api/client';
import { TransferFormCard } from '@/components/TransferFormCard';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { cn } from '@/lib/utils';
import { LAST_GAME_STORAGE_KEY } from '@/pages/NewGamePage';
import type { AccountDto, GameConfig, GameDto } from '@/types/api';
import { formatCents } from '@/utils/money';
import { PAWN_COLOR_THEMES } from '@/utils/pawnColors';

export function GameDashboardPage() {
  const { id } = useParams<{ id: string }>();
  const gameId = Number(id);
  const isValidId = Number.isInteger(gameId) && gameId > 0;

  const {
    data: game,
    isLoading,
    isError,
    error,
    refetch,
    isFetching,
  } = useQuery({
    queryKey: ['game', gameId],
    queryFn: () => apiClient.getGame(gameId),
    enabled: isValidId,
  });

  const { data: config } = useQuery({
    queryKey: ['config'],
    queryFn: () => apiClient.getConfig(),
  });

  useEffect(() => {
    if (game?.id) {
      localStorage.setItem(LAST_GAME_STORAGE_KEY, String(game.id));
    }
  }, [game?.id]);

  if (!isValidId) {
    return (
      <Alert variant="destructive">
        <AlertCircle className="h-4 w-4" />
        <AlertTitle>Invalid game ID</AlertTitle>
        <AlertDescription>
          The requested game URL is invalid.{' '}
          <Link to="/" className="font-semibold underline">
            Return to games
          </Link>
        </AlertDescription>
      </Alert>
    );
  }

  if (isLoading) {
    return (
      <Card aria-busy="true">
        <CardHeader>
          <CardTitle className="text-xl">Loading Game #{gameId}...</CardTitle>
          <CardDescription>
            Fetching player balances and bank cash.
          </CardDescription>
        </CardHeader>
      </Card>
    );
  }

  if (isError || !game) {
    const errorMessage =
      error instanceof ApiError
        ? error.detail
        : 'Unable to load game balances. Please check your connection.';

    return (
      <div className="space-y-4">
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertTitle>Could not load game</AlertTitle>
          <AlertDescription>{errorMessage}</AlertDescription>
        </Alert>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => void refetch()}>
            Try Again
          </Button>
          <Button variant="ghost" asChild>
            <Link to="/">Back to Home</Link>
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <DashboardHeader
        game={game}
        isRefreshing={isFetching}
        onRefresh={() => void refetch()}
      />

      <BankAndTotalSummary game={game} config={config} />

      <PlayerBalancesSection players={game.players} />

      <TransferFormCard game={game} reasons={config?.reasons} />
    </div>
  );
}

interface DashboardHeaderProps {
  game: GameDto;
  isRefreshing: boolean;
  onRefresh: () => void;
}

function DashboardHeader({
  game,
  isRefreshing,
  onRefresh,
}: DashboardHeaderProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div>
        <div className="flex items-center gap-2.5">
          <h1 className="text-2xl font-bold tracking-tight">{game.name}</h1>
          <Badge variant="secondary" className="gap-1">
            <Users className="h-3.5 w-3.5" />
            {game.players.length} Players
          </Badge>
        </div>
        <p className="mt-1 text-xs text-muted-foreground">
          Game #{game.id} · {game.transactionCount}{' '}
          {game.transactionCount === 1 ? 'transaction' : 'transactions'}
        </p>
      </div>

      <Button
        type="button"
        variant="outline"
        size="sm"
        onClick={onRefresh}
        disabled={isRefreshing}
      >
        <RefreshCw className={cn('h-4 w-4', isRefreshing && 'animate-spin')} />
        Refresh
      </Button>
    </div>
  );
}

interface BankAndTotalSummaryProps {
  game: GameDto;
  config?: GameConfig;
}

function BankAndTotalSummary({ game, config }: BankAndTotalSummaryProps) {
  const isUnlimitedBank = config?.bank.unlimited ?? false;

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Card
        data-testid="bank-balance-card"
        className="border-primary/30 bg-primary/5"
      >
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardDescription className="font-semibold uppercase tracking-wider text-primary">
              Cash Held by the Bank
            </CardDescription>
            <Landmark className="h-5 w-5 text-primary" />
          </div>
          <CardTitle className="text-3xl font-extrabold tracking-tight">
            {isUnlimitedBank ? '∞ Unlimited' : formatCents(game.bank.balanceCents)}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground">
            {isUnlimitedBank
              ? `Ledger net balance: ${formatCents(game.bank.balanceCents)}`
              : `Initial reserve: ${formatCents(game.bank.initialBalanceCents)}`}
          </p>
        </CardContent>
      </Card>

      <Card data-testid="total-money-card">
        <CardHeader className="pb-2">
          <div className="flex items-center justify-between">
            <CardDescription className="font-semibold uppercase tracking-wider">
              Total Money in Game
            </CardDescription>
            <Coins className="h-5 w-5 text-muted-foreground" />
          </div>
          <CardTitle className="text-3xl font-extrabold tracking-tight">
            {formatCents(game.totalMoneyCents)}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-xs text-muted-foreground">
            <ArrowRightLeft className="mr-1 inline h-3.5 w-3.5" />
            {game.transactionCount} recorded{' '}
            {game.transactionCount === 1 ? 'transfer' : 'transfers'}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}

interface PlayerBalancesSectionProps {
  players: AccountDto[];
}

function PlayerBalancesSection({ players }: PlayerBalancesSectionProps) {
  return (
    <section aria-labelledby="players-heading" className="space-y-3">
      <div className="flex items-center justify-between">
        <h2
          id="players-heading"
          className="text-sm font-semibold uppercase tracking-wider text-muted-foreground"
        >
          Player Balances
        </h2>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {players.map((player) => (
          <PlayerBalanceCard key={player.id} player={player} />
        ))}
      </div>
    </section>
  );
}

interface PlayerBalanceCardProps {
  player: AccountDto;
}

function PlayerBalanceCard({ player }: PlayerBalanceCardProps) {
  const theme = player.color ? PAWN_COLOR_THEMES[player.color] : null;
  const netDeltaCents = player.balanceCents - player.initialBalanceCents;
  const deltaPrefix = netDeltaCents > 0 ? '+' : '';

  return (
    <Card
      data-testid={`player-card-${player.name}`}
      className="transition-shadow hover:shadow-sm"
    >
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2.5">
            <span
              aria-hidden="true"
              className={cn(
                'h-4 w-4 shrink-0 rounded-full',
                theme?.swatchClass ?? 'bg-slate-400',
              )}
            />
            <CardTitle className="text-base font-bold">{player.name}</CardTitle>
          </div>
          {theme && (
            <Badge variant="outline" className={cn('text-xs', theme.badgeClass)}>
              {theme.label}
            </Badge>
          )}
        </div>
      </CardHeader>

      <CardContent>
        <p className="text-2xl font-extrabold tracking-tight">
          {formatCents(player.balanceCents)}
        </p>
        <p className="mt-1 text-xs text-muted-foreground">
          Started with {formatCents(player.initialBalanceCents)}
          {netDeltaCents !== 0 && (
            <span
              className={cn(
                'ml-1.5 font-medium',
                netDeltaCents > 0 ? 'text-emerald-700' : 'text-destructive',
              )}
            >
              ({deltaPrefix}
              {formatCents(netDeltaCents)})
            </span>
          )}
        </p>
      </CardContent>
    </Card>
  );
}
