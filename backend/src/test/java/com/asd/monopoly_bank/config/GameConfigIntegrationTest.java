package com.asd.monopoly_bank.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asd.monopoly_bank.AbstractIntegrationTest;
import com.asd.monopoly_bank.controller.GameConfigController;
import com.asd.monopoly_bank.exception.InsufficientFundsException;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.PawnColor;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class GameConfigIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	private GameRulesProperties gameRulesProperties;

	@Autowired
	private GameConfigController gameConfigController;

	@Test
	void getConfigExposesDefaultRulesColorsReasonsAndBankPolicyFromApplicationYaml() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(gameConfigController).build();

		mockMvc.perform(get("/api/config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startingBalanceCents").value(150_000))
				.andExpect(jsonPath("$.salaryCents").value(20_000))
				.andExpect(jsonPath("$.minPlayers").value(2))
				.andExpect(jsonPath("$.maxPlayers").value(6))
				.andExpect(jsonPath("$.bank.initialCashCents").value(2_000_000))
				.andExpect(jsonPath("$.bank.unlimited").value(false))
				.andExpect(jsonPath("$.colors.length()").value(6))
				.andExpect(jsonPath("$.colors[0]").value("RED"))
				.andExpect(jsonPath("$.reasons.length()").value(5))
				.andExpect(jsonPath("$.reasons[0]").value("Purchase"))
				.andExpect(jsonPath("$.reasons[3]").value("Salary"));
	}

	@Test
	void overridingPropertiesChangesConfigResponseWithoutCodeChanges() throws Exception {
		GameRulesProperties customProps = new GameRulesProperties();
		customProps.setStartingBalanceCents(250_000L);
		customProps.setSalaryCents(30_000L);
		customProps.setMinPlayers(3);
		customProps.setMaxPlayers(5);
		customProps.getBank().setInitialCashCents(5_000_000L);
		customProps.getBank().setUnlimited(true);
		customProps.setReasons(List.of("Purchase", "Rent", "Tax", "Salary", "Mortgage", "Other"));

		MockMvc customMockMvc = MockMvcBuilders.standaloneSetup(new GameConfigController(customProps)).build();

		customMockMvc.perform(get("/api/config"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.startingBalanceCents").value(250_000))
				.andExpect(jsonPath("$.salaryCents").value(30_000))
				.andExpect(jsonPath("$.minPlayers").value(3))
				.andExpect(jsonPath("$.maxPlayers").value(5))
				.andExpect(jsonPath("$.bank.initialCashCents").value(5_000_000))
				.andExpect(jsonPath("$.bank.unlimited").value(true))
				.andExpect(jsonPath("$.reasons.length()").value(6))
				.andExpect(jsonPath("$.reasons[4]").value("Mortgage"));
	}

	@Test
	void bankUnlimitedFlagTogglesBankOverdraftWhilePlayersAreAlwaysRestricted() {
		Game game = new Game("Policy Test");
		Account bank = Account.createBank(game, 10_000L);
		Account alice = Account.createPlayer(game, "Alice", PawnColor.RED, 10_000L);

		// When bank.unlimited = false (default), the bank cannot pay more than its balanceCents
		GameRulesProperties limitedRules = new GameRulesProperties();
		limitedRules.getBank().setUnlimited(false);

		assertThatThrownBy(() -> limitedRules.validateSufficientFunds(bank, 15_000L))
				.isInstanceOf(InsufficientFundsException.class)
				.hasMessage("Bank has R$ 100,00 and cannot pay R$ 150,00.");

		// When bank.unlimited = true, the bank is allowed to pay beyond its cash balance
		GameRulesProperties unlimitedRules = new GameRulesProperties();
		unlimitedRules.getBank().setUnlimited(true);

		assertThatCode(() -> unlimitedRules.validateSufficientFunds(bank, 15_000L))
				.doesNotThrowAnyException();

		// Even when bank.unlimited = true, players are strictly restricted to their available balance
		assertThatThrownBy(() -> unlimitedRules.validateSufficientFunds(alice, 15_000L))
				.isInstanceOf(InsufficientFundsException.class)
				.hasMessage("Alice has R$ 100,00 and cannot pay R$ 150,00.");
	}
}
