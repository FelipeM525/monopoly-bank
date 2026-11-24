import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '@/App';
import { ApiError, apiClient } from '@/api/client';
import type { GameConfig, GameDto, TransactionDto } from '@/types/api';

const MOCK_CONFIG: GameConfig = {
  startingBalanceCents: 150_000,
  salaryCents: 20_000,
  minPlayers: 2,
  maxPlayers: 6,
  bank: { initialCashCents: 2_000_000, unlimited: false },
  colors: ['RED', 'BLUE', 'GREEN', 'YELLOW', 'PURPLE', 'ORANGE'],
  reasons: ['Purchase', 'Rent', 'Tax', 'Salary', 'Other'],
};

const MOCK_GAME: GameDto = {
  id: 12,
  name: 'Friday Night Session',
  createdAt: '2026-11-24T12:00:00Z',
  bank: {
    id: 1,
    type: 'BANK',
    name: 'Bank',
    color: null,
    initialBalanceCents: 2_000_000,
    balanceCents: 1_980_000,
  },
  players: [
    {
      id: 2,
      type: 'PLAYER',
      name: 'Ana',
      color: 'RED',
      initialBalanceCents: 150_000,
      balanceCents: 185_000,
    },
    {
      id: 3,
      type: 'PLAYER',
      name: 'Bruno',
      color: 'BLUE',
      initialBalanceCents: 150_000,
      balanceCents: 135_000,
    },
  ],
  totalMoneyCents: 2_300_000,
  transactionCount: 3,
};

