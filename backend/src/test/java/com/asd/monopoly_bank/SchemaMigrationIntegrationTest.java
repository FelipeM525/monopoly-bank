package com.asd.monopoly_bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

class SchemaMigrationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private JdbcTemplate jdbc;

	private Long gameId;

	@BeforeEach
	void setUpGame() {
		jdbc.execute("DELETE FROM transactions");
		jdbc.execute("DELETE FROM accounts");
		jdbc.execute("DELETE FROM games");

		gameId = jdbc.queryForObject(
				"INSERT INTO games (name) VALUES ('Friday Night') RETURNING id",
				Long.class);
	}

	@Test
	void flywayMigrationAppliesVersion1() {
		Integer applied = jdbc.queryForObject(
				"SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = true",
				Integer.class);
		assertThat(applied).isEqualTo(1);
	}

	@Test
	void enforcesSingleBankPerGame() {
		insertBank(gameId, 200_000_00L);

		assertThatThrownBy(() -> insertBank(gameId, 200_000_00L))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enforcesCaseInsensitiveUniquePlayerNamePerGame() {
		insertPlayer(gameId, "Alice", "RED", 150_000L);

		assertThatThrownBy(() -> insertPlayer(gameId, "alice", "BLUE", 150_000L))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enforcesUniqueColorPerGameButAllowsSameColorInAnotherGame() {
		insertPlayer(gameId, "Alice", "RED", 150_000L);

		assertThatThrownBy(() -> insertPlayer(gameId, "Bob", "RED", 150_000L))
				.isInstanceOf(DataIntegrityViolationException.class);

		Long secondGameId = jdbc.queryForObject(
				"INSERT INTO games (name) VALUES ('Saturday Night') RETURNING id",
				Long.class);
		Long otherGamePlayerId = insertPlayer(secondGameId, "Alice", "RED", 150_000L);
		assertThat(otherGamePlayerId).isNotNull();
	}

	@Test
	void rejectsNegativePlayerBalance() {
		Long playerId = insertPlayer(gameId, "Alice", "RED", 150_000L);

		assertThatThrownBy(() -> jdbc.update(
				"UPDATE accounts SET balance_cents = -1 WHERE id = ?",
				playerId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enforcesTransactionInvariantsAndIdempotencyAndSingleReversal() {
		Long bankId = insertBank(gameId, 2_000_000L);
		Long aliceId = insertPlayer(gameId, "Alice", "RED", 150_000L);

		// Self-transfer (from_account_id == to_account_id) is rejected
		assertThatThrownBy(() -> insertTransaction(gameId, aliceId, aliceId, 100L, "Rent", UUID.randomUUID(), null))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Non-positive amount is rejected
		assertThatThrownBy(() -> insertTransaction(gameId, aliceId, bankId, 0L, "Tax", UUID.randomUUID(), null))
				.isInstanceOf(DataIntegrityViolationException.class);

		// Duplicate idempotency_key is rejected
		UUID key = UUID.randomUUID();
		Long txId = insertTransaction(gameId, aliceId, bankId, 5_000L, "Purchase", key, null);
		assertThatThrownBy(() -> insertTransaction(gameId, aliceId, bankId, 5_000L, "Purchase", key, null))
				.isInstanceOf(DataIntegrityViolationException.class);

		// A transaction can be reversed once, but not twice
		Long reversalId = insertTransaction(gameId, bankId, aliceId, 5_000L, "Reversal", UUID.randomUUID(), txId);
		assertThat(reversalId).isNotNull();

		assertThatThrownBy(() -> insertTransaction(gameId, bankId, aliceId, 5_000L, "Reversal", UUID.randomUUID(), txId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Long insertBank(Long gId, long cashCents) {
		return jdbc.queryForObject(
				"""
				INSERT INTO accounts (game_id, type, name, color, initial_balance_cents, balance_cents)
				VALUES (?, 'BANK', 'Bank', NULL, ?, ?)
				RETURNING id
				""",
				Long.class, gId, cashCents, cashCents);
	}

	private Long insertPlayer(Long gId, String name, String color, long startingCents) {
		return jdbc.queryForObject(
				"""
				INSERT INTO accounts (game_id, type, name, color, initial_balance_cents, balance_cents)
				VALUES (?, 'PLAYER', ?, ?, ?, ?)
				RETURNING id
				""",
				Long.class, gId, name, color, startingCents, startingCents);
	}

	private Long insertTransaction(
			Long gId,
			Long fromId,
			Long toId,
			long amountCents,
			String reason,
			UUID idempotencyKey,
			Long reversalOfId) {
		return jdbc.queryForObject(
				"""
				INSERT INTO transactions (
					game_id, from_account_id, to_account_id, amount_cents, reason, idempotency_key, reversal_of_transaction_id
				) VALUES (?, ?, ?, ?, ?, ?, ?)
				RETURNING id
				""",
				Long.class, gId, fromId, toId, amountCents, reason, idempotencyKey, reversalOfId);
	}
}
