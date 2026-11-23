package com.asd.monopoly_bank.exception;

public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String message) {
		super(message);
	}

	public static ResourceNotFoundException game(Long gameId) {
		return new ResourceNotFoundException("Game #" + gameId + " was not found.");
	}

	public static ResourceNotFoundException account(Long accountId) {
		return new ResourceNotFoundException("Account #" + accountId + " was not found.");
	}

	public static ResourceNotFoundException transaction(Long transactionId) {
		return new ResourceNotFoundException("Transaction #" + transactionId + " was not found.");
	}
}
