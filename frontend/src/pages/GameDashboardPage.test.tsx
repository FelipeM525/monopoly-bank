import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '@/App';
import { ApiError, apiClient } from '@/api/client';
import type { GameConfig, GameDto } from '@/types/api';

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
});
