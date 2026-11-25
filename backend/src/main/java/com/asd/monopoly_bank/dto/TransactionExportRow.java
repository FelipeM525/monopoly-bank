package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.model.AccountType;
import com.asd.monopoly_bank.model.Transaction;
import java.time.Instant;
import java.util.UUID;

/**
 * Flat, self-describing ledger row used by both the CSV and JSON exports.
 * Amounts stay in integer centavos so the file can be re-added without rounding.
 */
public record TransactionExportRow(
		Long id,
		Instant createdAt,
		String payer,
		AccountType payerType,
		String receiver,
		AccountType receiverType,
		long amountCents,
		String reason,
		Long reversalOfTransactionId,
		UUID idempotencyKey) {

	public static TransactionExportRow from(Transaction tx) {
		return new TransactionExportRow(
				tx.getId(),
				tx.getCreatedAt(),
				tx.getFromAccount().getName(),
				tx.getFromAccount().getType(),
				tx.getToAccount().getName(),
				tx.getToAccount().getType(),
				tx.getAmountCents(),
				tx.getReason(),
				tx.getReversalOf() != null ? tx.getReversalOf().getId() : null,
				tx.getIdempotencyKey());
	}
}
