package io.simplicity.training.model.entity;

import io.simplicity.training.model.Emails;
import io.simplicity.training.model.enums.PlatformRole;
import io.simplicity.training.model.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppUser {

  @Id @GeneratedValue private UUID id;

  /**
   * Null until the person first authenticates. An administrator can invite an email address before
   * any Cognito account exists for it.
   */
  @Column(name = "cognito_sub", unique = true)
  private String cognitoSub;

  @Column(nullable = false, unique = true)
  private String email;

  /**
   * Catches every construction path, including the Lombok builder, which writes the field directly
   * and would otherwise bypass a setter.
   */
  @PrePersist
  @PreUpdate
  void normaliseEmail() {
    this.email = Emails.normalise(this.email);
  }

  @Column(name = "full_name")
  private String fullName;

  private String phone;

  @Column(name = "professional_role")
  private String professionalRole;

  /**
   * Which organisation this clinician is currently looking at. A preference only: membership is
   * checked on every request regardless, so a stale or hostile value grants nothing.
   */
  @Column(name = "active_org_id")
  private UUID activeOrgId;

  @Enumerated(EnumType.STRING)
  @Column(name = "platform_role", nullable = false)
  @Builder.Default
  private PlatformRole platformRole = PlatformRole.STANDARD;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  @Builder.Default
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "profile_completed", nullable = false)
  @Builder.Default
  private boolean profileCompleted = false;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
