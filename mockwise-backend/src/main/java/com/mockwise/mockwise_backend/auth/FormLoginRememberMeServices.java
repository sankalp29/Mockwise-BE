package com.mockwise.mockwise_backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;

/**
 * Remember-me that applies only to form-login (local) and ignores OAuth2 logins.
 */
public class FormLoginRememberMeServices extends TokenBasedRememberMeServices {
    public FormLoginRememberMeServices(String key, AppUserDetailsService userDetailsService) {
        super(key, userDetailsService);
    }

    @Override
    public void loginSuccess(HttpServletRequest request, HttpServletResponse response, Authentication successfulAuthentication) {
        if (successfulAuthentication instanceof OAuth2AuthenticationToken) {
            // Do not set/clear remember-me cookies for OAuth logins
            return;
        }
        super.loginSuccess(request, response, successfulAuthentication);
    }

    @Override
    public void loginFail(HttpServletRequest request, HttpServletResponse response) {
        String uri = request.getRequestURI();
        if (uri != null && (uri.startsWith("/login/oauth2/") || uri.startsWith("/oauth2/"))) {
            // Ignore remember-me failure handling for OAuth2 endpoints
            return;
        }
        super.loginFail(request, response);
    }
}


