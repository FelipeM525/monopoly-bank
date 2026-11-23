package com.asd.monopoly_bank.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(ResourceNotFoundException.class)
	public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
		return buildProblem(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage(), request);
	}

	@ExceptionHandler(DuplicatePlayerException.class)
	public ProblemDetail handleDuplicatePlayer(DuplicatePlayerException ex, HttpServletRequest request) {
		return buildProblem(HttpStatus.CONFLICT, "Duplicate Player or Pawn Color", ex.getMessage(), request);
	}

	@ExceptionHandler(InsufficientFundsException.class)
	public ProblemDetail handleInsufficientFunds(InsufficientFundsException ex, HttpServletRequest request) {
		ProblemDetail problem = buildProblem(
				HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient Funds", ex.getMessage(), request);
		problem.setProperty("payerName", ex.getPayerName());
		problem.setProperty("availableCents", ex.getAvailableCents());
		problem.setProperty("requestedCents", ex.getRequestedCents());
		return problem;
	}

	@ExceptionHandler(InvalidTransferException.class)
	public ProblemDetail handleInvalidTransfer(InvalidTransferException ex, HttpServletRequest request) {
		return buildProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid Transfer", ex.getMessage(), request);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();

		ex.getFieldErrors().forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

		ProblemDetail problem = buildProblem(
				HttpStatus.BAD_REQUEST,
				"Validation Failed",
				"One or more fields are invalid. Check the errors map for details.",
				request);
		problem.setProperty("errors", fieldErrors);
		return problem;
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ProblemDetail handleConstraintViolation(
			ConstraintViolationException ex, HttpServletRequest request) {
		Map<String, String> fieldErrors = new LinkedHashMap<>();
		ex.getConstraintViolations().forEach(v -> fieldErrors.putIfAbsent(
				v.getPropertyPath().toString(), v.getMessage()));

		ProblemDetail problem = buildProblem(
				HttpStatus.BAD_REQUEST,
				"Validation Failed",
				"One or more request parameters are invalid.",
				request);
		problem.setProperty("errors", fieldErrors);
		return problem;
	}

	@ExceptionHandler({
		MissingRequestHeaderException.class,
		MethodArgumentTypeMismatchException.class,
		HttpMessageNotReadableException.class,
		IllegalArgumentException.class
	})
	public ProblemDetail handleBadRequest(Exception ex, HttpServletRequest request) {
		String detail = switch (ex) {
			case MissingRequestHeaderException missingHeader ->
					"Required header '" + missingHeader.getHeaderName() + "' is missing.";
			case MethodArgumentTypeMismatchException mismatch ->
					"Invalid value for '" + mismatch.getName() + "'.";
			case HttpMessageNotReadableException ignored ->
					"Malformed JSON request body or invalid enum value.";
			default -> ex.getMessage();
		};
		return buildProblem(HttpStatus.BAD_REQUEST, "Bad Request", detail, request);
	}

	private ProblemDetail buildProblem(
			HttpStatus status, String title, String detail, HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("timestamp", Instant.now());
		return problem;
	}
}
