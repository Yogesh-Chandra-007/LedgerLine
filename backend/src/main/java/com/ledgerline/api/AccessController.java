package com.ledgerline.api;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccessController {
    @GetMapping("/access/me")
    public Map<String, Object> currentUser(Authentication authentication) {
        return Map.of(
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities().stream()
                        .map(authority -> authority.getAuthority()).sorted().toList()
        );
    }

    @GetMapping("/admin/status")
    public Map<String, String> adminStatus() {
        return Map.of("access", "ADMIN", "status", "AUTHORIZED");
    }

    @GetMapping("/auditor/status")
    public Map<String, String> auditorStatus() {
        return Map.of("access", "AUDITOR", "status", "AUTHORIZED");
    }

    @GetMapping("/account-holder/status")
    public Map<String, String> accountHolderStatus() {
        return Map.of("access", "ACCOUNT_HOLDER", "status", "AUTHORIZED");
    }
}
