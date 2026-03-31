package com.secureuser.secureuserapi.application.service;

import com.secureuser.secureuserapi.application.dto.RegisterRequest;
import com.secureuser.secureuserapi.application.dto.UserResponse;
import com.secureuser.secureuserapi.application.exception.DuplicateResourceException;
import com.secureuser.secureuserapi.application.mapper.UserMapper;
import com.secureuser.secureuserapi.domain.model.Role;
import com.secureuser.secureuserapi.domain.model.RoleName;
import com.secureuser.secureuserapi.domain.model.User;
import com.secureuser.secureuserapi.domain.repository.RoleRepository;
import com.secureuser.secureuserapi.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserRegistrationService userRegistrationService;

    private Role roleUser;
    private UUID savedUserId;

    @BeforeEach
    void setUp() {
        savedUserId = UUID.randomUUID();
        roleUser = Role.builder()
                .id(UUID.randomUUID())
                .name(RoleName.ROLE_USER)
                .build();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: happy path returns UserResponse with ROLE_USER assigned")
    void register_happyPath_returnsUserResponse() {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));

        User savedUser = User.builder()
                .id(savedUserId)
                .username("john_doe")
                .email("john@example.com")
                .password("$2a$encoded")
                .roles(Set.of(roleUser))
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponse expected = new UserResponse(savedUserId, "john_doe", "john@example.com", Set.of("ROLE_USER"));
        when(userMapper.toResponse(savedUser)).thenReturn(expected);

        UserResponse result = userRegistrationService.register(request);

        assertThat(result).isEqualTo(expected);
        assertThat(result.roles()).containsExactly("ROLE_USER");
    }

    @Test
    @DisplayName("register: email is trimmed and lowercased before persistence")
    void register_emailIsNormalised() {
        RegisterRequest request = new RegisterRequest("john_doe", "  JOHN@EXAMPLE.COM  ", "password123");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));

        User savedUser = User.builder()
                .id(savedUserId)
                .username("john_doe")
                .email("john@example.com")
                .password("$2a$encoded")
                .roles(Set.of(roleUser))
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any())).thenReturn(
                new UserResponse(savedUserId, "john_doe", "john@example.com", Set.of("ROLE_USER")));

        userRegistrationService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("john@example.com");
    }

    @Test
    @DisplayName("register: username is trimmed before persistence")
    void register_usernameIsTrimmed() {
        RegisterRequest request = new RegisterRequest("  john_doe  ", "john@example.com", "password123");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));

        User savedUser = User.builder()
                .id(savedUserId)
                .username("john_doe")
                .email("john@example.com")
                .password("$2a$encoded")
                .roles(Set.of(roleUser))
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any())).thenReturn(
                new UserResponse(savedUserId, "john_doe", "john@example.com", Set.of("ROLE_USER")));

        userRegistrationService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("john_doe");
    }

    @Test
    @DisplayName("register: password is encoded with BCrypt before persistence")
    void register_passwordIsEncoded() {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));

        User savedUser = User.builder()
                .id(savedUserId)
                .username("john_doe")
                .email("john@example.com")
                .password("$2a$encoded")
                .roles(Set.of(roleUser))
                .build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any())).thenReturn(
                new UserResponse(savedUserId, "john_doe", "john@example.com", Set.of("ROLE_USER")));

        userRegistrationService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$encoded");
        assertThat(captor.getValue().getPassword()).doesNotContain("password123");
    }

    @Test
    @DisplayName("register: raw password is never passed to save")
    void register_rawPasswordNeverPersisted() {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "supersecret");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("$2a$hashed");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));

        User savedUser = User.builder()
                .id(savedUserId).username("john_doe").email("john@example.com")
                .password("$2a$hashed").roles(Set.of(roleUser)).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any())).thenReturn(
                new UserResponse(savedUserId, "john_doe", "john@example.com", Set.of("ROLE_USER")));

        userRegistrationService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPassword()).doesNotContain("supersecret");
    }

    // ── Uniqueness violations ─────────────────────────────────────────────────

    @Test
    @DisplayName("register: throws DuplicateResourceException when username already taken")
    void register_duplicateUsername_throwsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest("existing_user", "new@example.com", "password123");

        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        assertThatThrownBy(() -> userRegistrationService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username is already taken");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("register: throws DuplicateResourceException when email already in use")
    void register_duplicateEmail_throwsDuplicateResourceException() {
        RegisterRequest request = new RegisterRequest("new_user", "existing@example.com", "password123");

        when(userRepository.existsByUsername("new_user")).thenReturn(false);
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userRegistrationService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Email is already in use");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    // ── Missing ROLE_USER in DB ───────────────────────────────────────────────

    @Test
    @DisplayName("register: throws IllegalStateException when ROLE_USER not found in database")
    void register_roleUserMissing_throwsIllegalStateException() {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userRegistrationService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Default role ROLE_USER not found. Ensure roles are seeded.");

        verify(userRepository, never()).save(any());
    }

    // ── Race condition ────────────────────────────────────────────────────────

    @Test
    @DisplayName("register: DataIntegrityViolationException from save() propagates (concurrent duplicate)")
    void register_raceCondition_propagatesDataIntegrityViolationException() {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));
        when(userRepository.save(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> userRegistrationService.register(request))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(userRepository, never()).findByUsername(anyString());
    }

    // ── Interaction verification ──────────────────────────────────────────────

    @Test
    @DisplayName("register: uniqueness checks use normalised values (trimmed username, lowercased email)")
    void register_uniquenessChecksUseNormalisedValues() {
        RegisterRequest request = new RegisterRequest("  John_Doe  ", "  JOHN@EXAMPLE.COM  ", "password123");

        when(userRepository.existsByUsername("John_Doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));

        User savedUser = User.builder()
                .id(savedUserId).username("John_Doe").email("john@example.com")
                .password("$2a$encoded").roles(Set.of(roleUser)).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any())).thenReturn(
                new UserResponse(savedUserId, "John_Doe", "john@example.com", Set.of("ROLE_USER")));

        userRegistrationService.register(request);

        verify(userRepository).existsByUsername("John_Doe");
        verify(userRepository).existsByEmail("john@example.com");
    }

    @Test
    @DisplayName("register: ROLE_USER is the only role assigned to new user")
    void register_onlyRoleUserAssigned() {
        RegisterRequest request = new RegisterRequest("john_doe", "john@example.com", "password123");

        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$encoded");
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(roleUser));

        User savedUser = User.builder()
                .id(savedUserId).username("john_doe").email("john@example.com")
                .password("$2a$encoded").roles(Set.of(roleUser)).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(userMapper.toResponse(any())).thenReturn(
                new UserResponse(savedUserId, "john_doe", "john@example.com", Set.of("ROLE_USER")));

        userRegistrationService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRoles()).containsExactly(roleUser);
        verify(roleRepository, never()).findByName(RoleName.ROLE_ADMIN);
    }
}
