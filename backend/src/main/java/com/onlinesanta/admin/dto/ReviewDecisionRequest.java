package com.onlinesanta.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * 審核決定的附註。
 *
 * <p>退件時特別重要——機構得知道要補什麼才能重新送審。
 */
public record ReviewDecisionRequest(
        @Size(max = 1000, message = "審核意見不可超過 1000 字")
        String note) {

    public static ReviewDecisionRequest empty() {
        return new ReviewDecisionRequest(null);
    }
}
