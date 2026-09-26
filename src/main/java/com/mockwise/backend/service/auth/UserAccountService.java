package com.mockwise.backend.service.auth;

import com.mockwise.backend.config.SupabaseUser;
import com.mockwise.backend.exception.ForbiddenException;
import com.mockwise.backend.repository.auth.AppUser;
import com.mockwise.backend.repository.auth.AppUserRepository;
import com.mockwise.backend.repository.auth.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    public static final String ADMIN_EMAIL = "sankalp.29bhagwat@gmail.com";

    private final AppUserRepository appUserRepository;

    @Transactional
    public AppUser ensure(SupabaseUser user) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            throw new ForbiddenException("A signed-in email is required.");
        }
        String email = user.getEmail().trim().toLowerCase();
        AppUser account = appUserRepository.findByEmailIgnoreCase(email).orElse(null);
        if (account == null) {
            account = new AppUser();
            account.setEmail(email);
            account.setExternalUserId(user.getId());
            account.setRole(ADMIN_EMAIL.equals(email) ? UserRole.ADMIN : UserRole.USER);
            return appUserRepository.save(account);
        }
        if (account.getExternalUserId() == null && user.getId() != null) {
            account.setExternalUserId(user.getId());
        }
        if (ADMIN_EMAIL.equals(email) && account.getRole() != UserRole.ADMIN) {
            account.setRole(UserRole.ADMIN);
        }
        return appUserRepository.save(account);
    }

    @Transactional
    public void requireAdmin(SupabaseUser user) {
        AppUser account = ensure(user);
        if (account.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Admin access is required.");
        }
    }
}
