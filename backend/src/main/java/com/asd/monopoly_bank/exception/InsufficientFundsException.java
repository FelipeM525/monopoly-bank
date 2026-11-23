package com.asd.monopoly_bank.exception;

import lombok.Getter;

@Getter
public class InsufficientFundsException extends RuntimeException {

	private final String payerName;
	private final long availableCents;
	private final long requestedCents;

	public InsufficientFundsException(String payerName, long availableCents, long requestedCents) {
		super(String.format(
				"%s has %s and cannot pay %s.",
				payerName,
				MoneyFormatter.formatCents(availableCents),
				MoneyFormatter.formatCents(requestedCents)));
		this.payerName = payerName;
		this.availableCents = availableCents;
		this.requestedCents = requestedCents;
	}
}
