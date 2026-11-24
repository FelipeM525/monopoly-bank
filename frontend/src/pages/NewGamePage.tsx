import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AlertCircle, Check, Plus, Trash2, Users } from 'lucide-react';
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
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { cn } from '@/lib/utils';
import type { CreateGameRequest, GameConfig, PawnColor } from '@/types/api';
import {
  formatCents,
  formatCentsForInput,
  parseReaisToCents,
} from '@/utils/money';
import { ALL_PAWN_COLORS, PAWN_COLOR_THEMES } from '@/utils/pawnColors';

export const LAST_GAME_STORAGE_KEY = 'monopoly-bank:last-game-id';

const FALLBACK_CONFIG: GameConfig = {
  startingBalanceCents: 150_000,
  salaryCents: 20_000,
  minPlayers: 2,
  maxPlayers: 6,
  bank: { initialCashCents: 2_000_000, unlimited: false },
  colors: ALL_PAWN_COLORS,
  reasons: ['Purchase', 'Rent', 'Tax', 'Salary', 'Other'],
};

interface PlayerRowState {
  key: string;
  name: string;
  color: PawnColor;
  startingBalanceInput: string;
  balanceEdited: boolean;
}

interface PlayerRowErrors {
  name?: string;
  color?: string;
  startingBalance?: string;
}

function createInitialPlayers(defaultBalanceCents: number): PlayerRowState[] {
  const formatted = formatCentsForInput(defaultBalanceCents);
  return [
    {
      key: 'player-1',
      name: '',
      color: 'RED',
      startingBalanceInput: formatted,
      balanceEdited: false,
    },
    {
      key: 'player-2',
      name: '',
      color: 'BLUE',
      startingBalanceInput: formatted,
      balanceEdited: false,
    },
  ];
}

function pickNextAvailableColor(
  usedColors: Set<PawnColor>,
  availableColors: PawnColor[],
): PawnColor {
  return (
    availableColors.find((color) => !usedColors.has(color)) ??
    availableColors[0] ??
    'RED'
  );
}

function validatePlayerRows(
  players: PlayerRowState[],
): Record<number, PlayerRowErrors> {
  const rowErrors: Record<number, PlayerRowErrors> = {};
  const seenNames = new Map<string, number>();

  players.forEach((player, index) => {
    const errors: PlayerRowErrors = {};
    const trimmed = player.name.trim();
    const normalized = trimmed.toLowerCase();

    if (!trimmed) {
      errors.name = 'Player name is required.';
    } else if (normalized === 'bank') {
      errors.name = "Player name 'Bank' is reserved for the bank account.";
    } else if (seenNames.has(normalized)) {
      errors.name = `Duplicate player name '${trimmed}'. Each player needs a unique name.`;
    } else {
      seenNames.set(normalized, index);
    }

    const parsedCents = parseReaisToCents(player.startingBalanceInput);
    if (parsedCents === null) {
      errors.startingBalance = 'Enter a valid amount (e.g. 1500,00).';
    } else if (parsedCents < 0) {
      errors.startingBalance = 'Starting balance cannot be negative.';
    }

    if (Object.keys(errors).length > 0) {
      rowErrors[index] = errors;
    }
  });

  return rowErrors;
}

function mapServerFieldErrors(
  fieldErrors: Record<string, string>,
): Record<number, PlayerRowErrors> {
  const mapped: Record<number, PlayerRowErrors> = {};

  Object.entries(fieldErrors).forEach(([field, message]) => {
    const match = /^players\[(\d+)\]\.(\w+)$/.exec(field);
    if (!match) {
      return;
    }
    const rowIndex = Number.parseInt(match[1], 10);
    const prop = match[2];
    const current = mapped[rowIndex] ?? {};

    if (prop === 'name') {
      current.name = message;
    } else if (prop === 'color') {
      current.color = message;
    } else if (prop === 'startingBalanceCents') {
      current.startingBalance = message;
    }
    mapped[rowIndex] = current;
  });

  return mapped;
}

