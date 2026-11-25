import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiClient } from '@/api/client';
import { ExportHistoryButtons } from '@/components/ExportHistoryButtons';
import { saveBlob } from '@/utils/download';

vi.mock('@/utils/download', () => ({ saveBlob: vi.fn() }));

function renderButtons() {
  const queryClient = new QueryClient({
    defaultOptions: { mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <ExportHistoryButtons gameId={12} />
    </QueryClientProvider>,
  );
}

describe('ExportHistoryButtons', () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.mocked(saveBlob).mockClear();
  });

  it('downloads the CSV and JSON files for the whole game', async () => {
    const user = userEvent.setup();
    const blob = new Blob(['id\n1']);
    const downloadSpy = vi
      .spyOn(apiClient, 'downloadTransactionsExport')
      .mockResolvedValue({ blob, filename: 'game-12-transactions.csv' });

    renderButtons();

    await user.click(screen.getByRole('button', { name: /Export CSV/i }));

    await waitFor(() => {
      expect(saveBlob).toHaveBeenCalledWith(blob, 'game-12-transactions.csv');
    });
    expect(downloadSpy).toHaveBeenCalledWith(12, 'csv');

    await user.click(screen.getByRole('button', { name: /Export JSON/i }));
    await waitFor(() => {
      expect(downloadSpy).toHaveBeenCalledWith(12, 'json');
    });
  });

  it('shows the server error message when the export fails', async () => {
    const user = userEvent.setup();
    vi.spyOn(apiClient, 'downloadTransactionsExport').mockRejectedValue(
      new ApiError(404, {
        status: 404,
        title: 'Resource Not Found',
        detail: 'Game #12 was not found.',
      }),
    );

    renderButtons();
    await user.click(screen.getByRole('button', { name: /Export CSV/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Game #12 was not found.',
    );
    expect(saveBlob).not.toHaveBeenCalled();
  });
});
