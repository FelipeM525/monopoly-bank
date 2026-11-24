import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, expect, it } from 'vitest';
import App from './App';

function renderAtRoute(route: string) {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[route]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('App routing & shell', () => {
  it('renders the home route at /', () => {
    renderAtRoute('/');
    expect(
      screen.getByRole('heading', { name: /The Bank of Banco Imobiliário/i }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: /Start a New Game/i }),
    ).toHaveAttribute('href', '/games/new');
  });

  it('renders the new game setup route at /games/new', () => {
    renderAtRoute('/games/new');
    expect(
      screen.getByRole('heading', { name: /New Game Setup/i }),
    ).toBeInTheDocument();
  });

  it('renders the game dashboard route at /games/:id', () => {
    renderAtRoute('/games/42');
    expect(
      screen.getByRole('heading', { name: /Game #42 Dashboard/i }),
    ).toBeInTheDocument();
  });
});
