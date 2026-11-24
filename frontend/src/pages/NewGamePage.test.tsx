import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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

function renderNewGameScreen() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/games/new']}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('NewGamePage (R1)', () => {
  beforeEach(() => {
    vi.spyOn(apiClient, 'getConfig').mockResolvedValue(MOCK_CONFIG);
    vi.spyOn(apiClient, 'listTransactions').mockResolvedValue({
      items: [],
      page: 0,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    });
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('prefills starting balances from /api/config, disables taken pawn colors, and adds/removes player rows', async () => {
    const user = userEvent.setup();
    renderNewGameScreen();

    const p1Balance = await screen.findByLabelText(/Starting Balance/i, {
      selector: '#player-1-balance',
    });
    expect(p1Balance).toHaveValue('1500,00');

    // Player 1 has RED selected; Player 2 should see RED disabled as taken
    const row1 = screen.getByTestId('player-row-0');
    const row2 = screen.getByTestId('player-row-1');
    expect(within(row1).getByRole('radio', { name: 'Red pawn' })).toHaveAttribute(
      'aria-checked',
      'true',
    );
    expect(within(row2).getByRole('radio', { name: 'Red pawn' })).toBeDisabled();

    // Add a 3rd player -> picks GREEN (next available color) and unlocks Remove buttons
    await user.click(screen.getByRole('button', { name: /Add Player/i }));
    const row3 = screen.getByTestId('player-row-2');
    expect(
      within(row3).getByRole('radio', { name: 'Green pawn' }),
    ).toHaveAttribute('aria-checked', 'true');

    // Remove the 3rd player -> back to 2 players
    await user.click(
      screen.getByRole('button', { name: /Remove Player 3/i }),
    );
    expect(screen.queryByTestId('player-row-2')).not.toBeInTheDocument();
  });

  it('validates blank or duplicate player names on the client before calling the API', async () => {
    const user = userEvent.setup();
    const createSpy = vi.spyOn(apiClient, 'createGame');
    renderNewGameScreen();

    await user.click(screen.getByRole('button', { name: /Start Game/i }));
    expect(
      await screen.findAllByText(/Player name is required\./i),
    ).toHaveLength(2);
    expect(createSpy).not.toHaveBeenCalled();

    // Enter duplicate names (case-insensitive)
    await user.type(screen.getByLabelText(/Player 1 Name/i), 'Ana');
    await user.type(screen.getByLabelText(/Player 2 Name/i), 'ana');
    await user.click(screen.getByRole('button', { name: /Start Game/i }));

    expect(
      await screen.findByText(/Duplicate player name 'ana'/i),
    ).toBeInTheDocument();
    expect(createSpy).not.toHaveBeenCalled();
  });

  it('submits valid players in centavos and navigates to the game dashboard on success', async () => {
    const user = userEvent.setup();
    const createdGame: GameDto = {
      id: 77,
      name: 'Friday Night',
      createdAt: '2026-11-23T23:00:00Z',
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
          initialBalanceCents: 200_000,
          balanceCents: 200_000,
        },
      ],
      totalMoneyCents: 2_350_000,
      transactionCount: 0,
    };

    const createSpy = vi
      .spyOn(apiClient, 'createGame')
      .mockResolvedValue(createdGame);
    vi.spyOn(apiClient, 'getGame').mockResolvedValue(createdGame);

    renderNewGameScreen();

    await user.type(
      screen.getByLabelText(/Table \/ Session Name/i),
      'Friday Night',
    );
    await user.type(screen.getByLabelText(/Player 1 Name/i), 'Ana');
    await user.type(screen.getByLabelText(/Player 2 Name/i), 'Bruno');

    const p2Balance = screen.getByLabelText(/Starting Balance/i, {
      selector: '#player-2-balance',
    });
    await user.clear(p2Balance);
    await user.type(p2Balance, '2000,00');

    await user.click(screen.getByRole('button', { name: /Start Game/i }));

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledWith({
        name: 'Friday Night',
        players: [
          { name: 'Ana', color: 'RED', startingBalanceCents: 150_000 },
          { name: 'Bruno', color: 'BLUE', startingBalanceCents: 200_000 },
        ],
      });
    });

    expect(
      await screen.findByRole('heading', { name: 'Friday Night' }),
    ).toBeInTheDocument();
  });

  it('displays server ProblemDetail errors and maps field errors to player rows', async () => {
    const user = userEvent.setup();
    vi.spyOn(apiClient, 'createGame').mockRejectedValue(
      new ApiError(400, {
        status: 400,
        title: 'Validation Failed',
        detail: 'One or more fields are invalid.',
        errors: {
          'players[1].startingBalanceCents':
            'Starting balance cannot be negative.',
        },
      }),
    );

    renderNewGameScreen();

    await user.type(screen.getByLabelText(/Player 1 Name/i), 'Ana');
    await user.type(screen.getByLabelText(/Player 2 Name/i), 'Bruno');
    await user.click(screen.getByRole('button', { name: /Start Game/i }));

    expect(
      await screen.findByText(/One or more fields are invalid\./i),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Starting balance cannot be negative\./i),
    ).toBeInTheDocument();
  });
});
