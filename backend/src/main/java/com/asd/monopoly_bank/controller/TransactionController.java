package com.asd.monopoly_bank.controller;

import com.asd.monopoly_bank.dto.CreateTransactionRequest;
import com.asd.monopoly_bank.dto.PagedResponse;
import com.asd.monopoly_bank.dto.TransactionResponse;
import com.asd.monopoly_bank.service.TransactionService;
import com.asd.monopoly_bank.service.TransactionService.TransferResult;
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
public class TransactionController {

	private final TransactionService transactionService;

	@PostMapping
	public ResponseEntity<TransactionResponse> createTransaction(
			@PathVariable Long gameId,
			@RequestHeader("Idempotency-Key") UUID idempotencyKey,
			@Valid @RequestBody CreateTransactionRequest request) {
		TransferResult result = transactionService.transfer(gameId, request, idempotencyKey);
		HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(result.transaction());
	}

	@GetMapping
	public ResponseEntity<PagedResponse<TransactionResponse>> listTransactions(
			@PathVariable Long gameId,
			@RequestParam(required = false) Long accountId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok(
				transactionService.listTransactions(gameId, accountId, page, size));
	}
}
