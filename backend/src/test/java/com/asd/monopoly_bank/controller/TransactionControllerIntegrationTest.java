package com.asd.monopoly_bank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asd.monopoly_bank.AbstractIntegrationTest;
import com.asd.monopoly_bank.config.GameRulesProperties;
import com.asd.monopoly_bank.dto.CreateTransactionRequest;
import com.asd.monopoly_bank.exception.GlobalExceptionHandler;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.PawnColor;
import com.asd.monopoly_bank.repository.AccountRepository;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import com.asd.monopoly_bank.service.TransactionService;
import com.asd.monopoly_bank.service.TransactionService.TransferResult;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionControllerIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TransactionController transactionController;

	@Autowired
	private TransactionService transactionService;

	@Autowired
	private GlobalExceptionHandler globalExceptionHandler;

	@Autowired
	private GameRepository gameRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	@Autowired
	private GameRulesProperties gameRules;

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
		gameRules.getBank().setUnlimited(false);

		game = gameRepository.save(new Game("Transfer Test Game"));
		bank = accountRepository.save(Account.createBank(game, 2_000_000L));
		ana = accountRepository.save(Account.createPlayer(game, "Ana", PawnColor.RED, 150_000L));
		bruno = accountRepository.save(Account.createPlayer(game, "Bruno", PawnColor.BLUE, 150_000L));

		mockMvc = MockMvcBuilders.standaloneSetup(transactionController)
				.setControllerAdvice(globalExceptionHandler)
				.build();
	}

	@AfterEach
	void resetBankPolicy() {
		gameRules.getBank().setUnlimited(false);
	}

	@Test
	void transfersAcrossAllThreeDirectionsAndPreservesTotalMoneyInGame() throws Exception {
		long initialTotalMoney = totalMoneyInGame(game.getId());

		// 1. Player -> Bank (Purchase: R$ 200,00 = 20_000 centavos)
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 20000,
								  "reason": "  Purchase  "
								}
								""", ana.getId(), bank.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.gameId").value(game.getId()))
				.andExpect(jsonPath("$.fromAccount.id").value(ana.getId()))
				.andExpect(jsonPath("$.fromAccount.balanceCents").value(130_000))
				.andExpect(jsonPath("$.toAccount.id").value(bank.getId()))
				.andExpect(jsonPath("$.toAccount.balanceCents").value(2_020_000))
				.andExpect(jsonPath("$.amountCents").value(20_000))
				.andExpect(jsonPath("$.reason").value("Purchase"));

		// 2. Bank -> Player (Salary: R$ 200,00 = 20_000 centavos)
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 20000,
								  "reason": "Salary"
								}
								""", bank.getId(), bruno.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fromAccount.balanceCents").value(2_000_000))
				.andExpect(jsonPath("$.toAccount.balanceCents").value(170_000));

		// 3. Player -> Player (Rent: R$ 350,00 = 35_000 centavos, Bruno -> Ana, testing high ID -> low ID lock order)
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 35000,
								  "reason": "Rent"
								}
								""", bruno.getId(), ana.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fromAccount.balanceCents").value(135_000))
				.andExpect(jsonPath("$.toAccount.balanceCents").value(165_000));

		assertThat(totalMoneyInGame(game.getId())).isEqualTo(initialTotalMoney);
		assertThat(transactionRepository.countByGameId(game.getId())).isEqualTo(3);
	}

	@Test
	void rejectsInsufficientPlayerFundsWith422AndLeavesBalancesUnchanged() throws Exception {
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 200000,
								  "reason": "Rent"
								}
								""", ana.getId(), bruno.getId())))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.title").value("Insufficient Funds"))
				.andExpect(jsonPath("$.detail").value("Ana has R$ 1.500,00 and cannot pay R$ 2.000,00."));

		assertThat(accountRepository.findById(ana.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(150_000L);
		assertThat(accountRepository.findById(bruno.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(150_000L);
		assertThat(transactionRepository.countByGameId(game.getId())).isZero();
	}

	@Test
	void enforcesBankCashLimitWhenUnlimitedIsFalseAndAllowsOverdraftWhenUnlimitedIsTrue() throws Exception {
		// Limited bank mode (default): cannot pay more than 2,000,000 centavos
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 2500000,
								  "reason": "Salary"
								}
								""", bank.getId(), ana.getId())))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value("Bank has R$ 20.000,00 and cannot pay R$ 25.000,00."));

		// Unlimited bank mode: bank can pay beyond its initial cash
		gameRules.getBank().setUnlimited(true);

		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 2500000,
								  "reason": "Salary"
								}
								""", bank.getId(), ana.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.fromAccount.balanceCents").value(-500_000))
				.andExpect(jsonPath("$.toAccount.balanceCents").value(2_650_000));
	}

	@Test
	void rejectsSelfTransferCrossGameTransferAndUnknownGameOrAccount() throws Exception {
		// Self-transfer -> 422
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 5000,
								  "reason": "Rent"
								}
								""", ana.getId(), ana.getId())))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value("Payer and receiver cannot be the same account."));

		// Cross-game account -> 422
		Game otherGame = gameRepository.save(new Game("Other Game"));
		Account otherPlayer = accountRepository.save(
				Account.createPlayer(otherGame, "Other", PawnColor.GREEN, 150_000L));

		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 5000,
								  "reason": "Rent"
								}
								""", ana.getId(), otherPlayer.getId())))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value(
						"Account #" + otherPlayer.getId() + " does not belong to game #" + game.getId() + "."));

		// Non-existent account -> 404
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": 999999,
								  "amountCents": 5000,
								  "reason": "Rent"
								}
								""", ana.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));

		// Non-existent game -> 404
		mockMvc.perform(post("/api/games/{gameId}/transactions", 999_999L)
						.header("Idempotency-Key", UUID.randomUUID().toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(String.format("""
								{
								  "fromAccountId": %d,
								  "toAccountId": %d,
								  "amountCents": 5000,
								  "reason": "Rent"
								}
								""", ana.getId(), bruno.getId())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void rejectsMissingOrInvalidIdempotencyKeyHeaderWith400() throws Exception {
		String validBody = String.format("""
				{
				  "fromAccountId": %d,
				  "toAccountId": %d,
				  "amountCents": 10000,
				  "reason": "Rent"
				}
				""", ana.getId(), bruno.getId());

		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("Required header 'Idempotency-Key' is missing."));

		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", "not-a-valid-uuid")
						.contentType(MediaType.APPLICATION_JSON)
						.content(validBody))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("Invalid value for 'Idempotency-Key'."));
	}

	@Test
	void sequentialAndParallelDuplicateIdempotencyKeysChargeOnlyOnceAndRejectMismatchedPayload() throws Exception {
		UUID key = UUID.randomUUID();
		String payload = String.format("""
				{
				  "fromAccountId": %d,
				  "toAccountId": %d,
				  "amountCents": 25000,
				  "reason": "Rent"
				}
				""", ana.getId(), bruno.getId());

		// 1st request -> 201 Created
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", key.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.idempotencyKey").value(key.toString()))
				.andExpect(jsonPath("$.fromAccount.balanceCents").value(125_000))
				.andExpect(jsonPath("$.toAccount.balanceCents").value(175_000));

		// 2nd request with the exact same key & payload -> 200 OK, balances are NOT debited a second time
		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", key.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(payload))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.idempotencyKey").value(key.toString()))
				.andExpect(jsonPath("$.amountCents").value(25_000));

		assertThat(accountRepository.findById(ana.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(125_000L);
		assertThat(accountRepository.findById(bruno.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(175_000L);
		assertThat(transactionRepository.countByGameId(game.getId())).isEqualTo(1);

		// Same key with a DIFFERENT amount or reason -> 422 Unprocessable Entity
		String differentPayload = String.format("""
				{
				  "fromAccountId": %d,
				  "toAccountId": %d,
				  "amountCents": 99999,
				  "reason": "Rent"
				}
				""", ana.getId(), bruno.getId());

		mockMvc.perform(post("/api/games/{gameId}/transactions", game.getId())
						.header("Idempotency-Key", key.toString())
						.contentType(MediaType.APPLICATION_JSON)
						.content(differentPayload))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value(
						"Idempotency-Key '" + key + "' was already used with different transfer parameters."));

		// Parallel double-tap race: 8 threads send the exact same new Idempotency-Key simultaneously
		UUID parallelKey = UUID.randomUUID();
		CreateTransactionRequest parallelReq = new CreateTransactionRequest(
				ana.getId(), bruno.getId(), 10_000L, "Purchase");

		int threadCount = 8;
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threadCount);

		try {
			List<Callable<TransferResult>> tasks = IntStream.range(0, threadCount)
					.<Callable<TransferResult>>mapToObj(i -> () -> {
						ready.countDown();
						start.await();
						return transactionService.transfer(game.getId(), parallelReq, parallelKey);
					})
					.toList();

			List<Future<TransferResult>> futures = tasks.stream().map(pool::submit).toList();
			ready.await();
			start.countDown();

			List<TransferResult> results = futures.stream()
					.map(f -> {
						try {
							return f.get();
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					})
					.toList();

			// Exactly 1 thread created the transaction (201), and the other 7 returned the existing one (200)
			long createdCount = results.stream().filter(TransferResult::created).count();
			long replayedCount = results.stream().filter(r -> !r.created()).count();
			assertThat(createdCount).isEqualTo(1);
			assertThat(replayedCount).isEqualTo(threadCount - 1);
		} finally {
			pool.shutdownNow();
		}

		// Money moved only once for parallelKey (125,000 -> 115,000)
		assertThat(accountRepository.findById(ana.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(115_000L);
		assertThat(accountRepository.findById(bruno.getId()).orElseThrow().getBalanceCents())
				.isEqualTo(185_000L);
		assertThat(transactionRepository.countByGameId(game.getId())).isEqualTo(2);
	}

	private long totalMoneyInGame(Long gameId) {
		return accountRepository.findByGameIdOrderByIdAsc(gameId).stream()
				.mapToLong(Account::getBalanceCents)
				.sum();
	}
}
