import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiClient } from './client';

describe('apiClient', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches game configuration from /api/config', async () => {
    const mockConfig = {
      startingBalanceCents: 150_000,
      salaryCents: 20_000,
      minPlayers: 2,
      maxPlayers: 6,
      bank: { initialCashCents: 2_000_000, unlimited: false },
      colors: ['RED', 'BLUE'],
      reasons: ['Purchase', 'Rent'],
    };

    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(mockConfig), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    const result = await apiClient.getConfig();
    expect(result).toEqual(mockConfig);
    expect(fetchSpy).toHaveBeenCalledWith(
      '/api/config',
      expect.objectContaining({ headers: expect.any(Headers) }),
    );
  });

  it('sends Idempotency-Key header on createTransaction', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ id: 10, amountCents: 5000 }), {
        status: 201,
        headers: { 'Content-Type': 'application/json' },
      }),
    );

    await apiClient.createTransaction(
      1,
      { fromAccountId: 2, toAccountId: 3, amountCents: 5000, reason: 'Rent' },
      '123e4567-e89b-12d3-a456-426614174000',
    );

    const [, init] = fetchSpy.mock.calls[0];
    const headers = init?.headers as Headers;
    expect(headers.get('Idempotency-Key')).toBe(
      '123e4567-e89b-12d3-a456-426614174000',
    );
    expect(headers.get('Content-Type')).toBe('application/json');
  });

  it('parses RFC 7807 ProblemDetail responses into ApiError with fieldErrors', async () => {
    const problem = {
      status: 422,
      title: 'Insufficient Funds',
      detail: 'Ana has R$ 120,00 and cannot pay R$ 200,00.',
      payerName: 'Ana',
      availableCents: 12_000,
      requestedCents: 20_000,
      errors: { amountCents: 'Exceeds balance' },
    };

    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify(problem), {
        status: 422,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    );

    await expect(apiClient.getGame(99)).rejects.toSatisfy((err: unknown) => {
      expect(err).toBeInstanceOf(ApiError);
      const apiErr = err as ApiError;
      expect(apiErr.status).toBe(422);
      expect(apiErr.title).toBe('Insufficient Funds');
      expect(apiErr.detail).toBe('Ana has R$ 120,00 and cannot pay R$ 200,00.');
      expect(apiErr.fieldErrors).toEqual({ amountCents: 'Exceeds balance' });
      expect(apiErr.payload.availableCents).toBe(12_000);
      return true;
    });
  });
});
