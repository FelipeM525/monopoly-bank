package com.asd.monopoly_bank.dto;

import com.asd.monopoly_bank.config.GameRulesProperties;
import com.asd.monopoly_bank.model.PawnColor;
import java.util.Arrays;
import java.util.List;

public record GameConfigResponse(
		long startingBalanceCents,
		long salaryCents,
		int minPlayers,
		int maxPlayers,
		BankPolicyDto bank,
		List<PawnColor> colors,
		List<String> reasons) {

	public record BankPolicyDto(long initialCashCents, boolean unlimited) {
	}

	public static GameConfigResponse from(GameRulesProperties props) {
		return new GameConfigResponse(
				props.getStartingBalanceCents(),
				props.getSalaryCents(),
				props.getMinPlayers(),
				props.getMaxPlayers(),
				new BankPolicyDto(props.getBank().getInitialCashCents(), props.getBank().isUnlimited()),
				Arrays.asList(PawnColor.values()),
				List.copyOf(props.getReasons()));
	}
}
