package com.asd.monopoly_bank.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asd.monopoly_bank.AbstractIntegrationTest;
import com.asd.monopoly_bank.exception.GlobalExceptionHandler;
import com.asd.monopoly_bank.repository.AccountRepository;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GameControllerIntegrationTest extends AbstractIntegrationTest {

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

	@BeforeEach
	void setUp() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		gameRepository.deleteAll();

		mockMvc = MockMvcBuilders.standaloneSetup(gameController)
				.setControllerAdvice(globalExceptionHandler)
				.build();
	}

	@Test
	void createsGameWithDefaultAndCustomStartingBalancesAndBankInOneTransaction() throws Exception {
		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "  Friday Night Table  ",
								  "players": [
								    { "name": "  Ana ", "color": "RED" },
								    { "name": "Bruno", "color": "BLUE", "startingBalanceCents": 200000 }
								  ]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("Friday Night Table"))
				.andExpect(jsonPath("$.bank.type").value("BANK"))
				.andExpect(jsonPath("$.bank.name").value("Bank"))
				.andExpect(jsonPath("$.bank.initialBalanceCents").value(2_000_000))
				.andExpect(jsonPath("$.bank.balanceCents").value(2_000_000))
				.andExpect(jsonPath("$.players.length()").value(2))
				.andExpect(jsonPath("$.players[0].name").value("Ana"))
				.andExpect(jsonPath("$.players[0].color").value("RED"))
				.andExpect(jsonPath("$.players[0].initialBalanceCents").value(150_000))
				.andExpect(jsonPath("$.players[0].balanceCents").value(150_000))
				.andExpect(jsonPath("$.players[1].name").value("Bruno"))
				.andExpect(jsonPath("$.players[1].color").value("BLUE"))
				.andExpect(jsonPath("$.players[1].balanceCents").value(200_000))
				.andExpect(jsonPath("$.totalMoneyCents").value(2_350_000))
				.andExpect(jsonPath("$.transactionCount").value(0));

		assertThat(gameRepository.count()).isEqualTo(1);
		assertThat(accountRepository.count()).isEqualTo(3);
	}

	@Test
	void defaultsGameNameWhenOmittedOrBlankAndListsGamesNewestFirst() throws Exception {
		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "players": [
								    { "name": "Ana", "color": "RED" },
								    { "name": "Bruno", "color": "BLUE" }
								  ]
								}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Banco Imobiliário"));

		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "Sunday Finale",
								  "players": [
								    { "name": "Carla", "color": "GREEN" },
								    { "name": "Duda", "color": "YELLOW" },
								    { "name": "Edu", "color": "PURPLE" }
								  ]
								}
								"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/games"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("Sunday Finale"))
				.andExpect(jsonPath("$[0].playerCount").value(3))
				.andExpect(jsonPath("$[0].transactionCount").value(0))
				.andExpect(jsonPath("$[1].name").value("Banco Imobiliário"))
				.andExpect(jsonPath("$[1].playerCount").value(2));
	}

	@Test
	void rejectsFewerThanTwoOrMoreThanSixPlayers() throws Exception {
		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "players": [
								    { "name": "Solo", "color": "RED" }
								  ]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("A game requires between 2 and 6 players (received 1)."));

		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "players": [
								    { "name": "P1", "color": "RED" },
								    { "name": "P2", "color": "BLUE" },
								    { "name": "P3", "color": "GREEN" },
								    { "name": "P4", "color": "YELLOW" },
								    { "name": "P5", "color": "PURPLE" },
								    { "name": "P6", "color": "ORANGE" },
								    { "name": "P7", "color": "RED" }
								  ]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("A game requires between 2 and 6 players (received 7)."));

		assertThat(gameRepository.count()).isZero();
	}

	@Test
	void rejectsCaseInsensitiveDuplicatePlayerNamesWith409() throws Exception {
		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "players": [
								    { "name": "Alice", "color": "RED" },
								    { "name": "  alice  ", "color": "BLUE" }
								  ]
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Duplicate Player or Pawn Color"))
				.andExpect(jsonPath("$.detail").value(
						"A player named 'alice' is already in this game. Each player must have a unique name."));

		assertThat(gameRepository.count()).isZero();
	}

	@Test
	void rejectsDuplicatePawnColorsWith409() throws Exception {
		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "players": [
								    { "name": "Alice", "color": "RED" },
								    { "name": "Bob", "color": "RED" }
								  ]
								}
								"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Duplicate Player or Pawn Color"))
				.andExpect(jsonPath("$.detail").value(
						"Pawn color RED is already taken in this game. Each player must choose a unique color."));

		assertThat(gameRepository.count()).isZero();
	}

	@Test
	void rejectsBlankPlayerNameMissingColorOrNegativeStartingBalanceWith400() throws Exception {
		mockMvc.perform(post("/api/games")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "players": [
								    { "name": "   ", "color": "RED" },
								    { "name": "Bob", "color": "BLUE", "startingBalanceCents": -100 }
								  ]
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Validation Failed"))
				.andExpect(jsonPath("$.errors['players[0].name']").value("Player name is required."))
				.andExpect(jsonPath("$.errors['players[1].startingBalanceCents']").value(
						"Starting balance cannot be negative."));

		assertThat(gameRepository.count()).isZero();
	}
}
