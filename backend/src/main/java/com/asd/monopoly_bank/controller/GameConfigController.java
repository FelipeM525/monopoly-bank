package com.asd.monopoly_bank.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.asd.monopoly_bank.config.GameRulesProperties;
import com.asd.monopoly_bank.dto.GameConfigResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class GameConfigController {

	private final GameRulesProperties gameRulesProperties;

	@GetMapping
	public ResponseEntity<GameConfigResponse> getConfig() {
		return ResponseEntity.ok(GameConfigResponse.from(gameRulesProperties));
	}

}
