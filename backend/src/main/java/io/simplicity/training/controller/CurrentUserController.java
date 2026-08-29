package io.simplicity.training.controller;

import io.simplicity.training.model.response.CurrentUserResponse;
import io.simplicity.training.security.CurrentPrincipal;
import io.simplicity.training.service.CurrentUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Current user", description = "The signed-in clinician's own profile and memberships")
public class CurrentUserController {

  private final CurrentUserService currentUserService;

  @GetMapping
  @Operation(
      summary = "Describe the signed-in user",
      description =
          "Provisions the user on first call. Clients use profileCompleted and the organisations "
              + "list to decide whether to show onboarding.")
  public CurrentUserResponse me() {
    return currentUserService.describe(CurrentPrincipal.require());
  }
}
