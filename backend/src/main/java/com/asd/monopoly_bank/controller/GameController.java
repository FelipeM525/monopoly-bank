package com.asd.monopoly_bank.controller;

import com.asd.monopoly_bank.dto.CreateGameRequest;
import com.asd.monopoly_bank.dto.GameAuditResponse;
import com.asd.monopoly_bank.dto.GameResponse;
import com.asd.monopoly_bank.dto.GameSummaryResponse;
import com.asd.monopoly_bank.service.GameService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
@Tag(name = "Games", description = "Create games with 2-6 players, inspect balances dashboard, and audit ledger consistency")
public class GameController {

	private final GameService gameService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(
			summary = "Create a new game with 2 to 6 players (R1)",
			description = "Creates a game, its BANK account, and 2-6 PLAYER accounts with unique names and unique pawn colors in a single transaction.")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Game created"),
		@ApiResponse(responseCode = "400", description = "Invalid player count or field validation failure"),
		@ApiResponse(responseCode = "409", description = "Duplicate player name or duplicate pawn color")
	})
	public ResponseEntity<GameResponse> createGame(@Valid @RequestBody CreateGameRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(gameService.createGame(request));
	}

	@GetMapping
	@Operation(
			summary = "List all games newest-first (R6)",
			description = "Returns summary metadata for all games so players can resume an existing session or switch between concurrent games.")
	@ApiResponse(responseCode = "200", description = "Games listed")
	public ResponseEntity<List<GameSummaryResponse>> listGames() {
		return ResponseEntity.ok(gameService.listGames());
	}

	@GetMapping("/{id}")
	@Operation(
			summary = "Get game balances dashboard (R2)",
			description = "Returns the bank balance, every player's balance and pawn color, total money in circulation, and transaction count.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Game dashboard retrieved"),
		@ApiResponse(responseCode = "404", description = "Game not found")
	})
	public ResponseEntity<GameResponse> getGame(
			@Parameter(description = "Game ID") @PathVariable Long id) {
		return ResponseEntity.ok(gameService.getGame(id));
	}

	@GetMapping("/{id}/audit")
	@Operation(
			summary = "Audit game balances against transaction history (R5)",
			description = "Rebuilds every account's balance from initialBalanceCents + credits - debits and verifies total money conservation.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Audit completed"),
		@ApiResponse(responseCode = "404", description = "Game not found")
	})
	public ResponseEntity<GameAuditResponse> auditGame(
			@Parameter(description = "Game ID") @PathVariable Long id) {
		return ResponseEntity.ok(gameService.auditGame(id));
	}
}