function renderDashboard(route = '/games/12') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('GameDashboardPage (R2)', () => {
  beforeEach(() => {
    vi.spyOn(apiClient, 'getConfig').mockResolvedValue(MOCK_CONFIG);
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('displays every player balance with pawn color, the bank cash, and total money on one screen', async () => {
    vi.spyOn(apiClient, 'getGame').mockResolvedValue(MOCK_GAME);

    renderDashboard('/games/12');

    expect(
      await screen.findByRole('heading', { name: 'Friday Night Session' }),
    ).toBeInTheDocument();

    const bankCard = screen.getByTestId('bank-balance-card');
    expect(within(bankCard).getByText('R$ 19.800,00')).toBeInTheDocument();

    const totalCard = screen.getByTestId('total-money-card');
    expect(within(totalCard).getByText('R$ 23.000,00')).toBeInTheDocument();

    const anaCard = screen.getByTestId('player-card-Ana');
    expect(within(anaCard).getByText('Ana')).toBeInTheDocument();
    expect(within(anaCard).getByText('Red')).toBeInTheDocument();
    expect(within(anaCard).getByText('R$ 1.850,00')).toBeInTheDocument();

    const brunoCard = screen.getByTestId('player-card-Bruno');
    expect(within(brunoCard).getByText('Bruno')).toBeInTheDocument();
    expect(within(brunoCard).getByText('Blue')).toBeInTheDocument();
    expect(within(brunoCard).getByText('R$ 1.350,00')).toBeInTheDocument();
  });

  it('shows ∞ Unlimited when the bank policy is configured as unlimited', async () => {
    vi.spyOn(apiClient, 'getConfig').mockResolvedValue({
      ...MOCK_CONFIG,
      bank: { initialCashCents: 2_000_000, unlimited: true },
    });
    vi.spyOn(apiClient, 'getGame').mockResolvedValue(MOCK_GAME);

    renderDashboard('/games/12');

    const bankCard = await screen.findByTestId('bank-balance-card');
    expect(within(bankCard).getByText(/∞ Unlimited/i)).toBeInTheDocument();
  });

  it('renders a clear ProblemDetail error state when the game is not found', async () => {
    vi.spyOn(apiClient, 'getGame').mockRejectedValue(
      new ApiError(404, {
        status: 404,
        title: 'Resource Not Found',
        detail: 'Game #999 was not found.',
      }),
    );

    renderDashboard('/games/999');

    expect(
      await screen.findByText('Game #999 was not found.'),
    ).toBeInTheDocument();
  });

  it('submits a transfer with a UUID Idempotency-Key and refreshes game balances on success', async () => {
    const user = userEvent.setup();
    vi.spyOn(apiClient, 'getGame').mockResolvedValue(MOCK_GAME);

    const mockTx: TransactionDto = {
      id: 501,
      gameId: 12,
      fromAccount: {
        id: 2,
        type: 'PLAYER',
        name: 'Ana',
        color: 'RED',
        balanceCents: 165_000,
      },
      toAccount: {
        id: 1,
        type: 'BANK',
        name: 'Bank',
        color: null,
        balanceCents: 2_000_000,
      },
      amountCents: 20_000,
      reason: 'Tax',
      idempotencyKey: '11111111-2222-3333-4444-555555555555',
      reversalOfTransactionId: null,
      reversed: false,
      createdAt: '2026-11-24T12:05:00Z',
    };

    const transferSpy = vi
      .spyOn(apiClient, 'createTransaction')
      .mockResolvedValue(mockTx);

    renderDashboard('/games/12');
    await screen.findByRole('heading', { name: 'Friday Night Session' });

    await user.type(screen.getByLabelText(/Amount \(R\$\)/i), '200,00');
    await user.selectOptions(screen.getByLabelText(/Reason/i), 'Tax');
    await user.click(screen.getByRole('button', { name: /Confirm Transfer/i }));

    await waitFor(() => {
      expect(transferSpy).toHaveBeenCalledTimes(1);
    });

    const [calledGameId, calledPayload, calledKey] =
      transferSpy.mock.calls[0];
    expect(calledGameId).toBe(12);
    expect(calledPayload).toEqual({
      fromAccountId: 2,
      toAccountId: 1,
      amountCents: 20_000,
      reason: 'Tax',
    });
    expect(calledKey).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
    );

    expect(
      await screen.findByText(/Transfer Confirmed/i),
    ).toBeInTheDocument();
  });

  it('reuses the exact same Idempotency-Key on retry when a network error occurs and rotates it only after edits or success', async () => {
    const user = userEvent.setup();
    vi.spyOn(apiClient, 'getGame').mockResolvedValue(MOCK_GAME);

    const transferSpy = vi
      .spyOn(apiClient, 'createTransaction')
      .mockRejectedValueOnce(new Error('Network timeout'))
      .mockRejectedValueOnce(
        new ApiError(422, {
          status: 422,
          title: 'Insufficient Funds',
          detail: 'Ana has R$ 1.850,00 and cannot pay R$ 5.000,00.',
        }),
      );

    renderDashboard('/games/12');
    await screen.findByRole('heading', { name: 'Friday Night Session' });

    await user.type(screen.getByLabelText(/Amount \(R\$\)/i), '5000,00');

    // 1st click fails with network error
    await user.click(screen.getByRole('button', { name: /Confirm Transfer/i }));
    expect(
      await screen.findByText(/Transfer failed due to a network error/i),
    ).toBeInTheDocument();

    // 2nd click without changing the form retries with the EXACT SAME idempotency key
    await user.click(screen.getByRole('button', { name: /Confirm Transfer/i }));
    expect(
      await screen.findByText(
        'Ana has R$ 1.850,00 and cannot pay R$ 5.000,00.',
      ),
    ).toBeInTheDocument();

    expect(transferSpy).toHaveBeenCalledTimes(2);
    const firstKey = transferSpy.mock.calls[0][2];
    const secondKey = transferSpy.mock.calls[1][2];
    expect(secondKey).toBe(firstKey);

    // Changing the amount rotates the idempotency key for the new intent
    await user.clear(screen.getByLabelText(/Amount \(R\$\)/i));
    await user.type(screen.getByLabelText(/Amount \(R\$\)/i), '100,00');
    await user.click(screen.getByRole('button', { name: /Confirm Transfer/i }));

    await waitFor(() => {
      expect(transferSpy).toHaveBeenCalledTimes(3);
    });
    const thirdKey = transferSpy.mock.calls[2][2];
    expect(thirdKey).not.toBe(firstKey);
  });
});
