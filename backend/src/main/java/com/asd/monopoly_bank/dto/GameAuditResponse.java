package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.AccountType;
import java.util.List;

public record GameAuditResponse(
		Long gameId,
		boolean consistent,
		long initialTotalMoneyCents,
		long currentTotalMoneyCents,
		long rebuiltTotalMoneyCents,
		long transactionCount,
		List<AccountAuditEntry> accounts) {

	public record AccountAuditEntry(
			Long accountId,
			AccountType type,
			String name,
			long initialBalanceCents,
			long totalCreditsCents,
			long totalDebitsCents,
			long rebuiltBalanceCents,
			long storedBalanceCents,
			boolean consistent) {

		public static AccountAuditEntry from(Account account, long totalCreditsCents, long totalDebitsCents) {
			long rebuilt = Math.subtractExact(
					Math.addExact(account.getInitialBalanceCents(), totalCreditsCents),
					totalDebitsCents);
			long stored = account.getBalanceCents();
			return new AccountAuditEntry(
					account.getId(),
					account.getType(),
					account.getName(),
					account.getInitialBalanceCents(),
					totalCreditsCents,
					totalDebitsCents,
					rebuilt,
					stored,
					rebuilt == stored);
		}
	}
}
