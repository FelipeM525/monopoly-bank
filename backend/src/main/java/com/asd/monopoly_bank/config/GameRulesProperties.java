package com.asd.monopoly_bank.config;

import com.asd.monopoly_bank.exception.InsufficientFundsException;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.AccountType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "game.rules")
@Validated
@Getter
@Setter
public class GameRulesProperties {

	/** Default player starting balance in centavos (e.g. R$ 1.500,00 = 150000). */
	@PositiveOrZero
	private long startingBalanceCents = 150_000L;

	/** Default salary amount paid when passing start, in centavos (e.g. R$ 200,00 = 20000). */
	@Positive
	private long salaryCents = 20_000L;

	/** Minimum number of players allowed in a game. */
	@Min(2)
	private int minPlayers = 2;

	/** Maximum number of players allowed in a game. */
	@Min(2)
	private int maxPlayers = 6;

	/** Quick-select transaction reasons shown at the table. Single source of truth. */
	@NotEmpty
	private List<@NotBlank String> reasons = List.of("Purchase", "Rent", "Tax", "Salary", "Other");

	@Valid
	@NotNull
	private BankPolicy bank = new BankPolicy();

	@Getter
	@Setter
	public static class BankPolicy {

		/** Initial cash held by the bank in centavos (e.g. R$ 20.000,00 = 2000000). */
		@PositiveOrZero
		private long initialCashCents = 2_000_000L;

		/**
		 * Whether the bank can keep paying even when its cash balance would drop below zero.
		 * When {@code false}, the bank is restricted by its {@code balanceCents} like any player.
		 */
		private boolean unlimited = false;
	}

	/**
	 * Enforces the sufficiency rule for an outgoing payment from {@code payer}.
	 * Players can never pay more than their balance; the bank is checked unless {@code bank.unlimited} is true.
	 */
	public void validateSufficientFunds(Account payer, long amountCents) {
		if (payer.getType() == AccountType.BANK && bank.isUnlimited()) {
			return;
		}
		if (payer.getBalanceCents() < amountCents) {
			throw new InsufficientFundsException(payer.getName(), payer.getBalanceCents(), amountCents);
		}
	}
}
