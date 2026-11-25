import type {
  CreateGameRequest,
  CreateTransactionRequest,
  GameAuditDto,
  GameConfig,
  GameDto,
  GameSummaryDto,
  PagedResponse,
  ProblemDetailPayload,
  TransactionDto,
} from '../types/api';

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly fieldErrors: Record<string, string>;
  readonly payload: ProblemDetailPayload;

  constructor(status: number, payload: ProblemDetailPayload) {
    const title = payload.title ?? 'Request Failed';
    const detail = payload.detail ?? `HTTP error ${status}`;
    super(detail);
    this.name = 'ApiError';
    this.status = status;
    this.title = title;
    this.detail = detail;
    this.fieldErrors = payload.errors ?? {};
    this.payload = payload;
  }
}

async function requestJson<T>(
  path: string,
  init?: RequestInit,
): Promise<T> {
  const headers = new Headers(init?.headers);
  if (init?.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  headers.set('Accept', 'application/json, application/problem+json');

  const response = await fetch(path, { ...init, headers });
  if (!response.ok) {
    throw await parseApiError(response);
  }

  return (await response.json()) as T;
}

async function parseApiError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as ProblemDetailPayload;
    return new ApiError(response.status, body);
  } catch {
    return new ApiError(response.status, {
      status: response.status,
      title: response.statusText || 'Error',
      detail: `Request failed with status ${response.status}.`,
    });
  }
}

export type ExportFormat = 'csv' | 'json';

export const apiClient = {
  getConfig(): Promise<GameConfig> {
    return requestJson<GameConfig>('/api/config');
  },

  listGames(): Promise<GameSummaryDto[]> {
    return requestJson<GameSummaryDto[]>('/api/games');
  },

  createGame(request: CreateGameRequest): Promise<GameDto> {
    return requestJson<GameDto>('/api/games', {
      method: 'POST',
      body: JSON.stringify(request),
    });
  },

  getGame(gameId: number): Promise<GameDto> {
    return requestJson<GameDto>(`/api/games/${gameId}`);
  },

  auditGame(gameId: number): Promise<GameAuditDto> {
    return requestJson<GameAuditDto>(`/api/games/${gameId}/audit`);
  },

  createTransaction(
    gameId: number,
    request: CreateTransactionRequest,
    idempotencyKey: string,
  ): Promise<TransactionDto> {
    return requestJson<TransactionDto>(`/api/games/${gameId}/transactions`, {
      method: 'POST',
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(request),
    });
  },

  listTransactions(
    gameId: number,
    params?: { accountId?: number | null; page?: number; size?: number },
  ): Promise<PagedResponse<TransactionDto>> {
    const query = new URLSearchParams();
    if (params?.accountId != null) {
      query.set('accountId', String(params.accountId));
    }
    if (params?.page != null) {
      query.set('page', String(params.page));
    }
    if (params?.size != null) {
      query.set('size', String(params.size));
    }
    const queryString = query.toString();
    const url = `/api/games/${gameId}/transactions${queryString ? `?${queryString}` : ''}`;
    return requestJson<PagedResponse<TransactionDto>>(url);
  },

  async downloadTransactionsExport(
    gameId: number,
    format: ExportFormat,
  ): Promise<{ blob: Blob; filename: string }> {
    const response = await fetch(
      `/api/games/${gameId}/transactions/export?format=${format}`,
    );
    if (!response.ok) {
      throw await parseApiError(response);
    }
    return {
      blob: await response.blob(),
      filename: `game-${gameId}-transactions.${format}`,
    };
  },
};
