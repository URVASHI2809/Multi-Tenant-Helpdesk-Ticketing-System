package com.helpdesk.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleStatus(ResponseStatusException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(),
                exception.getReason() == null ? "Request failed" : exception.getReason());
        problem.setTitle(HttpStatus.valueOf(exception.getStatusCode().value()).getReasonPhrase());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message",
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .toList());
        return problem;
    }
}