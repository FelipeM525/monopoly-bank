package com.asd.monopoly_bank.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.asd.monopoly_bank.AbstractIntegrationTest;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.AccountType;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.PawnColor;
import com.asd.monopoly_bank.model.Transaction;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

class RepositoryIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@BeforeEach
	void cleanUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		gameRepository.deleteAll();
	}

	@Test
	@Transactional
	void locksAccountsInAscendingIdOrderRegardlessOfInputOrder() {
		Game game = gameRepository.save(new Game("Table 1"));
		Account bank = accountRepository.save(Account.createBank(game, 2_000_000L));
		Account alice = accountRepository.save(Account.createPlayer(game, "Alice", PawnColor.RED, 150_000L));
		Account bob = accountRepository.save(Account.createPlayer(game, "Bob", PawnColor.BLUE, 150_000L));

		List<Account> locked = accountRepository.findAllByIdInOrderByIdForUpdate(
				List.of(bob.getId(), bank.getId(), alice.getId()));

		assertThat(locked)
				.extracting(Account::getId)
				.containsExactly(bank.getId(), alice.getId(), bob.getId());
		assertThat(accountRepository.findByGameIdAndType(game.getId(), AccountType.BANK))
				.isPresent()
				.get()
				.extracting(Account::getId)
				.isEqualTo(bank.getId());
	}

	@Test
	void findsTransactionByIdempotencyKeyAndFiltersPagedLedgerByAccount() {
		Game game = gameRepository.save(new Game("Table 2"));
		Account bank = accountRepository.save(Account.createBank(game, 2_000_000L));
		Account alice = accountRepository.save(Account.createPlayer(game, "Alice", PawnColor.RED, 150_000L));
		Account bob = accountRepository.save(Account.createPlayer(game, "Bob", PawnColor.BLUE, 150_000L));
		Account carla = accountRepository.save(Account.createPlayer(game, "Carla", PawnColor.GREEN, 150_000L));

		UUID key1 = UUID.randomUUID();
		Transaction tx1 = saveTransaction(game, bank, alice, 20_000L, "Salary", key1);
		Transaction tx2 = saveTransaction(game, alice, bob, 15_000L, "Rent", UUID.randomUUID());
		Transaction tx3 = saveTransaction(game, bob, carla, 10_000L, "Rent", UUID.randomUUID());

		assertThat(transactionRepository.findByIdempotencyKey(key1))
				.isPresent()
				.get()
				.extracting(Transaction::getId)
				.isEqualTo(tx1.getId());

		// Unfiltered ledger returns all 3 newest-first
		Page<Transaction> allPage = transactionRepository.findLedgerByGameIdAndOptionalAccountId(
				game.getId(), null, PageRequest.of(0, 10));
		assertThat(allPage.getTotalElements()).isEqualTo(3);
		assertThat(allPage.getContent())
				.extracting(Transaction::getId)
				.containsExactly(tx3.getId(), tx2.getId(), tx1.getId());

		// Filtered by Alice returns only tx2 (payer) and tx1 (receiver)
		Page<Transaction> alicePage = transactionRepository.findLedgerByGameIdAndOptionalAccountId(
				game.getId(), alice.getId(), PageRequest.of(0, 10));
		assertThat(alicePage.getTotalElements()).isEqualTo(2);
		assertThat(alicePage.getContent())
				.extracting(Transaction::getId)
				.containsExactly(tx2.getId(), tx1.getId());

		// Pagination page size 1 works
		Page<Transaction> firstOnly = transactionRepository.findLedgerByGameIdAndOptionalAccountId(
				game.getId(), alice.getId(), PageRequest.of(0, 1));
		assertThat(firstOnly.getTotalElements()).isEqualTo(2);
		assertThat(firstOnly.getTotalPages()).isEqualTo(2);
		assertThat(firstOnly.getContent()).extracting(Transaction::getId).containsExactly(tx2.getId());
	}

	private Transaction saveTransaction(
			Game game, Account from, Account to, long amountCents, String reason, UUID idempotencyKey) {
		Transaction tx = new Transaction();
		tx.setGame(game);
		tx.setFromAccount(from);
		tx.setToAccount(to);
		tx.setAmountCents(amountCents);
		tx.setReason(reason);
		tx.setIdempotencyKey(idempotencyKey);
		return transactionRepository.save(tx);
	}
}
