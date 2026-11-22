package com.asd.monopoly_bank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false, updatable = false)
	private Game game;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16, updatable = false)
	private AccountType type;

	@Column(nullable = false, length = 60)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(length = 32)
	private PawnColor color;

	@Column(name = "initial_balance_cents", nullable = false, updatable = false)
	private long initialBalanceCents;

	@Column(name = "balance_cents", nullable = false)
	private long balanceCents;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public static Account createBank(Game game, long initialCashCents) {
		Account bank = new Account();
		bank.setGame(game);
		bank.setType(AccountType.BANK);
		bank.setName("Bank");
		bank.setColor(null);
		bank.setInitialBalanceCents(initialCashCents);
		bank.setBalanceCents(initialCashCents);
		return bank;
	}

	public static Account createPlayer(Game game, String name, PawnColor color, long startingBalanceCents) {
		Account player = new Account();
		player.setGame(game);
		player.setType(AccountType.PLAYER);
		player.setName(name);
		player.setColor(color);
		player.setInitialBalanceCents(startingBalanceCents);
		player.setBalanceCents(startingBalanceCents);
		return player;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
