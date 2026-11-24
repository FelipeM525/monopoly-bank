import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AlertCircle,
  ArrowRight,
  ChevronLeft,
  ChevronRight,
  History,
  Undo2,
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
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import type {
  AccountParticipantDto,
  GameDto,
  TransactionDto,
} from '@/types/api';
import { formatCents } from '@/utils/money';
import { PAWN_COLOR_THEMES } from '@/utils/pawnColors';

const DEFAULT_PAGE_SIZE = 10;

interface TransactionHistoryCardProps {
  game: GameDto;
}

export function TransactionHistoryCard({ game }: TransactionHistoryCardProps) {
  const [selectedAccountId, setSelectedAccountId] = useState<number | null>(
    null,
  );
  const [page, setPage] = useState<number>(0);

  const {
    data: historyPage,
    isLoading,
    isError,
    error,
  } = useQuery({
    queryKey: ['transactions', game.id, selectedAccountId, page],
    queryFn: () =>
      apiClient.listTransactions(game.id, {
        accountId: selectedAccountId,
        page,
        size: DEFAULT_PAGE_SIZE,
      }),
  });

  function handleFilterChange(event: React.ChangeEvent<HTMLSelectElement>) {
    const rawValue = event.target.value;
    setSelectedAccountId(rawValue === '' ? null : Number(rawValue));
    setPage(0);
  }

  const items = historyPage?.items ?? [];
  const totalElements = historyPage?.totalElements ?? 0;
  const totalPages = historyPage?.totalPages ?? 0;
  const hasPrevious = page > 0;
  const hasNext = historyPage?.hasNext ?? false;

  return (
    <Card data-testid="transaction-history-card">
      <CardHeader>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <div className="flex items-center gap-2">
              <CardTitle className="text-lg">Transaction History</CardTitle>
              <Badge variant="secondary" className="text-xs">
                {totalElements}
              </Badge>
            </div>
            <CardDescription>
              Every transfer recorded newest-first.
            </CardDescription>
          </div>

          <div className="flex items-center gap-2">
            <Label htmlFor="history-player-filter" className="text-xs">
              Filter by player
            </Label>
            <select
              id="history-player-filter"
              value={selectedAccountId ?? ''}
              onChange={handleFilterChange}
              className="flex h-9 rounded-md border border-input bg-background px-3 py-1 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              <option value="">All Players & Bank</option>
              {game.players.map((player) => (
                <option key={player.id} value={player.id}>
                  {player.name}
                </option>
              ))}
              <option value={game.bank.id}>🏦 Bank</option>
            </select>
          </div>
        </div>
      </CardHeader>

      <CardContent>
        {isLoading && (
          <p className="py-6 text-center text-sm text-muted-foreground">
            Loading transaction history...
          </p>
        )}

        {isError && (
          <Alert variant="destructive">
            <AlertCircle className="h-4 w-4" />
            <AlertTitle>Could not load history</AlertTitle>
            <AlertDescription>
              {error instanceof ApiError
                ? error.detail
                : 'Failed to load transaction history.'}
            </AlertDescription>
          </Alert>
        )}

        {!isLoading && !isError && items.length === 0 && (
          <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border py-8 text-center">
            <History className="h-7 w-7 text-muted-foreground/60" />
            <p className="mt-2 text-sm font-medium text-foreground">
              No transactions found
            </p>
            <p className="mt-0.5 text-xs text-muted-foreground">
              {selectedAccountId === null
                ? 'Transfers will appear here as soon as money moves.'
                : 'No transfers recorded for the selected participant yet.'}
            </p>
          </div>
        )}

        {!isLoading && !isError && items.length > 0 && (
          <ul
            aria-label="Transaction history list"
            className="divide-y divide-border"
          >
            {items.map((tx) => (
              <TransactionHistoryRow
                key={tx.id}
                tx={tx}
                selectedAccountId={selectedAccountId}
              />
            ))}
          </ul>
        )}
      </CardContent>

      {totalPages > 1 && (
        <CardFooter className="flex items-center justify-between border-t border-border pt-4">
          <span className="text-xs text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <div className="flex items-center gap-2">
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!hasPrevious}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              <ChevronLeft className="h-4 w-4" />
              Previous
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={!hasNext}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </CardFooter>
      )}
    </Card>
  );
}

interface TransactionHistoryRowProps {
  tx: TransactionDto;
  selectedAccountId: number | null;
}

function TransactionHistoryRow({
  tx,
  selectedAccountId,
}: TransactionHistoryRowProps) {
  const isOutgoingForSelected =
    selectedAccountId !== null && tx.fromAccount.id === selectedAccountId;
  const isIncomingForSelected =
    selectedAccountId !== null && tx.toAccount.id === selectedAccountId;

  const amountPrefix = isOutgoingForSelected
    ? '-'
    : isIncomingForSelected
      ? '+'
      : '';

  return (
    <li
      data-testid={`transaction-row-${tx.id}`}
      className={cn(
        'flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0',
        tx.reversed && 'opacity-60',
      )}
    >
      <div className="space-y-1">
        <div className="flex flex-wrap items-center gap-1.5 text-sm font-semibold">
          <ParticipantPill participant={tx.fromAccount} />
          <ArrowRight className="h-3.5 w-3.5 text-muted-foreground" />
          <ParticipantPill participant={tx.toAccount} />
        </div>

        <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <Badge variant="outline" className="text-xs font-normal">
            {tx.reason}
          </Badge>
          <span>{formatTransactionTime(tx.createdAt)}</span>
          {tx.reversalOfTransactionId !== null && (
            <Badge variant="secondary" className="gap-1 text-xs">
              <Undo2 className="h-3 w-3" />
              Reversal of #{tx.reversalOfTransactionId}
            </Badge>
          )}
          {tx.reversed && (
            <Badge variant="outline" className="text-xs">
              Undone
            </Badge>
          )}
        </div>
      </div>

      <div
        className={cn(
          'text-base font-bold tabular-nums',
          isOutgoingForSelected && 'text-destructive',
          isIncomingForSelected && 'text-emerald-700 dark:text-emerald-400',
          tx.reversed && 'line-through',
        )}
      >
        {amountPrefix}
        {formatCents(tx.amountCents)}
      </div>
    </li>
  );
}

function ParticipantPill({
  participant,
}: {
  participant: AccountParticipantDto;
}) {
  if (participant.type === 'BANK') {
    return <span>🏦 Bank</span>;
  }

  const theme = participant.color
    ? PAWN_COLOR_THEMES[participant.color]
    : null;

  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        aria-hidden="true"
        className={cn(
          'h-2.5 w-2.5 rounded-full',
          theme?.swatchClass ?? 'bg-slate-400',
        )}
      />
      <span>{participant.name}</span>
    </span>
  );
}

function formatTransactionTime(isoTimestamp: string): string {
  const date = new Date(isoTimestamp);
  if (Number.isNaN(date.getTime())) {
    return isoTimestamp;
  }
  return date.toLocaleTimeString('pt-BR', {
    hour: '2-digit',
    minute: '2-digit',
  });
}
