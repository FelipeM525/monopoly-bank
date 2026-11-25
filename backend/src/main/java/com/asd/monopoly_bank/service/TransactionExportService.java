package com.asd.monopoly_bank.service;

import com.asd.monopoly_bank.dto.TransactionExportRow;
import com.asd.monopoly_bank.exception.ResourceNotFoundException;
import com.asd.monopoly_bank.repository.GameRepository;
import com.asd.monopoly_bank.repository.TransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionExportService {

	private final GameRepository gameRepository;
	private final TransactionRepository transactionRepository;

	/** Returns the full ledger of a game in chronological order (oldest first). */
	@Transactional(readOnly = true)
	public List<TransactionExportRow> loadRows(Long gameId) {
		if (!gameRepository.existsById(gameId)) {
			throw ResourceNotFoundException.game(gameId);
		}
		return transactionRepository.findAllByGameIdOrderByCreatedAtAscIdAsc(gameId).stream()
				.map(TransactionExportRow::from)
				.toList();
	}

	public String toCsv(List<TransactionExportRow> rows) {
		return TransactionCsvWriter.write(rows);
	}
}
