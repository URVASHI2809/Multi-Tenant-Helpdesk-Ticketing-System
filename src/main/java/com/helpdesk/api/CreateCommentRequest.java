package com.helpdesk.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(@NotBlank @Size(max = 4000) String body, Boolean internal) {
	public CreateCommentRequest {
		internal = Boolean.TRUE.equals(internal);
	}
}