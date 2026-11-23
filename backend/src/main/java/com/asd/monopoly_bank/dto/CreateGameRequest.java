package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.model.PawnColor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGameRequest(
		@Size(max = 100, message = "Game name must be at most 100 characters.")
		String name,

		@NotEmpty(message = "At least two players are required to start a game.")
		List<@Valid @NotNull(message = "Player entry cannot be null.") PlayerSetupRequest> players) {

	public record PlayerSetupRequest(
			@NotBlank(message = "Player name is required.")
			@Size(max = 60, message = "Player name must be at most 60 characters.")
			String name,

			@NotNull(message = "Pawn color is required.")
			PawnColor color,

			@PositiveOrZero(message = "Starting balance cannot be negative.")
			Long startingBalanceCents) {
	}
}
