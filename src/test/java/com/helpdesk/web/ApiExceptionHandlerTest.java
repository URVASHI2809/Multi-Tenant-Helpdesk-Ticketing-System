package com.helpdesk.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void statusErrorWithoutReasonUsesFallbackDetail() {
        ProblemDetail problem = handler.handleStatus(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getDetail()).isEqualTo("Request failed");
        assertThat(problem.getTitle()).isEqualTo("Not Found");
    }

    @Test
    void validationErrorWithoutDefaultMessageUsesFallbackText() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "subject", "bad value", false,
                null, null, null));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);

        ProblemDetail problem = handler.handleValidation(exception);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getProperties().get("errors")).isEqualTo(
                List.of(Map.of("field", "subject", "message", "Invalid value")));
    }
}