export function NewGamePage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: config = FALLBACK_CONFIG } = useQuery({
    queryKey: ['config'],
    queryFn: () => apiClient.getConfig(),
  });

  const [gameName, setGameName] = useState('');
  const [players, setPlayers] = useState<PlayerRowState[]>(() =>
    createInitialPlayers(FALLBACK_CONFIG.startingBalanceCents),
  );
  const [rowErrors, setRowErrors] = useState<Record<number, PlayerRowErrors>>(
    {},
  );
  const [formError, setFormError] = useState<string | null>(null);
  const [nextRowNumber, setNextRowNumber] = useState(3);

  useEffect(() => {
    const formattedDefault = formatCentsForInput(config.startingBalanceCents);
    setPlayers((prev) =>
      prev.map((row) =>
        row.balanceEdited
          ? row
          : { ...row, startingBalanceInput: formattedDefault },
      ),
    );
  }, [config.startingBalanceCents]);

  const createGameMutation = useMutation({
    mutationFn: (request: CreateGameRequest) => apiClient.createGame(request),
    onSuccess: (createdGame) => {
      localStorage.setItem(LAST_GAME_STORAGE_KEY, String(createdGame.id));
      queryClient.setQueryData(['game', createdGame.id], createdGame);
      void queryClient.invalidateQueries({ queryKey: ['games'] });
      void navigate(`/games/${createdGame.id}`);
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setFormError(err.detail);
        const serverRowErrors = mapServerFieldErrors(err.fieldErrors);
        if (Object.keys(serverRowErrors).length > 0) {
          setRowErrors(serverRowErrors);
        }
      } else {
        setFormError('Unable to create game. Please try again.');
      }
    },
  });

  const usedColors = new Set(players.map((p) => p.color));
  const canAddPlayer = players.length < config.maxPlayers;
  const canRemovePlayer = players.length > config.minPlayers;

  function handleAddPlayer() {
    if (!canAddPlayer) {
      return;
    }
    const nextColor = pickNextAvailableColor(usedColors, config.colors);
    const formattedDefault = formatCentsForInput(config.startingBalanceCents);

    setPlayers((prev) => [
      ...prev,
      {
        key: `player-${nextRowNumber}`,
        name: '',
        color: nextColor,
        startingBalanceInput: formattedDefault,
        balanceEdited: false,
      },
    ]);
    setNextRowNumber((n) => n + 1);
    setFormError(null);
  }

  function handleRemovePlayer(indexToRemove: number) {
    if (!canRemovePlayer) {
      return;
    }
    setPlayers((prev) => prev.filter((_, idx) => idx !== indexToRemove));
    setRowErrors({});
    setFormError(null);
  }

  function updatePlayerRow(
    index: number,
    patch: Partial<PlayerRowState>,
  ) {
    setPlayers((prev) =>
      prev.map((row, idx) => (idx === index ? { ...row, ...patch } : row)),
    );
    setRowErrors((prev) => {
      if (!prev[index]) {
        return prev;
      }
      const next = { ...prev };
      delete next[index];
      return next;
    });
    setFormError(null);
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);

    if (
      players.length < config.minPlayers ||
      players.length > config.maxPlayers
    ) {
      setFormError(
        `A game requires between ${config.minPlayers} and ${config.maxPlayers} players.`,
      );
      return;
    }

    const validationErrors = validatePlayerRows(players);
    if (Object.keys(validationErrors).length > 0) {
      setRowErrors(validationErrors);
      setFormError('Please fix the highlighted player fields before starting.');
      return;
    }

    const payload: CreateGameRequest = {
      name: gameName.trim() || undefined,
      players: players.map((row) => ({
        name: row.name.trim(),
        color: row.color,
        startingBalanceCents:
          parseReaisToCents(row.startingBalanceInput) ??
          config.startingBalanceCents,
      })),
    };

    createGameMutation.mutate(payload);
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-6">
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <div>
              <CardTitle className="text-2xl">New Game Setup</CardTitle>
              <CardDescription>
                Add {config.minPlayers} to {config.maxPlayers} players, choose a
                unique pawn color, and confirm starting balances.
              </CardDescription>
            </div>
            <Badge variant="secondary" className="gap-1.5 px-3 py-1 text-xs">
              <Users className="h-3.5 w-3.5" />
              {players.length} / {config.maxPlayers} Players
            </Badge>
          </div>
        </CardHeader>

        <CardContent className="space-y-6">
          {formError && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Cannot start game</AlertTitle>
              <AlertDescription>{formError}</AlertDescription>
            </Alert>
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="game-name">Table / Session Name (optional)</Label>
              <Input
                id="game-name"
                placeholder="e.g. Friday Night Banco Imobiliário"
                value={gameName}
                onChange={(e) => setGameName(e.target.value)}
                maxLength={100}
              />
            </div>

            <div className="flex flex-col justify-center rounded-lg border border-border bg-muted/40 px-4 py-3 text-xs text-muted-foreground">
              <span className="font-semibold text-foreground">
                Bank Initial Cash:{' '}
                {config.bank.unlimited
                  ? 'Unlimited (∞)'
                  : formatCents(config.bank.initialCashCents)}
              </span>
              <span className="mt-0.5">
                Default player starting balance:{' '}
                {formatCents(config.startingBalanceCents)} · Salary:{' '}
                {formatCents(config.salaryCents)}
              </span>
            </div>
          </div>

          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                Players
              </h2>
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={handleAddPlayer}
                disabled={!canAddPlayer}
              >
                <Plus className="h-4 w-4" />
                Add Player
              </Button>
            </div>

            <div className="space-y-3">
              {players.map((player, index) => (
                <PlayerSetupRow
                  key={player.key}
                  index={index}
                  player={player}
                  availableColors={config.colors}
                  usedColors={usedColors}
                  canRemove={canRemovePlayer}
                  errors={rowErrors[index]}
                  onChange={(patch) => updatePlayerRow(index, patch)}
                  onRemove={() => handleRemovePlayer(index)}
                />
              ))}
            </div>
          </div>
        </CardContent>

        <CardFooter className="flex items-center justify-between border-t border-border pt-6">
          <Button type="button" variant="ghost" asChild>
            <Link to="/">Cancel</Link>
          </Button>
          <Button
            type="submit"
            size="lg"
            disabled={createGameMutation.isPending}
          >
            {createGameMutation.isPending
              ? 'Creating Game...'
              : 'Start Game'}
          </Button>
        </CardFooter>
      </Card>
    </form>
  );
}

