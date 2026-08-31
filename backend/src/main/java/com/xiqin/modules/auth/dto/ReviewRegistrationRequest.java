package com.xiqin.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReviewRegistrationRequest {
    @NotBlank
    private String action; // approve / reject
    private String reviewNote;
}
