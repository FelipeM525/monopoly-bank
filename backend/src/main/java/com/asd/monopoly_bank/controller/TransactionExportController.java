package com.asd.monopoly_bank.controller;

import com.asd.monopoly_bank.dto.ExportFormat;
import com.asd.monopoly_bank.dto.TransactionExportRow;
import com.asd.monopoly_bank.service.TransactionExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/transactions/export")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Atomic, idempotent money transfers and filterable transaction history")
public class TransactionExportController {

	private final TransactionExportService exportService;

	@GetMapping
	@Operation(
			summary = "Export the full transaction history as a CSV or JSON file",
			description = "Downloads every transaction of the game, oldest first, with amounts in integer centavos. "
					+ "Useful when players argue about a payment.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "File generated (Content-Disposition: attachment)"),
		@ApiResponse(responseCode = "400", description = "Unsupported format"),
		@ApiResponse(responseCode = "404", description = "Game not found")
	})
	public ResponseEntity<?> exportTransactions(
			@Parameter(description = "Game ID") @PathVariable Long gameId,
			@Parameter(description = "File format: csv or json") @RequestParam(defaultValue = "csv") String format) {
		ExportFormat exportFormat = ExportFormat.fromParam(format);
		List<TransactionExportRow> rows = exportService.loadRows(gameId);

		return switch (exportFormat) {
			case CSV -> attachment(exportFormat, gameId).body(exportService.toCsv(rows));
			case JSON -> attachment(exportFormat, gameId).body(rows);
		};
	}

	private ResponseEntity.BodyBuilder attachment(ExportFormat format, Long gameId) {
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(format.filenameFor(gameId))
				.build();
		return ResponseEntity.ok()
				.contentType(format.mediaType())
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
	}
}
