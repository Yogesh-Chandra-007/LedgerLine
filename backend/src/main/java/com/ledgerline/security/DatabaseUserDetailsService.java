package com.ledgerline.security;

import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ledgerline.domain.UserAccount;
import com.ledgerline.repository.UserAccountRepository;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository userAccountRepository;

    public DatabaseUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        UserAccount account = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        return User.withUsername(account.getUsername())
                .password(account.getPasswordHash())
                .authorities(account.getRole().authority())
                .disabled(!account.isEnabled())
                .build();
    }
}
