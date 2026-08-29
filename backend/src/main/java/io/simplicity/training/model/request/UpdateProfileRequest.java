package io.simplicity.training.model.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The details gathered by the profile wizard immediately after a clinician's first sign-in. */
public record UpdateProfileRequest(
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 40)
        @Pattern(
            regexp = "^$|^[+0-9][0-9 ()\\-]{4,}$",
            message = "must be a plausible phone number")
        String phone,
    @NotBlank @Size(max = 120) String professionalRole) {}
