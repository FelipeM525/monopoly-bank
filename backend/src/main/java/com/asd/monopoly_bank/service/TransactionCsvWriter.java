package com.asd.monopoly_bank.service;

import com.asd.monopoly_bank.dto.TransactionExportRow;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes the ledger as RFC 4180 CSV.
 *
 * <p>Player names and reasons are typed by users, so text cells that start with a spreadsheet
 * formula character are prefixed with a single quote to stop Excel/Sheets from running them.
 * A UTF-8 byte order mark is added so Excel shows accented names correctly.
 */
final class TransactionCsvWriter {

	private static final String BOM = "\uFEFF";
	private static final String EOL = "\r\n";
	private static final String FORMULA_PREFIXES = "=+-@\t\r";
	private static final List<String> HEADER = List.of(
			"id", "createdAt", "payer", "payerType", "receiver", "receiverType",
			"amountCents", "reason", "reversalOfTransactionId", "idempotencyKey");

	private TransactionCsvWriter() {
	}

	static String write(List<TransactionExportRow> rows) {
		return Stream.concat(Stream.of(HEADER), rows.stream().map(TransactionCsvWriter::toCells))
				.map(cells -> String.join(",", cells))
				.collect(Collectors.joining(EOL, BOM, EOL));
	}

	private static List<String> toCells(TransactionExportRow row) {
		return List.of(
				String.valueOf(row.id()),
				row.createdAt().toString(),
				text(row.payer()),
				row.payerType().name(),
				text(row.receiver()),
				row.receiverType().name(),
				String.valueOf(row.amountCents()),
				text(row.reason()),
				row.reversalOfTransactionId() == null ? "" : String.valueOf(row.reversalOfTransactionId()),
				row.idempotencyKey().toString());
	}

	private static String text(String value) {
		return quoteIfNeeded(neutralizeFormula(value));
	}

	private static String neutralizeFormula(String value) {
		boolean startsLikeFormula = !value.isEmpty() && FORMULA_PREFIXES.indexOf(value.charAt(0)) >= 0;
		return startsLikeFormula ? "'" + value : value;
	}

	private static String quoteIfNeeded(String value) {
		boolean needsQuotes = value.contains(",") || value.contains("\"")
				|| value.contains("\n") || value.contains("\r");
		return needsQuotes ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
	}
}
