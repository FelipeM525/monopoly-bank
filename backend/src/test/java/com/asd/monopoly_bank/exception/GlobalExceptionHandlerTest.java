package com.asd.monopoly_bank.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asd.monopoly_bank.model.PawnColor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new DummyErrorController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();
	}

	@Test
	void formatsCentavosAsBrazilianReaisWithoutFloatingPoint() {
		assertThat(MoneyFormatter.formatCents(0L)).isEqualTo("R$ 0,00");
		assertThat(MoneyFormatter.formatCents(5L)).isEqualTo("R$ 0,05");
		assertThat(MoneyFormatter.formatCents(12_000L)).isEqualTo("R$ 120,00");
		assertThat(MoneyFormatter.formatCents(150_000L)).isEqualTo("R$ 1.500,00");
		assertThat(MoneyFormatter.formatCents(-20_050L)).isEqualTo("-R$ 200,50");
	}

	@Test
	void returns404ProblemDetailForResourceNotFound() throws Exception {
		mockMvc.perform(get("/api/test/not-found"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404))
				.andExpect(jsonPath("$.title").value("Resource Not Found"))
				.andExpect(jsonPath("$.detail").value("Game #42 was not found."))
				.andExpect(jsonPath("$.instance").value("/api/test/not-found"))
				.andExpect(jsonPath("$.timestamp").exists());
	}

	@Test
	void returns409ProblemDetailForDuplicatePlayerOrColor() throws Exception {
		mockMvc.perform(get("/api/test/duplicate-name"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.title").value("Duplicate Player or Pawn Color"))
				.andExpect(jsonPath("$.detail").value(
						"A player named 'Alice' is already in this game. Each player must have a unique name."));

		mockMvc.perform(get("/api/test/duplicate-color"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409))
				.andExpect(jsonPath("$.detail").value(
						"Pawn color RED is already taken in this game. Each player must choose a unique color."));
	}

	@Test
	void returns422ProblemDetailWithHumanReadableReaisMessageForInsufficientFunds() throws Exception {
		mockMvc.perform(get("/api/test/insufficient-funds"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.title").value("Insufficient Funds"))
				.andExpect(jsonPath("$.detail").value("Alice has R$ 120,00 and cannot pay R$ 200,00."))
				.andExpect(jsonPath("$.payerName").value("Alice"))
				.andExpect(jsonPath("$.availableCents").value(12_000))
				.andExpect(jsonPath("$.requestedCents").value(20_000));
	}

	@Test
	void returns422ProblemDetailForInvalidTransfer() throws Exception {
		mockMvc.perform(get("/api/test/invalid-transfer"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.title").value("Invalid Transfer"))
				.andExpect(jsonPath("$.detail").value("Payer and receiver cannot be the same account."));
	}

	@Test
	void returns400ProblemDetailWithFieldErrorsMapForBeanValidationFailures() throws Exception {
		mockMvc.perform(post("/api/test/validate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{
								  "name": "   ",
								  "amountCents": -50
								}
								"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Validation Failed"))
				.andExpect(jsonPath("$.errors.name").value("Player name is required."))
				.andExpect(jsonPath("$.errors.amountCents").value("Amount must be greater than zero."));
	}

	@Test
	void returns400ProblemDetailForMissingOrInvalidIdempotencyHeader() throws Exception {
		mockMvc.perform(post("/api/test/header"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.title").value("Bad Request"))
				.andExpect(jsonPath("$.detail").value("Required header 'Idempotency-Key' is missing."));

		mockMvc.perform(post("/api/test/header").header("Idempotency-Key", "not-a-uuid"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.detail").value("Invalid value for 'Idempotency-Key'."));
	}

	@RestController
	@RequestMapping("/api/test")
	static class DummyErrorController {

		record SampleRequest(
				@NotBlank(message = "Player name is required.") String name,
				@Positive(message = "Amount must be greater than zero.") long amountCents) {
		}

		@GetMapping("/not-found")
		void notFound() {
			throw ResourceNotFoundException.game(42L);
		}

		@GetMapping("/duplicate-name")
		void duplicateName() {
			throw DuplicatePlayerException.duplicateName("Alice");
		}

		@GetMapping("/duplicate-color")
		void duplicateColor() {
			throw DuplicatePlayerException.duplicateColor(PawnColor.RED);
		}

		@GetMapping("/insufficient-funds")
		void insufficientFunds() {
			throw new InsufficientFundsException("Alice", 12_000L, 20_000L);
		}

		@GetMapping("/invalid-transfer")
		void invalidTransfer() {
			throw new InvalidTransferException("Payer and receiver cannot be the same account.");
		}

		@PostMapping("/validate")
		void validate(@Valid @RequestBody SampleRequest request) {
		}

		@PostMapping("/header")
		void header(@RequestHeader("Idempotency-Key") UUID idempotencyKey) {
		}
	}
}
