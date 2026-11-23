package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.model.Game;
import java.time.Instant;
import java.util.List;

public record GameResponse(
		Long id,
		String name,
		Instant createdAt,
		AccountResponse bank,
		List<AccountResponse> players,
		long totalMoneyCents,
		long transactionCount) {

	public static GameResponse from(
			Game game,
			AccountResponse bank,
			List<AccountResponse> players,
			long transactionCount) {
		long playersTotal = players.stream().mapToLong(AccountResponse::balanceCents).sum();
		long totalMoneyCents = bank.balanceCents() + playersTotal;
		return new GameResponse(
				game.getId(),
				game.getName(),
				game.getCreatedAt(),
				bank,
				players,
				totalMoneyCents,
				transactionCount);
	}
}
