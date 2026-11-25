package com.asd.monopoly_bank.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.asd.monopoly_bank.dto.TransactionExportRow;
import com.asd.monopoly_bank.model.AccountType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionCsvWriterTest {

	private static final UUID KEY = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final Instant TIME = Instant.parse("2026-11-25T15:00:00Z");

	@Test
	void writesBomHeaderAndOneCrlfTerminatedLinePerTransaction() {
		TransactionExportRow row = row("Ana", "Bank", 20_000L, "Rent", null);

		String csv = TransactionCsvWriter.write(List.of(row));

		assertThat(csv).startsWith("\uFEFF").endsWith("\r\n");
		assertThat(csv.substring(1).split("\r\n")).containsExactly(
				"id,createdAt,payer,payerType,receiver,receiverType,amountCents,reason,reversalOfTransactionId,idempotencyKey",
				"7,2026-11-25T15:00:00Z,Ana,PLAYER,Bank,PLAYER,20000,Rent,," + KEY);
	}

	@Test
	void writesOnlyTheHeaderWhenThereAreNoTransactions() {
		String csv = TransactionCsvWriter.write(List.of());

		assertThat(csv.substring(1).split("\r\n")).hasSize(1);
	}

	@Test
	void quotesCellsWithCommasQuotesAndNewlines() {
		TransactionExportRow row = row("Silva, Ana", "say \"hi\"", 100L, "line1\nline2", null);

		String csv = TransactionCsvWriter.write(List.of(row));

		assertThat(csv).contains("\"Silva, Ana\"", "\"say \"\"hi\"\"\"", "\"line1\nline2\"");
	}

	@Test
	void neutralizesSpreadsheetFormulasTypedByUsers() {
		TransactionExportRow row = row("=SUM(A1)", "+cmd", 100L, "@evil", 3L);

		String csv = TransactionCsvWriter.write(List.of(row));

		assertThat(csv).contains("'=SUM(A1)", "'+cmd", "'@evil");
		assertThat(csv).contains(",3," + KEY);
	}

	private static TransactionExportRow row(
			String payer, String receiver, long amountCents, String reason, Long reversalOf) {
		return new TransactionExportRow(
				7L, TIME, payer, AccountType.PLAYER, receiver, AccountType.PLAYER,
				amountCents, reason, reversalOf, KEY);
	}
}
