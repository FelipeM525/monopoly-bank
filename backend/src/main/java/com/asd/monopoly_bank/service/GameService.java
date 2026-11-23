package com.asd.monopoly_bank.service;

import com.asd.monopoly_bank.config.GameRulesProperties;
import com.asd.monopoly_bank.dto.AccountResponse;
import com.asd.monopoly_bank.dto.CreateGameRequest;
import com.asd.monopoly_bank.dto.CreateGameRequest.PlayerSetupRequest;
import com.asd.monopoly_bank.dto.GameAuditResponse;
import com.asd.monopoly_bank.dto.GameAuditResponse.AccountAuditEntry;
import com.asd.monopoly_bank.dto.GameResponse;
import com.asd.monopoly_bank.dto.GameSummaryResponse;
import com.asd.monopoly_bank.exception.DuplicatePlayerException;
import com.asd.monopoly_bank.exception.ResourceNotFoundException;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.AccountType;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.PawnColor;
import com.asd.monopoly_bank.model.Transaction;
import com.asd.monopoly_bank.repository.AccountRepository;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GameService {

	private static final String DEFAULT_GAME_NAME = "Banco Imobiliário";

	private final GameRepository gameRepository;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final GameRulesProperties gameRules;

	@Transactional
	public GameResponse createGame(CreateGameRequest request) {
		List<PlayerSetupRequest> playerRequests = request.players();
		validatePlayerCount(playerRequests.size());
		validateUniquePlayersAndColors(playerRequests);

		String gameName = (request.name() == null || request.name().isBlank())
				? DEFAULT_GAME_NAME
				: request.name().trim();

		Game game = gameRepository.save(new Game(gameName));

		Account bank = accountRepository.save(
				Account.createBank(game, gameRules.getBank().getInitialCashCents()));

		List<AccountResponse> players = playerRequests.stream()
				.map(p -> {
					long startingBalance = p.startingBalanceCents() != null
							? p.startingBalanceCents()
							: gameRules.getStartingBalanceCents();
					Account savedPlayer = accountRepository.save(
							Account.createPlayer(game, p.name().trim(), p.color(), startingBalance));
					return AccountResponse.from(savedPlayer);
				})
				.toList();

		return GameResponse.from(game, AccountResponse.from(bank), players, 0L);
	}

	@Transactional(readOnly = true)
	public GameResponse getGame(Long gameId) {
		Game game = gameRepository.findById(gameId)
				.orElseThrow(() -> ResourceNotFoundException.game(gameId));

		List<Account> accounts = accountRepository.findByGameIdOrderByIdAsc(gameId);

		AccountResponse bank = accounts.stream()
				.filter(a -> a.getType() == AccountType.BANK)
				.findFirst()
				.map(AccountResponse::from)
				.orElseThrow(() -> new IllegalStateException("Bank account missing for game #" + gameId));

		List<AccountResponse> players = accounts.stream()
				.filter(a -> a.getType() == AccountType.PLAYER)
				.map(AccountResponse::from)
				.toList();

		long txCount = transactionRepository.countByGameId(gameId);
		return GameResponse.from(game, bank, players, txCount);
	}

	@Transactional(readOnly = true)
	public List<GameSummaryResponse> listGames() {
		return gameRepository.findAllByOrderByCreatedAtDescIdDesc().stream()
				.map(game -> {
					int playerCount = (int) accountRepository.findByGameIdOrderByIdAsc(game.getId())
							.stream()
							.filter(a -> a.getType() == AccountType.PLAYER)
							.count();
					long txCount = transactionRepository.countByGameId(game.getId());
					return GameSummaryResponse.from(game, playerCount, txCount);
				})
				.toList();
	}

	@Transactional(readOnly = true)
	public GameAuditResponse auditGame(Long gameId) {
		if (!gameRepository.existsById(gameId)) {
			throw ResourceNotFoundException.game(gameId);
		}

		List<Account> accounts = accountRepository.findByGameIdOrderByIdAsc(gameId);
		List<Transaction> transactions = transactionRepository
				.findAllByGameIdOrderByCreatedAtAscIdAsc(gameId);

		List<AccountAuditEntry> entries = buildAccountAuditEntries(accounts, transactions);
		return buildGameAuditResponse(gameId, entries, transactions.size());
	}

	private List<AccountAuditEntry> buildAccountAuditEntries(
			List<Account> accounts,
			List<Transaction> transactions) {
		Map<Long, Long> creditsByAccount = new HashMap<>();
		Map<Long, Long> debitsByAccount = new HashMap<>();

		transactions.forEach(tx -> {
			debitsByAccount.merge(tx.getFromAccount().getId(), tx.getAmountCents(), Math::addExact);
			creditsByAccount.merge(tx.getToAccount().getId(), tx.getAmountCents(), Math::addExact);
		});

		return accounts.stream()
				.map(account -> AccountAuditEntry.from(
						account,
						creditsByAccount.getOrDefault(account.getId(), 0L),
						debitsByAccount.getOrDefault(account.getId(), 0L)))
				.toList();
	}

	private GameAuditResponse buildGameAuditResponse(
			Long gameId,
			List<AccountAuditEntry> entries,
			long transactionCount) {
		long initialTotal = entries.stream().mapToLong(AccountAuditEntry::initialBalanceCents).sum();
		long currentTotal = entries.stream().mapToLong(AccountAuditEntry::storedBalanceCents).sum();
		long rebuiltTotal = entries.stream().mapToLong(AccountAuditEntry::rebuiltBalanceCents).sum();

		boolean allAccountsConsistent = entries.stream().allMatch(AccountAuditEntry::consistent);
		boolean totalMoneyPreserved = initialTotal == currentTotal && currentTotal == rebuiltTotal;

		return new GameAuditResponse(
				gameId,
				allAccountsConsistent && totalMoneyPreserved,
				initialTotal,
				currentTotal,
				rebuiltTotal,
				transactionCount,
				entries);
	}

	private void validatePlayerCount(int count) {
		int min = gameRules.getMinPlayers();
		int max = gameRules.getMaxPlayers();
		if (count < min || count > max) {
			throw new IllegalArgumentException(
					String.format("A game requires between %d and %d players (received %d).", min, max, count));
		}
	}

	private void validateUniquePlayersAndColors(List<PlayerSetupRequest> playerRequests) {
		Set<String> seenNames = new HashSet<>();
		Set<PawnColor> seenColors = EnumSet.noneOf(PawnColor.class);

		playerRequests.forEach(player -> {
			String trimmedName = player.name().trim();
			String normalizedName = trimmedName.toLowerCase(Locale.ROOT);

			if ("bank".equals(normalizedName)) {
				throw new IllegalArgumentException("Player name 'Bank' is reserved for the bank account.");
			}
			if (!seenNames.add(normalizedName)) {
				throw DuplicatePlayerException.duplicateName(trimmedName);
			}
			if (!seenColors.add(player.color())) {
				throw DuplicatePlayerException.duplicateColor(player.color());
			}
		});
	}
}
