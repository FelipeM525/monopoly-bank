package com.asd.monopoly_bank.repository;

import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.AccountType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

	List<Account> findByGameIdOrderByIdAsc(Long gameId);

	Optional<Account> findByGameIdAndType(Long gameId, AccountType type);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id = :id")
	Optional<Account> findByIdForUpdate(@Param("id") Long id);

	/**
	 * Acquires PostgreSQL {@code FOR UPDATE} row locks on the requested accounts in deterministic
	 * ascending {@code id} order so concurrent crossed transfers (A→B and B→A) never deadlock.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT a FROM Account a WHERE a.id IN :ids ORDER BY a.id ASC")
	List<Account> findAllByIdInOrderByIdForUpdate(@Param("ids") Collection<Long> ids);
}
