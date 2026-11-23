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

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransactionService {

    private final GameRepository gameRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final GameRulesProperties gameRules;
    private final TransactionTemplate requiresNewTx;

    public TransactionService(
            GameRepository gameRepository,
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            GameRulesProperties gameRules,
            PlatformTransactionManager transactionManager) {
        this.gameRepository = gameRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.gameRules = gameRules;
        this.requiresNewTx = createRequiresNewTemplate(transactionManager);
    }

    public record TransferResult(TransactionResponse transaction, boolean created) {
    }

    private record LockedTransferAccounts(Account from, Account to) {
    }

    public TransferResult transfer(
            Long gameId,
            CreateTransactionRequest request,
            UUID idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);

        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> toReplayedResult(existing, gameId, request))
                .orElseGet(() -> executeWithRaceRecovery(gameId, request, idempotencyKey));
    }

    private TransferResult executeWithRaceRecovery(
            Long gameId,
            CreateTransactionRequest request,
            UUID idempotencyKey) {
        try {
            return requiresNewTx.execute(
                    status -> executeAtomicTransfer(gameId, request, idempotencyKey));
        } catch (DataIntegrityViolationException ex) {
            return transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .map(winner -> toReplayedResult(winner, gameId, request))
                    .orElseThrow(() -> ex);
        }
    }

    private TransferResult executeAtomicTransfer(
            Long gameId,
            CreateTransactionRequest request,
            UUID idempotencyKey) {
        Game game = findGameOrThrow(gameId);
        LockedTransferAccounts accounts = lockAccountsInOrder(
                gameId, request.fromAccountId(), request.toAccountId());

        return transactionRepository.findByIdempotencyKey(idempotencyKey)
                .map(existing -> toReplayedResult(existing, gameId, request))
                .orElseGet(() -> debitCreditAndRecord(game, accounts, request, idempotencyKey));
    }

    private LockedTransferAccounts lockAccountsInOrder(Long gameId, Long fromId, Long toId) {
        if (fromId.equals(toId)) {
            throw new InvalidTransferException("Payer and receiver cannot be the same account.");
        }

        Map<Long, Account> lockedById = accountRepository
                .findAllByIdInOrderByIdForUpdate(List.of(fromId, toId))
                .stream()
                .collect(Collectors.toMap(Account::getId, Function.identity()));

        Account from = resolveGameAccount(lockedById.get(fromId), fromId, gameId);
        Account to = resolveGameAccount(lockedById.get(toId), toId, gameId);
        return new LockedTransferAccounts(from, to);
    }

    private TransferResult debitCreditAndRecord(
            Game game,
            LockedTransferAccounts accounts,
            CreateTransactionRequest request,
            UUID idempotencyKey) {
        long amountCents = request.amountCents();
        gameRules.validateSufficientFunds(accounts.from(), amountCents);

        accounts.from().setBalanceCents(Math.subtractExact(accounts.from().getBalanceCents(), amountCents));
        accounts.to().setBalanceCents(Math.addExact(accounts.to().getBalanceCents(), amountCents));

        Transaction saved = transactionRepository.saveAndFlush(Transaction.builder()
                .game(game)
                .fromAccount(accounts.from())
                .toAccount(accounts.to())
                .amountCents(amountCents)
                .reason(request.reason().trim())
                .idempotencyKey(idempotencyKey)
                .build());

        return new TransferResult(TransactionResponse.from(saved), true);
    }

    private TransferResult toReplayedResult(
            Transaction existing,
            Long gameId,
            CreateTransactionRequest request) {
        if (!matchesRequest(existing, gameId, request)) {
            throw new InvalidTransferException(
                    "Idempotency-Key '" + existing.getIdempotencyKey()
                            + "' was already used with different transfer parameters.");
        }
        return new TransferResult(TransactionResponse.from(existing), false);
    }

    private boolean matchesRequest(
            Transaction existing,
            Long gameId,
            CreateTransactionRequest request) {
        return existing.getGame().getId().equals(gameId)
                && existing.getFromAccount().getId().equals(request.fromAccountId())
                && existing.getToAccount().getId().equals(request.toAccountId())
                && existing.getAmountCents() == request.amountCents()
                && existing.getReason().equals(request.reason().trim());
    }

    private Game findGameOrThrow(Long gameId) {
        return gameRepository.findById(gameId)
                .orElseThrow(() -> ResourceNotFoundException.game(gameId));
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

    private static void requireIdempotencyKey(UUID idempotencyKey) {
        if (idempotencyKey == null) {
            throw new IllegalArgumentException("Required header 'Idempotency-Key' is missing.");
        }
    }

    private static TransactionTemplate createRequiresNewTemplate(PlatformTransactionManager txManager) {
        TransactionTemplate template = new TransactionTemplate(txManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }
}
