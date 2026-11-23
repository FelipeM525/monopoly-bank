package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.AccountType;
import com.asd.monopoly_bank.model.PawnColor;

public record AccountResponse(
		Long id,
		AccountType type,
		String name,
		PawnColor color,
		long initialBalanceCents,
		long balanceCents) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(
				account.getId(),
				account.getType(),
				account.getName(),
				account.getColor(),
				account.getInitialBalanceCents(),
				account.getBalanceCents());
	}
}
