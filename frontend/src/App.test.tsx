import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import App from '@/App';
import { apiClient } from '@/api/client';

function renderAtRoute(route: string) {
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

describe('App routing & shell', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the home route at /', () => {
    vi.spyOn(apiClient, 'listGames').mockResolvedValue([]);
    renderAtRoute('/');
    expect(
      screen.getByRole('heading', { name: /The Bank of Banco Imobiliário/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: /Start a New Game/i }),
    ).toHaveAttribute('href', '/games/new');
  });

  it('renders the new game setup route at /games/new', () => {
    vi.spyOn(apiClient, 'getConfig').mockResolvedValue({
      startingBalanceCents: 150_000,
      salaryCents: 20_000,
      minPlayers: 2,
      maxPlayers: 6,
      bank: { initialCashCents: 2_000_000, unlimited: false },
      colors: ['RED', 'BLUE', 'GREEN', 'YELLOW', 'PURPLE', 'ORANGE'],
      reasons: ['Purchase', 'Rent', 'Tax', 'Salary', 'Other'],
    });
    renderAtRoute('/games/new');
    expect(
      screen.getByRole('heading', { name: /New Game Setup/i }),
    ).toBeInTheDocument();
  });

  it('renders the game dashboard route at /games/:id', async () => {
    vi.spyOn(apiClient, 'getGame').mockResolvedValue({
      id: 42,
      name: 'Table 42',
      createdAt: '2026-11-24T00:00:00Z',
      bank: {
        id: 1,
        type: 'BANK',
        name: 'Bank',
        color: null,
        initialBalanceCents: 2_000_000,
        balanceCents: 2_000_000,
      },
      players: [
        {
          id: 2,
          type: 'PLAYER',
          name: 'Ana',
          color: 'RED',
          initialBalanceCents: 150_000,
          balanceCents: 150_000,
        },
        {
          id: 3,
          type: 'PLAYER',
          name: 'Bruno',
          color: 'BLUE',
          initialBalanceCents: 150_000,
          balanceCents: 150_000,
        },
      ],
      totalMoneyCents: 2_300_000,
      transactionCount: 0,
    });

    vi.spyOn(apiClient, 'listTransactions').mockResolvedValue({
      items: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    });

    renderAtRoute('/games/42');
    expect(
      await screen.findByRole('heading', { name: /Table 42/i }),
    ).toBeInTheDocument();
  });
});