interface PlayerSetupRowProps {
  index: number;
  player: PlayerRowState;
  availableColors: PawnColor[];
  usedColors: Set<PawnColor>;
  canRemove: boolean;
  errors?: PlayerRowErrors;
  onChange: (patch: Partial<PlayerRowState>) => void;
  onRemove: () => void;
}

function PlayerSetupRow({
  index,
  player,
  availableColors,
  usedColors,
  canRemove,
  errors,
  onChange,
  onRemove,
}: PlayerSetupRowProps) {
  const playerNumber = index + 1;
  const nameInputId = `player-${playerNumber}-name`;
  const balanceInputId = `player-${playerNumber}-balance`;

  return (
    <div
      data-testid={`player-row-${index}`}
      className="rounded-xl border border-border bg-card p-4 shadow-2xs"
    >
      <div className="grid gap-4 sm:grid-cols-12 sm:items-start">
        <div className="space-y-1.5 sm:col-span-5">
          <Label htmlFor={nameInputId}>Player {playerNumber} Name</Label>
          <Input
            id={nameInputId}
            placeholder={`e.g. Player ${playerNumber}`}
            value={player.name}
            onChange={(e) => onChange({ name: e.target.value })}
            aria-invalid={Boolean(errors?.name)}
          />
          {errors?.name && (
            <p className="text-xs font-medium text-destructive">
              {errors.name}
            </p>
          )}
        </div>

        <div className="space-y-1.5 sm:col-span-4">
          <span className="block text-sm font-medium leading-none">
            Pawn Color
          </span>
          <div
            role="radiogroup"
            aria-label={`Player ${playerNumber} pawn color`}
            className="flex flex-wrap items-center gap-2 pt-1"
          >
            {availableColors.map((color) => {
              const isSelected = player.color === color;
              const isTakenByOther = !isSelected && usedColors.has(color);
              const theme = PAWN_COLOR_THEMES[color];

              return (
                <button
                  key={color}
                  type="button"
                  role="radio"
                  aria-checked={isSelected}
                  aria-label={`${theme.label} pawn`}
                  disabled={isTakenByOther}
                  onClick={() => onChange({ color })}
                  className={cn(
                    'flex h-8 w-8 items-center justify-center rounded-full text-white transition-transform focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2',
                    theme.swatchClass,
                    theme.ringClass,
                    isSelected && 'scale-110 ring-2 ring-offset-2',
                    isTakenByOther && 'cursor-not-allowed opacity-25',
                  )}
                >
                  {isSelected && <Check className="h-4 w-4 stroke-[3]" />}
                </button>
              );
            })}
          </div>
          {errors?.color && (
            <p className="text-xs font-medium text-destructive">
              {errors.color}
            </p>
          )}
        </div>

        <div className="space-y-1.5 sm:col-span-3">
          <div className="flex items-center justify-between">
            <Label htmlFor={balanceInputId}>Starting Balance (R$)</Label>
            {canRemove && (
              <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={onRemove}
                aria-label={`Remove Player ${playerNumber}`}
                className="h-6 px-2 text-xs text-muted-foreground hover:text-destructive"
              >
                <Trash2 className="h-3.5 w-3.5" />
                Remove
              </Button>
            )}
          </div>
          <Input
            id={balanceInputId}
            inputMode="decimal"
            value={player.startingBalanceInput}
            onChange={(e) =>
              onChange({
                startingBalanceInput: e.target.value,
                balanceEdited: true,
              })
            }
            aria-invalid={Boolean(errors?.startingBalance)}
          />
          {errors?.startingBalance && (
            <p className="text-xs font-medium text-destructive">
              {errors.startingBalance}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
