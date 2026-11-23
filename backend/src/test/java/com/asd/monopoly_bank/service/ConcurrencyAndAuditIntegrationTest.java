package com.asd.monopoly_bank.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asd.monopoly_bank.AbstractIntegrationTest;
import com.asd.monopoly_bank.controller.GameController;
import com.asd.monopoly_bank.dto.CreateTransactionRequest;
import com.asd.monopoly_bank.dto.GameAuditResponse;
import com.asd.monopoly_bank.dto.GameAuditResponse.AccountAuditEntry;
import com.asd.monopoly_bank.exception.GlobalExceptionHandler;
import com.asd.monopoly_bank.exception.InsufficientFundsException;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.PawnColor;
import com.asd.monopoly_bank.repository.AccountRepository;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import com.asd.monopoly_bank.service.TransactionService.TransferResult;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ConcurrencyAndAuditIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private GameService gameService;

	@Autowired
	private GameController gameController;

	@Autowired
	private GlobalExceptionHandler globalExceptionHandler;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	private MockMvc mockMvc;
	private Game game;
	private Account bank;
	private Account ana;
	private Account bruno;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		gameRepository.deleteAll();

		game = gameRepository.save(new Game("Concurrency & Audit Table"));
		bank = accountRepository.save(Account.createBank(game, 2_000_000L));
		ana = accountRepository.save(Account.createPlayer(game, "Ana", PawnColor.RED, 150_000L));
		bruno = accountRepository.save(Account.createPlayer(game, "Bruno", PawnColor.BLUE, 150_000L));

		mockMvc = MockMvcBuilders.standaloneSetup(gameController)
				.setControllerAdvice(globalExceptionHandler)
				.build();
	}

	@Test
	void concurrentCrossedTransfersBetweenTwoAccountsNeverDeadlockAndPreserveTotalMoney() throws Exception {
		int pairs = 15;
		int totalThreads = pairs * 2;
		long amountPerTransfer = 1_000L;

		List<Callable<TransferResult>> tasks = IntStream.range(0, totalThreads)
				.<Callable<TransferResult>>mapToObj(i -> {
					boolean anaToBruno = (i % 2 == 0);
					Long fromId = anaToBruno ? ana.getId() : bruno.getId();
					Long toId = anaToBruno ? bruno.getId() : ana.getId();
					CreateTransactionRequest req = new CreateTransactionRequest(
							fromId, toId, amountPerTransfer, "Rent");
					return () -> transactionService.transfer(game.getId(), req, UUID.randomUUID());
				})
				.toList();

		List<Future<TransferResult>> futures = executeConcurrently(tasks);
		futures.forEach(f -> assertThat(unwrapFuture(f).created()).isTrue());

		// 15 transfers of R$ 10,00 each way -> both Ana and Bruno end right back at 150,000 centavos
		assertThat(accountRepository.findById(ana.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(150_000L);
		assertThat(accountRepository.findById(bruno.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(150_000L);

		GameAuditResponse audit = gameService.auditGame(game.getId());
		assertThat(audit.consistent()).isTrue();
		assertThat(audit.transactionCount()).isEqualTo(totalThreads);
		assertThat(audit.currentTotalMoneyCents()).isEqualTo(audit.initialTotalMoneyCents());
	}

	@Test
	void concurrentOverdraftRaceAllowsOnlyOnePaymentWhenCombinedAmountExceedsBalance() throws Exception {
		// Ana has 150,000 centavos. Two concurrent payments of 100,000 centavos each race.
		int threadCount = 2;
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		List<Callable<Void>> tasks = IntStream.range(0, threadCount)
				.<Callable<Void>>mapToObj(i -> {
					Long receiverId = (i == 0) ? bruno.getId() : bank.getId();
					CreateTransactionRequest req = new CreateTransactionRequest(
							ana.getId(), receiverId, 100_000L, "Purchase");
					return () -> {
						try {
							transactionService.transfer(game.getId(), req, UUID.randomUUID());
							succeeded.incrementAndGet();
						} catch (InsufficientFundsException expected) {
							rejected.incrementAndGet();
						}
						return null;
					};
				})
				.toList();

		List<Future<Void>> futures = executeConcurrently(tasks);
		futures.forEach(this::unwrapFuture);

		assertThat(succeeded.get()).isEqualTo(1);
		assertThat(rejected.get()).isEqualTo(1);
		assertThat(accountRepository.findById(ana.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(50_000L);

		GameAuditResponse audit = gameService.auditGame(game.getId());
		assertThat(audit.consistent()).isTrue();
		assertThat(audit.transactionCount()).isEqualTo(1);
	}

	@Test
	void auditEndpointRebuildsBalancesFromInitialBalanceAndLedgerAndDetectsTampering() throws Exception {
		transactionService.transfer(
				game.getId(),
				new CreateTransactionRequest(bank.getId(), ana.getId(), 20_000L, "Salary"),
				UUID.randomUUID());
		transactionService.transfer(
				game.getId(),
				new CreateTransactionRequest(ana.getId(), bruno.getId(), 35_000L, "Rent"),
				UUID.randomUUID());

		mockMvc.perform(get("/api/games/{id}/audit", game.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.gameId").value(game.getId()))
				.andExpect(jsonPath("$.consistent").value(true))
				.andExpect(jsonPath("$.initialTotalMoneyCents").value(2_300_000))
				.andExpect(jsonPath("$.currentTotalMoneyCents").value(2_300_000))
				.andExpect(jsonPath("$.rebuiltTotalMoneyCents").value(2_300_000))
				.andExpect(jsonPath("$.transactionCount").value(2))
				.andExpect(jsonPath("$.accounts.length()").value(3))
				.andExpect(jsonPath("$.accounts[1].name").value("Ana"))
				.andExpect(jsonPath("$.accounts[1].initialBalanceCents").value(150_000))
				.andExpect(jsonPath("$.accounts[1].totalCreditsCents").value(20_000))
				.andExpect(jsonPath("$.accounts[1].totalDebitsCents").value(35_000))
				.andExpect(jsonPath("$.accounts[1].rebuiltBalanceCents").value(135_000))
				.andExpect(jsonPath("$.accounts[1].storedBalanceCents").value(135_000))
				.andExpect(jsonPath("$.accounts[1].consistent").value(true));

		// Simulate unexpected direct balance tampering outside the ledger -> audit reports consistent: false
		Account tamperedAna = accountRepository.findById(ana.getId()).orElseThrow();
		tamperedAna.setBalanceCents(999_999L);
		accountRepository.save(tamperedAna);

		GameAuditResponse tamperedAudit = gameService.auditGame(game.getId());
		assertThat(tamperedAudit.consistent()).isFalse();
		assertThat(tamperedAudit.accounts())
				.filteredOn(a -> a.accountId().equals(ana.getId()))
				.singleElement()
				.satisfies(entry -> {
					assertThat(entry.consistent()).isFalse();
					assertThat(entry.rebuiltBalanceCents()).isEqualTo(135_000L);
					assertThat(entry.storedBalanceCents()).isEqualTo(999_999L);
				});
	}

	private <T> List<Future<T>> executeConcurrently(List<Callable<T>> tasks) throws InterruptedException {
		int count = tasks.size();
		CountDownLatch ready = new CountDownLatch(count);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(count);

		try {
			List<Future<T>> futures = tasks.stream()
					.map(task -> pool.submit(() -> {
						ready.countDown();
						start.await();
						return task.call();
					}))
					.toList();

			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			pool.shutdown();
			assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
			return futures;
		} finally {
			pool.shutdownNow();
		}
	}

	private <T> T unwrapFuture(Future<T> future) {
		try {
			return future.get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
	}
}
