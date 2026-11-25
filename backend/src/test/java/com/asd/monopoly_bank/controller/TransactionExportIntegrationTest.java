package com.asd.monopoly_bank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asd.monopoly_bank.AbstractIntegrationTest;
import com.asd.monopoly_bank.dto.CreateTransactionRequest;
import com.asd.monopoly_bank.exception.GlobalExceptionHandler;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.PawnColor;
import com.asd.monopoly_bank.repository.AccountRepository;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import com.asd.monopoly_bank.service.TransactionService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionExportIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private TransactionExportController exportController;

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

	private MockMvc mockMvc;
	private Game game;

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		gameRepository.deleteAll();

		game = gameRepository.save(new Game("Export Table"));
		Account bank = accountRepository.save(Account.createBank(game, 2_000_000L));
		Account ana = accountRepository.save(Account.createPlayer(game, "Ana", PawnColor.RED, 150_000L));
		Account bruno = accountRepository.save(Account.createPlayer(game, "Bruno", PawnColor.BLUE, 150_000L));

		transactionService.transfer(
				game.getId(),
				new CreateTransactionRequest(bank.getId(), ana.getId(), 20_000L, "Salary"),
				UUID.randomUUID());
		transactionService.transfer(
				game.getId(),
				new CreateTransactionRequest(ana.getId(), bruno.getId(), 15_000L, "Rent"),
				UUID.randomUUID());

		mockMvc = MockMvcBuilders.standaloneSetup(exportController)
				.setControllerAdvice(globalExceptionHandler)
				.build();
	}

	@Test
	void exportsTheFullHistoryAsCsvByDefaultWithADownloadFilename() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/games/{gameId}/transactions/export", game.getId()))
				.andExpect(status().isOk())
				.andReturn();

		assertThat(result.getResponse().getContentType()).startsWith("text/csv");
		assertThat(result.getResponse().getHeader("Content-Disposition"))
				.contains("attachment")
				.contains("game-" + game.getId() + "-transactions.csv");

		String[] lines = result.getResponse().getContentAsString(StandardCharsets.UTF_8)
				.substring(1)
				.split("\r\n");
		assertThat(lines).hasSize(3);
		assertThat(lines[1]).contains("Bank,BANK", "Ana,PLAYER", ",20000,Salary,");
		assertThat(lines[2]).contains("Ana,PLAYER", "Bruno,PLAYER", ",15000,Rent,");
	}

	@Test
	void exportsTheFullHistoryAsJsonOldestFirst() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/games/{gameId}/transactions/export", game.getId())
						.param("format", "JSON"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].reason").value("Salary"))
				.andExpect(jsonPath("$[0].payer").value("Bank"))
				.andExpect(jsonPath("$[0].amountCents").value(20_000))
				.andExpect(jsonPath("$[1].reason").value("Rent"))
				.andExpect(jsonPath("$[1].receiver").value("Bruno"))
				.andReturn();

		assertThat(result.getResponse().getContentType()).startsWith("application/json");
		assertThat(result.getResponse().getHeader("Content-Disposition"))
				.contains("game-" + game.getId() + "-transactions.json");
	}

	@Test
	void rejectsUnsupportedFormatsAndUnknownGames() throws Exception {
		mockMvc.perform(get("/api/games/{gameId}/transactions/export", game.getId())
						.param("format", "xml"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Unsupported export format 'xml'. Use 'csv' or 'json'."));

		mockMvc.perform(get("/api/games/{gameId}/transactions/export", 999_999L))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.detail").value("Game #999999 was not found."));
	}
}
