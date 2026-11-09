package com.ledgerline.api;

import com.ledgerline.domain.UserAccount;
import com.ledgerline.domain.UserRole;
import com.ledgerline.repository.UserAccountRepository;
import com.ledgerline.service.AuditEventService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditEventService auditEvents;

    public AdminUserController(UserAccountRepository users, PasswordEncoder passwordEncoder,
                               AuditEventService auditEvents) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditEvents = auditEvents;
    }

    @GetMapping
    public List<UserSummary> listUsers() {
        return users.findAllByOrderByCreatedAtDesc().stream()
                .map(user -> new UserSummary(user.getUsername(), user.getRole().name(),
                        user.isEnabled(), user.getCreatedAt().toString()))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public CreatedUser create(Authentication authentication, @Valid @RequestBody CreateUserRequest request) {
        String username = request.username().trim();
        if (users.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already registered");
        }
        UserRole role;
        try {
            role = UserRole.valueOf(request.role().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported user role");
        }
        if (role == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Additional administrator accounts require a separate provisioning procedure");
        }
        UserAccount saved = users.save(new UserAccount(username,
                passwordEncoder.encode(request.password()), role));
        auditEvents.recordSuccess(authentication.getName(), "USER_CREATED", "USER", username,
                null, "Role=" + role.name());
        return new CreatedUser(saved.getUsername(), saved.getRole().name(), saved.isEnabled());
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 80) @Pattern(regexp = "^[A-Za-z0-9._-]+$") String username,
            @NotBlank @Pattern(regexp = "^[!-~]{12,72}$", message = "Password must be 12-72 printable ASCII characters") String password,
            @NotBlank String role) {}
    public record CreatedUser(String username, String role, boolean enabled) {}
    public record UserSummary(String username, String role, boolean enabled, String createdAt) {}
}
