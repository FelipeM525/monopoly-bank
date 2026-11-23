package com.asd.monopoly_bank.service;

import com.asd.monopoly_bank.config.GameRulesProperties;
import com.asd.monopoly_bank.dto.CreateTransactionRequest;
import com.asd.monopoly_bank.dto.TransactionResponse;
import com.asd.monopoly_bank.exception.InvalidTransferException;
import com.asd.monopoly_bank.exception.ResourceNotFoundException;
import com.asd.monopoly_bank.model.Account;
import com.asd.monopoly_bank.model.Game;
import com.asd.monopoly_bank.model.Transaction;
import com.asd.monopoly_bank.repository.AccountRepository;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

	private final GameRepository gameRepository;
	private final AccountRepository accountRepository;
	private final TransactionRepository transactionRepository;
	private final GameRulesProperties gameRules;

	@Transactional
	public TransactionResponse transfer(
			Long gameId,
			CreateTransactionRequest request,
			UUID idempotencyKey) {
		Game game = gameRepository.findById(gameId)
				.orElseThrow(() -> ResourceNotFoundException.game(gameId));

		Long fromId = request.fromAccountId();
		Long toId = request.toAccountId();

		if (fromId.equals(toId)) {
			throw new InvalidTransferException("Payer and receiver cannot be the same account.");
		}

		// Lock both accounts in deterministic ascending ID order to prevent deadlocks
		List<Account> lockedAccounts = accountRepository.findAllByIdInOrderByIdForUpdate(
				List.of(fromId, toId));

		Map<Long, Account> accountsById = lockedAccounts.stream()
				.collect(Collectors.toMap(Account::getId, Function.identity()));

		Account fromAccount = resolveGameAccount(accountsById.get(fromId), fromId, gameId);
		Account toAccount = resolveGameAccount(accountsById.get(toId), toId, gameId);

		long amountCents = request.amountCents();
		gameRules.validateSufficientFunds(fromAccount, amountCents);

		fromAccount.setBalanceCents(Math.subtractExact(fromAccount.getBalanceCents(), amountCents));
		toAccount.setBalanceCents(Math.addExact(toAccount.getBalanceCents(), amountCents));

		Transaction transaction = Transaction.builder()
				.game(game)
				.fromAccount(fromAccount)
				.toAccount(toAccount)
				.amountCents(amountCents)
				.reason(request.reason().trim())
				.idempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID())
				.build();

		Transaction saved = transactionRepository.save(transaction);
		return TransactionResponse.from(saved);
	}

	private Account resolveGameAccount(Account account, Long accountId, Long expectedGameId) {
		if (account == null) {
			throw ResourceNotFoundException.account(accountId);
		}
		if (!account.getGame().getId().equals(expectedGameId)) {
			throw new InvalidTransferException(
					"Account #" + accountId + " does not belong to game #" + expectedGameId + ".");
		}
		return account;
	}
}
