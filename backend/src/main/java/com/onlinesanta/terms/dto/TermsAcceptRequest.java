package com.onlinesanta.terms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param version 使用者在畫面上看到並同意的版本（前端打包的須知版本） */
public record TermsAcceptRequest(@NotBlank @Size(max = 50) String version) {
}
