package com.asd.monopoly_bank.exception;

import com.asd.monopoly_bank.model.PawnColor;

public class DuplicatePlayerException extends RuntimeException {

	public DuplicatePlayerException(String message) {
		super(message);
	}

	public static DuplicatePlayerException duplicateName(String name) {
		return new DuplicatePlayerException(
				"A player named '" + name + "' is already in this game. Each player must have a unique name.");
	}

	public static DuplicatePlayerException duplicateColor(PawnColor color) {
		return new DuplicatePlayerException(
				"Pawn color " + color + " is already taken in this game. Each player must choose a unique color.");
	}
}
