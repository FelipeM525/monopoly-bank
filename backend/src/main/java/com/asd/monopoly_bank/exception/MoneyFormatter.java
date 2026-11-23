package com.asd.monopoly_bank.exception;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyFormatter {

	private static final Locale PT_BR = Locale.of("pt", "BR");

	private MoneyFormatter() {
	}

	/**
	 * Formats an integer amount in centavos as Brazilian Reais, e.g. {@code 12000L -> "R$ 120,00"}.
	 * Uses integer division and remainder first, or exact BigDecimal scaling, never floating-point.
	 */
	public static String formatCents(long cents) {
		boolean negative = cents < 0;
		long abs = Math.abs(cents);
		long reais = abs / 100;
		long centavos = abs % 100;

		NumberFormat integerFormat = NumberFormat.getIntegerInstance(PT_BR);
		String formattedReais = integerFormat.format(reais);
		String prefix = negative ? "-R$ " : "R$ ";
		return String.format(Locale.ROOT, "%s%s,%02d", prefix, formattedReais, centavos);
	}
}
