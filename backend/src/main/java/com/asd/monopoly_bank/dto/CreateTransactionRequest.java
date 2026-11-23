package com.asd.monopoly_bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateTransactionRequest(
		@NotNull(message = "Payer account (fromAccountId) is required.")
		Long fromAccountId,

		@NotNull(message = "Receiver account (toAccountId) is required.")
		Long toAccountId,

		@NotNull(message = "Transfer amount is required.")
		@Positive(message = "Transfer amount must be greater than zero.")
		Long amountCents,

		@NotBlank(message = "Transfer reason is required.")
		@Size(max = 120, message = "Transfer reason must be at most 120 characters.")
		String reason) {
}
