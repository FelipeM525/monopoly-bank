package com.asd.monopoly_bank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asd.monopoly_bank.AbstractIntegrationTest;
import com.asd.monopoly_bank.config.GameRulesProperties;
import com.asd.monopoly_bank.exception.GlobalExceptionHandler;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.PawnColor;
import com.asd.monopoly_bank.repository.AccountRepository;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import java.util.UUID;
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

	private long totalMoneyInGame(Long gameId) {
		return accountRepository.findByGameIdOrderByIdAsc(gameId).stream()
				.mapToLong(Account::getBalanceCents)
				.sum();
	}
}
