package com.asd.monopoly_bank.repository;

import com.asd.monopoly_bank.model.Transaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

	@Query("""
			SELECT t FROM Transaction t
			JOIN FETCH t.fromAccount
			JOIN FETCH t.toAccount
			WHERE t.idempotencyKey = :idempotencyKey
			""")
	Optional<Transaction> findByIdempotencyKey(@Param("idempotencyKey") UUID idempotencyKey);

	@Query(
			value = """
					SELECT t FROM Transaction t
					JOIN FETCH t.fromAccount
					JOIN FETCH t.toAccount
					LEFT JOIN FETCH t.reversalOf
					WHERE t.game.id = :gameId
					  AND (:accountId IS NULL OR t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
					ORDER BY t.createdAt DESC, t.id DESC
					""",
			countQuery = """
					SELECT COUNT(t) FROM Transaction t
					WHERE t.game.id = :gameId
					  AND (:accountId IS NULL OR t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
					""")
	Page<Transaction> findLedgerByGameIdAndOptionalAccountId(
			@Param("gameId") Long gameId,
			@Param("accountId") Long accountId,
			Pageable pageable);

	@Query("""
			SELECT t FROM Transaction t
			JOIN FETCH t.fromAccount
			JOIN FETCH t.toAccount
			LEFT JOIN FETCH t.reversalOf
			WHERE t.game.id = :gameId
			ORDER BY t.createdAt ASC, t.id ASC
			""")
	List<Transaction> findAllByGameIdOrderByCreatedAtAscIdAsc(@Param("gameId") Long gameId);

	boolean existsByReversalOfId(Long transactionId);

	long countByGameId(Long gameId);
}
