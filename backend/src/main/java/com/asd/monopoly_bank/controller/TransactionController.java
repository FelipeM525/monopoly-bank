package com.asd.monopoly_bank.controller;

import com.asd.monopoly_bank.dto.CreateTransactionRequest;
import com.asd.monopoly_bank.dto.PagedResponse;
import com.asd.monopoly_bank.dto.TransactionResponse;
import com.asd.monopoly_bank.service.TransactionService;
import com.asd.monopoly_bank.service.TransactionService.TransferResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games/{gameId}/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Atomic, idempotent money transfers and filterable transaction history")
public class TransactionController {

	private final TransactionService transactionService;

	@PostMapping
	@Operation(
			summary = "Execute an atomic, idempotent money transfer (R3, R5)",
			description = "Moves centavos between a player and the bank or between two players. "
					+ "Requires a client-generated UUID in the Idempotency-Key header to prevent double-charging on double taps.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Transfer executed and recorded"),
		@ApiResponse(responseCode = "200", description = "Duplicate Idempotency-Key replayed without double-charging"),
		@ApiResponse(responseCode = "400", description = "Missing/invalid Idempotency-Key or invalid request fields"),
		@ApiResponse(responseCode = "404", description = "Game or account not found"),
		@ApiResponse(responseCode = "422", description = "Insufficient funds, self-transfer, cross-game account, or mismatched idempotency payload")
	})
	public ResponseEntity<TransactionResponse> createTransaction(
			@Parameter(description = "Game ID") @PathVariable Long gameId,
			@Parameter(description = "Client-generated UUID for double-tap protection", required = true)
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody CreateTransactionRequest request) {
		TransferResult result = transactionService.transfer(gameId, request, idempotencyKey);
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.transaction());
	}

	@GetMapping
	@Operation(
			summary = "List transaction history with optional player filter (R4)",
			description = "Returns paged transactions ordered newest-first. When accountId is supplied, returns only transactions where that account was payer or receiver.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Transaction history retrieved"),
		@ApiResponse(responseCode = "400", description = "Invalid pagination parameters"),
		@ApiResponse(responseCode = "404", description = "Game or filter account not found"),
		@ApiResponse(responseCode = "422", description = "Filter account belongs to a different game")
	})
	public ResponseEntity<PagedResponse<TransactionResponse>> listTransactions(
			@Parameter(description = "Game ID") @PathVariable Long gameId,
			@Parameter(description = "Optional account ID to filter by player (payer or receiver)")
			@RequestParam(required = false) Long accountId,
			@Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") int page,
			@Parameter(description = "Page size (1 to 100)") @RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(
				transactionService.listTransactions(gameId, accountId, page, size));
	}
}
