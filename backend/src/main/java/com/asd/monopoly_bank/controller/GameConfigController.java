package com.asd.monopoly_bank.controller;

import com.asd.monopoly_bank.config.GameRulesProperties;
import com.asd.monopoly_bank.dto.GameConfigResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@Tag(name = "Configuration", description = "Game rule defaults, available pawn colors, transaction reasons and bank cash policy")
public class GameConfigController {

	private final GameRulesProperties gameRulesProperties;

	@GetMapping
	@Operation(
			summary = "Get game configuration and rule defaults",
			description = "Returns the configured starting balance, salary, player count bounds, bank cash policy, pawn colors and transaction reasons.")
	@ApiResponse(responseCode = "200", description = "Configuration retrieved successfully")
	public ResponseEntity<GameConfigResponse> getConfig() {
		return ResponseEntity.ok(GameConfigResponse.from(gameRulesProperties));
	}
}
