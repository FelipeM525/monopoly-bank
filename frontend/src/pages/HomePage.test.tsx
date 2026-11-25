import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import App from '@/App';
import { apiClient } from '@/api/client';
import { LAST_GAME_STORAGE_KEY } from '@/pages/NewGamePage';
import type { GameSummaryDto } from '@/types/api';

const MOCK_GAMES: GameSummaryDto[] = [
  {
    id: 20,
    name: 'Saturday Championship',
    createdAt: '2026-11-24T21:00:00Z',
    playerCount: 4,
    transactionCount: 18,
  },
  {
    id: 10,
    name: 'Friday Warmup',
    createdAt: '2026-11-23T20:00:00Z',
    playerCount: 3,
    transactionCount: 7,
  },
];

function renderHome() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('HomePage — game list, resume, and concurrent games (R6)', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('shows an empty state when no games exist yet', async () => {
    vi.spyOn(apiClient, 'listGames').mockResolvedValue([]);

    renderHome();

    const emptyCard = await screen.findByTestId('empty-games-state');
    expect(
      within(emptyCard).getByText(/No games started yet/i),
    ).toBeInTheDocument();
    expect(
      within(emptyCard).getByRole('link', { name: /Create First Game/i }),
    ).toHaveAttribute('href', '/games/new');
  });

  it('lists all concurrent games and highlights the last active game from localStorage for quick resume', async () => {
    localStorage.setItem(LAST_GAME_STORAGE_KEY, '10');
    vi.spyOn(apiClient, 'listGames').mockResolvedValue(MOCK_GAMES);

    renderHome();

    const continueLink = await screen.findByTestId('continue-last-game-link');
    expect(continueLink).toHaveTextContent('Continue Friday Warmup (#10)');
    expect(continueLink).toHaveAttribute('href', '/games/10');

    const card20 = screen.getByTestId('game-summary-card-20');
    expect(within(card20).getByText('Saturday Championship')).toBeInTheDocument();
    expect(within(card20).getByText(/4 Players/i)).toBeInTheDocument();
    expect(within(card20).getByText(/18 Transactions/i)).toBeInTheDocument();

    const card10 = screen.getByTestId('game-summary-card-10');
    expect(within(card10).getByText('Friday Warmup')).toBeInTheDocument();
    expect(within(card10).getByText('Recent')).toBeInTheDocument();
  });
});
