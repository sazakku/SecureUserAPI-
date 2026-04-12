package com.secureuser.secureuserapi.application.service;

import com.secureuser.secureuserapi.application.dto.AuthResponse;
import com.secureuser.secureuserapi.application.dto.LoginRequest;
import com.secureuser.secureuserapi.application.port.out.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserLoginService {

    private final AuthenticationManager authenticationManager;
    // TokenProvider is an application-layer port — no infrastructure import needed
    private final TokenProvider tokenProvider;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = tokenProvider.generateToken(userDetails);

        return AuthResponse.of(token, userDetails.getUsername());
    }
}
