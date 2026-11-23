package com.asd.monopoly_bank.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PagedResponse<T>(
		List<T> items,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean hasNext) {

	public static <E, T> PagedResponse<T> from(Page<E> pageResult, Function<E, T> mapper) {
		List<T> mapped = pageResult.getContent().stream().map(mapper).toList();
		return new PagedResponse<>(
				mapped,
				pageResult.getNumber(),
				pageResult.getSize(),
				pageResult.getTotalElements(),
				pageResult.getTotalPages(),
				pageResult.hasNext());
	}
}
