package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.model.Game;
import java.time.Instant;

public record GameSummaryResponse(
		Long id,
		String name,
		Instant createdAt,
		int playerCount,
		long transactionCount) {

	public static GameSummaryResponse from(Game game, int playerCount, long transactionCount) {
		return new GameSummaryResponse(
				game.getId(),
				game.getName(),
				game.getCreatedAt(),
				playerCount,
				transactionCount);
	}
}
