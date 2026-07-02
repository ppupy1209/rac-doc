package com.yeonwoo.ragdoc.common;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(@NotBlank String question, Integer topK) {
}
