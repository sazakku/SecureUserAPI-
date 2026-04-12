package com.secureuser.secureuserapi.application.port.out;

import org.springframework.security.core.userdetails.UserDetails;

/**
 * Outbound port: token generation capability.
 *
 * Defined in the application layer so services depend on this abstraction
 * rather than on the concrete JwtTokenProvider in the infrastructure layer.
 * This preserves the hexagonal architecture rule that the application layer
 * must not import from infrastructure.
 */
public interface TokenProvider {
    String generateToken(UserDetails userDetails);
}
