package com.asd.monopoly_bank.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
public class Transaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false, updatable = false)
	private Game game;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "from_account_id", nullable = false, updatable = false)
	private Account fromAccount;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "to_account_id", nullable = false, updatable = false)
	private Account toAccount;

	@Column(name = "amount_cents", nullable = false, updatable = false)
	private long amountCents;

	@Column(nullable = false, length = 120, updatable = false)
	private String reason;

	@Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
	private UUID idempotencyKey;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "reversal_of_transaction_id", unique = true, updatable = false)
	private Transaction reversalOf;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
