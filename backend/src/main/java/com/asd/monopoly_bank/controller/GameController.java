package com.asd.monopoly_bank.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.asd.monopoly_bank.dto.CreateGameRequest;
import com.asd.monopoly_bank.dto.GameAuditResponse;
import com.asd.monopoly_bank.dto.GameResponse;
import com.asd.monopoly_bank.dto.GameSummaryResponse;
import com.asd.monopoly_bank.service.GameService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameController {

	private final GameService gameService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<GameResponse> createGame(@Valid @RequestBody CreateGameRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(gameService.createGame(request));
	}

	@GetMapping
	public ResponseEntity<List<GameSummaryResponse>> listGames() {
		return ResponseEntity.ok(gameService.listGames());
	}

	@GetMapping("/{id}")
	public ResponseEntity<GameResponse> getGame(@PathVariable Long id) {
		return ResponseEntity.ok(gameService.getGame(id));
	}

	@GetMapping("/{id}/audit")
	public ResponseEntity<GameAuditResponse> auditGame(@PathVariable Long id) {
		return ResponseEntity.ok(gameService.auditGame(id));
	}
}
