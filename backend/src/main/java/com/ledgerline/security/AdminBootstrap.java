package com.ledgerline.security;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ledgerline.domain.UserAccount;
import com.ledgerline.domain.UserRole;
import com.ledgerline.repository.UserAccountRepository;

@Component
public class AdminBootstrap implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public AdminBootstrap(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Environment environment) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        String username = environment.getProperty("BOOTSTRAP_ADMIN_USERNAME");
        String password = environment.getProperty("BOOTSTRAP_ADMIN_PASSWORD");

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }

        if (userAccountRepository.findByUsername(username.trim()).isPresent()) {
            return;
        }

        userAccountRepository.save(new UserAccount(
                username.trim(),
                passwordEncoder.encode(password),
                UserRole.ADMIN
        ));
    }
}
