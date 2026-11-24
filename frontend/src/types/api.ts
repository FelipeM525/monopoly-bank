export type AccountType = 'BANK' | 'PLAYER';

export type PawnColor =
  | 'RED'
  | 'BLUE'
  | 'GREEN'
  | 'YELLOW'
  | 'PURPLE'
  | 'ORANGE';

export interface BankPolicy {
  initialCashCents: number;
  unlimited: boolean;
}

export interface GameConfig {
  startingBalanceCents: number;
  salaryCents: number;
  minPlayers: number;
  maxPlayers: number;
  bank: BankPolicy;
  colors: PawnColor[];
  reasons: string[];
}

export interface PlayerSetupInput {
  name: string;
  color: PawnColor;
  startingBalanceCents?: number;
}

export interface CreateGameRequest {
  name?: string;
  players: PlayerSetupInput[];
}

export interface AccountDto {
  id: number;
  type: AccountType;
  name: string;
  color: PawnColor | null;
  initialBalanceCents: number;
  balanceCents: number;
}

export interface GameDto {
  id: number;
  name: string;
  createdAt: string;
  bank: AccountDto;
  players: AccountDto[];
  totalMoneyCents: number;
  transactionCount: number;
}

export interface GameSummaryDto {
  id: number;
  name: string;
  createdAt: string;
  playerCount: number;
  transactionCount: number;
}

export interface CreateTransactionRequest {
  fromAccountId: number;
  toAccountId: number;
  amountCents: number;
  reason: string;
}

export interface AccountParticipantDto {
  id: number;
  type: AccountType;
  name: string;
  color: PawnColor | null;
  balanceCents: number;
}

export interface TransactionDto {
  id: number;
  gameId: number;
  fromAccount: AccountParticipantDto;
  toAccount: AccountParticipantDto;
  amountCents: number;
  reason: string;
  idempotencyKey: string;
  reversalOfTransactionId: number | null;
  reversed: boolean;
  createdAt: string;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface AccountAuditEntryDto {
  accountId: number;
  type: AccountType;
  name: string;
  initialBalanceCents: number;
  totalCreditsCents: number;
  totalDebitsCents: number;
  rebuiltBalanceCents: number;
  storedBalanceCents: number;
  consistent: boolean;
}

export interface GameAuditDto {
  gameId: number;
  consistent: boolean;
  initialTotalMoneyCents: number;
  currentTotalMoneyCents: number;
  rebuiltTotalMoneyCents: number;
  transactionCount: number;
  accounts: AccountAuditEntryDto[];
}

export interface ProblemDetailPayload {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  timestamp?: string;
  errors?: Record<string, string>;
  payerName?: string;
  availableCents?: number;
  requestedCents?: number;
}
