package com.asd.monopoly_bank.dto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.MediaType;

public enum ExportFormat {

	CSV("csv", new MediaType("text", "csv", StandardCharsets.UTF_8)),
	JSON("json", MediaType.APPLICATION_JSON);

	private final String extension;
	private final MediaType mediaType;

	ExportFormat(String extension, MediaType mediaType) {
		this.extension = extension;
		this.mediaType = mediaType;
	}

	public MediaType mediaType() {
		return mediaType;
	}

	public String filenameFor(Long gameId) {
		return "game-" + gameId + "-transactions." + extension;
	}

	public static ExportFormat fromParam(String raw) {
		String normalized = raw == null ? "" : raw.trim();
		return Arrays.stream(values())
				.filter(format -> format.extension.equalsIgnoreCase(normalized))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException(
						"Unsupported export format '" + raw + "'. Use 'csv' or 'json'."));
	}
}
