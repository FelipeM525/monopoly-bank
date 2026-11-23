package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.AccountType;
import com.asd.monopoly_bank.model.PawnColor;
import com.asd.monopoly_bank.model.Transaction;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
		Long id,
		Long gameId,
		AccountParticipant fromAccount,
		AccountParticipant toAccount,
		long amountCents,
		String reason,
		UUID idempotencyKey,
		Long reversalOfTransactionId,
		Instant createdAt) {

	public record AccountParticipant(
			Long id,
			AccountType type,
			String name,
			PawnColor color,
			long balanceCents) {

		public static AccountParticipant from(Account account) {
			return new AccountParticipant(
					account.getId(),
					account.getType(),
					account.getName(),
					account.getColor(),
					account.getBalanceCents());
		}
	}

	public static TransactionResponse from(Transaction tx) {
		return new TransactionResponse(
				tx.getId(),
				tx.getGame().getId(),
				AccountParticipant.from(tx.getFromAccount()),
				AccountParticipant.from(tx.getToAccount()),
				tx.getAmountCents(),
				tx.getReason(),
				tx.getIdempotencyKey(),
				tx.getReversalOf() != null ? tx.getReversalOf().getId() : null,
				tx.getCreatedAt());
	}
}
