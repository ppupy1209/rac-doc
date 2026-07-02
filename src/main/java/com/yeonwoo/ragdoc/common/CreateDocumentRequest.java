package com.yeonwoo.ragdoc.common;

import jakarta.validation.constraints.NotBlank;

public record CreateDocumentRequest(@NotBlank String title, @NotBlank String content) {
}
