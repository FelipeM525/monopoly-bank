import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  AlertCircle,
  ArrowRight,
  ArrowRightLeft,
  CheckCircle2,
  Send,
} from 'lucide-react';
import { ApiError, apiClient } from '@/api/client';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
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
import type {
  AccountDto,
  CreateTransactionRequest,
  GameDto,
  TransactionDto,
} from '@/types/api';
import { formatCents, parseReaisToCents } from '@/utils/money';

const DEFAULT_REASONS = ['Purchase', 'Rent', 'Tax', 'Salary', 'Other'];

interface TransferFormCardProps {
  game: GameDto;
  reasons?: string[];
}

export function TransferFormCard({ game, reasons }: TransferFormCardProps) {
  const queryClient = useQueryClient();
  const availableReasons =
    reasons && reasons.length > 0 ? reasons : DEFAULT_REASONS;

  const allAccounts: AccountDto[] = [game.bank, ...game.players];
  const defaultFromId = game.players[0]?.id ?? game.bank.id;
  const defaultToId = game.bank.id;

  const [fromAccountId, setFromAccountId] = useState<number>(defaultFromId);
  const [toAccountId, setToAccountId] = useState<number>(defaultToId);
  const [amountInput, setAmountInput] = useState<string>('');
  const [reason, setReason] = useState<string>(availableReasons[0]);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [lastCompletedTx, setLastCompletedTx] =
    useState<TransactionDto | null>(null);

  // Generated once per user intent; reused across retries of the same form state;
  // rotated only after a successful transfer or when the user edits any form input.
  const idempotencyKeyRef = useRef<string>(crypto.randomUUID());

  function rotateIdempotencyKey() {
    idempotencyKeyRef.current = crypto.randomUUID();
    setErrorMessage(null);
    setLastCompletedTx(null);
  }

  const transferMutation = useMutation({
    mutationFn: (payload: {
      request: CreateTransactionRequest;
      idempotencyKey: string;
    }) =>
      apiClient.createTransaction(
        game.id,
        payload.request,
        payload.idempotencyKey,
      ),
    onSuccess: (tx) => {
      setLastCompletedTx(tx);
      setErrorMessage(null);
      setAmountInput('');
      idempotencyKeyRef.current = crypto.randomUUID();
      void queryClient.invalidateQueries({ queryKey: ['game', game.id] });
      void queryClient.invalidateQueries({
        queryKey: ['transactions', game.id],
      });
      void queryClient.invalidateQueries({ queryKey: ['games'] });
    },
    onError: (err: unknown) => {
      if (err instanceof ApiError) {
        setErrorMessage(err.detail);
      } else {
        setErrorMessage(
          'Transfer failed due to a network error. You can safely tap Confirm to retry without double-charging.',
        );
      }
    },
  });

  function handleSwapDirection() {
    setFromAccountId(toAccountId);
    setToAccountId(fromAccountId);
    rotateIdempotencyKey();
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setErrorMessage(null);
    setLastCompletedTx(null);

    if (fromAccountId === toAccountId) {
      setErrorMessage('Payer and receiver cannot be the same account.');
      return;
    }

    const parsedAmountCents = parseReaisToCents(amountInput);
    if (parsedAmountCents === null || parsedAmountCents <= 0) {
      setErrorMessage('Enter a transfer amount greater than R$ 0,00.');
      return;
    }

    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setErrorMessage('Transfer reason is required.');
      return;
    }

    transferMutation.mutate({
      request: {
        fromAccountId,
        toAccountId,
        amountCents: parsedAmountCents,
        reason: trimmedReason,
      },
      idempotencyKey: idempotencyKeyRef.current,
    });
  }

  return (
    <Card data-testid="transfer-form-card">
      <CardHeader>
        <div className="flex items-center justify-between">
          <div>
            <CardTitle className="text-lg">New Transaction</CardTitle>
            <CardDescription>
              Move money between players and the bank.
            </CardDescription>
          </div>
          <ArrowRightLeft className="h-5 w-5 text-muted-foreground" />
        </div>
      </CardHeader>

      <form onSubmit={handleSubmit} noValidate>
        <CardContent className="space-y-4">
          {errorMessage && (
            <Alert variant="destructive">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>Transfer Rejected</AlertTitle>
              <AlertDescription>{errorMessage}</AlertDescription>
            </Alert>
          )}

          {lastCompletedTx && (
            <Alert className="border-emerald-300 bg-emerald-50/80 text-emerald-950 dark:border-emerald-800 dark:bg-emerald-950/40 dark:text-emerald-200">
              <CheckCircle2 className="h-4 w-4 text-emerald-700 dark:text-emerald-400" />
              <AlertTitle>Transfer Confirmed</AlertTitle>
              <AlertDescription>
                {lastCompletedTx.fromAccount.name} paid{' '}
                <strong>{formatCents(lastCompletedTx.amountCents)}</strong> to{' '}
                {lastCompletedTx.toAccount.name} ({lastCompletedTx.reason}).
              </AlertDescription>
            </Alert>
          )}

          <div className="grid gap-3 sm:grid-cols-11 sm:items-end">
            <div className="space-y-1.5 sm:col-span-5">
              <Label htmlFor="transfer-from">From (Payer)</Label>
              <select
                id="transfer-from"
                value={fromAccountId}
                onChange={(e) => {
                  setFromAccountId(Number(e.target.value));
                  rotateIdempotencyKey();
                }}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {allAccounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {formatAccountOptionLabel(acc)}
                  </option>
                ))}
              </select>
            </div>

            <div className="flex justify-center sm:col-span-1">
              <Button
                type="button"
                variant="outline"
                size="icon"
                onClick={handleSwapDirection}
                aria-label="Swap payer and receiver"
                title="Swap payer and receiver"
              >
                <ArrowRight className="h-4 w-4" />
              </Button>
            </div>

            <div className="space-y-1.5 sm:col-span-5">
              <Label htmlFor="transfer-to">To (Receiver)</Label>
              <select
                id="transfer-to"
                value={toAccountId}
                onChange={(e) => {
                  setToAccountId(Number(e.target.value));
                  rotateIdempotencyKey();
                }}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {allAccounts.map((acc) => (
                  <option key={acc.id} value={acc.id}>
                    {formatAccountOptionLabel(acc)}
                  </option>
                ))}
              </select>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-1.5">
              <Label htmlFor="transfer-amount">Amount (R$)</Label>
              <Input
                id="transfer-amount"
                inputMode="decimal"
                placeholder="e.g. 200,00"
                value={amountInput}
                onChange={(e) => {
                  setAmountInput(e.target.value);
                  rotateIdempotencyKey();
                }}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="transfer-reason">Reason</Label>
              <select
                id="transfer-reason"
                value={reason}
                onChange={(e) => {
                  setReason(e.target.value);
                  rotateIdempotencyKey();
                }}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {availableReasons.map((r) => (
                  <option key={r} value={r}>
                    {r}
                  </option>
                ))}
              </select>
            </div>
          </div>
        </CardContent>

        <CardFooter className="flex justify-end border-t border-border pt-4">
          <Button
            type="submit"
            disabled={transferMutation.isPending}
            className="w-full sm:w-auto"
          >
            <Send className="h-4 w-4" />
            {transferMutation.isPending ? 'Processing...' : 'Confirm Transfer'}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}

function formatAccountOptionLabel(account: AccountDto): string {
  const prefix = account.type === 'BANK' ? '🏦 Bank' : account.name;
  return `${prefix} (${formatCents(account.balanceCents)})`;
}
