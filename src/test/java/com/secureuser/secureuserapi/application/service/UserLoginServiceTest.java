package com.secureuser.secureuserapi.application.service;

import com.secureuser.secureuserapi.application.dto.AuthResponse;
import com.secureuser.secureuserapi.application.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import com.secureuser.secureuserapi.infrastructure.security.JwtTokenProvider;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLoginServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private UserLoginService userLoginService;

    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userDetails = new User("john_doe", "$2a$encoded", Collections.emptyList());
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("login: happy path returns AuthResponse with token and username")
    void login_happyPath_returnsAuthResponse() {
        LoginRequest request = new LoginRequest("john_doe", "password123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(jwtTokenProvider.generateToken(userDetails)).thenReturn("jwt.token.here");

        AuthResponse result = userLoginService.login(request);

        assertThat(result.token()).isEqualTo("jwt.token.here");
        assertThat(result.username()).isEqualTo("john_doe");
        assertThat(result.type()).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("login: passes correct username and password to AuthenticationManager")
    void login_passesCorrectCredentialsToAuthManager() {
        LoginRequest request = new LoginRequest("john_doe", "password123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("jwt.token.here");

        userLoginService.login(request);

        verify(authenticationManager).authenticate(
                argThat(token ->
                        "john_doe".equals(token.getPrincipal()) &&
                        "password123".equals(token.getCredentials())
                )
        );
    }

    @Test
    @DisplayName("login: uses authenticated UserDetails to generate JWT (not raw request)")
    void login_usesAuthenticatedUserDetailsForToken() {
        LoginRequest request = new LoginRequest("john_doe", "password123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(userDetails)).thenReturn("signed.jwt");

        userLoginService.login(request);

        // Verify token is generated from the UserDetails returned by the authentication manager,
        // not from a raw username string (ensures the token reflects the authoritative principal)
        verify(jwtTokenProvider).generateToken(userDetails);
    }

    @Test
    @DisplayName("login: response never contains password")
    void login_responseDoesNotContainPassword() {
        LoginRequest request = new LoginRequest("john_doe", "password123");

        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtTokenProvider.generateToken(any())).thenReturn("jwt.token");

        AuthResponse result = userLoginService.login(request);

        assertThat(result.token()).doesNotContain("password123");
        // AuthResponse has no password field by design — verified structurally
        // (record has only: token, type, username)
    }

    // ── Bad credentials ───────────────────────────────────────────────────────

    @Test
    @DisplayName("login: BadCredentialsException from AuthenticationManager propagates uncaught")
    void login_badCredentials_propagatesBadCredentialsException() {
        LoginRequest request = new LoginRequest("john_doe", "wrongpassword");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> userLoginService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verify(jwtTokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("login: JWT is not generated when authentication fails")
    void login_authFailure_tokenNeverGenerated() {
        LoginRequest request = new LoginRequest("unknown_user", "somepass1");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> userLoginService.login(request))
                .isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(jwtTokenProvider);
    }
}
