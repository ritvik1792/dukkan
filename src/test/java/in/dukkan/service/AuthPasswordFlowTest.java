package in.dukkan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.PasswordResetToken;
import in.dukkan.domain.Role;
import in.dukkan.repository.PasswordResetTokenRepository;
import in.dukkan.repository.UserRepository;
import in.dukkan.security.JwtService;
import in.dukkan.web.dto.AuthDtos.SignupRequest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthPasswordFlowTest {

    private static final String RESET_SECRET = "test-password-reset-secret-32chars!!";

    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock JwtService jwt;
    @Mock MailDeliveryService mail;

    PasswordEncoder encoder = new BCryptPasswordEncoder();
    AuthService auth;
    PasswordResetService passwordReset;

    @BeforeEach
    void setUp() {
        auth = new AuthService(users, encoder, jwt);
        passwordReset = new PasswordResetService(
                users, tokens, encoder, mail, RESET_SECRET, 45, "http://localhost:3000");
        when(jwt.createToken(anyString(), anyString())).thenReturn("jwt-token");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(inv -> inv.getArgument(0));
        when(mail.sendText(anyString(), anyString(), anyString())).thenReturn(false);
    }

    @Test
    void signupRequiresPhoneEmailPasswordWithoutOtpAndLeavesPhoneUnverified() {
        when(users.existsByEmailIgnoreCase("buyer@example.com")).thenReturn(false);

        var response = auth.signup(
                new SignupRequest("Buyer", "buyer@example.com", "9876543210", "secret12"));

        assertEquals("jwt-token", response.token());
        assertEquals("buyer@example.com", response.user().email());
        assertEquals("9876543210", response.user().phone());

        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(users).save(captor.capture());
        AppUser saved = captor.getValue();
        assertNull(saved.getPhoneVerifiedAt());
        assertTrue(encoder.matches("secret12", saved.getPasswordHash()));
        assertNotEquals("secret12", saved.getPasswordHash());
    }

    @Test
    void signupRejectsInvalidPhone() {
        assertThrows(
                ResponseStatusException.class,
                () -> auth.signup(new SignupRequest("Buyer", "buyer@example.com", "123", "secret12")));
        verify(users, never()).save(any());
    }

    @Test
    void forgotPasswordDoesNotRevealUnknownEmail() {
        when(users.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        passwordReset.requestReset("missing@example.com");

        verify(tokens, never()).save(any());
        verify(mail, never()).sendText(anyString(), anyString(), anyString());
    }

    @Test
    void resetRejectsTamperedToken() {
        assertThrows(
                ResponseStatusException.class,
                () -> passwordReset.resetPassword("not-a-real-jwt", "newpass1"));
    }

    @Test
    void resetRejectsExpiredAndReusedTokensThenAcceptsFreshLink() {
        AppUser user = new AppUser();
        user.setId("u-1");
        user.setEmail("buyer@example.com");
        user.setName("Buyer");
        user.setRole(Role.BUYER);
        user.setPasswordHash(encoder.encode("oldpass1"));
        when(users.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(user));
        when(users.findById("u-1")).thenReturn(Optional.of(user));

        AtomicReference<PasswordResetToken> stored = new AtomicReference<>();
        when(tokens.save(any(PasswordResetToken.class))).thenAnswer(inv -> {
            PasswordResetToken row = inv.getArgument(0);
            stored.set(row);
            return row;
        });
        when(tokens.findByIdAndTokenHash(anyString(), anyString())).thenAnswer(inv -> {
            PasswordResetToken row = stored.get();
            if (row == null) return Optional.empty();
            if (!row.getId().equals(inv.getArgument(0))) return Optional.empty();
            if (!row.getTokenHash().equals(inv.getArgument(1))) return Optional.empty();
            return Optional.of(row);
        });

        AtomicReference<String> capturedLink = new AtomicReference<>();
        when(mail.sendText(eq("buyer@example.com"), anyString(), anyString())).thenAnswer(inv -> {
            String body = inv.getArgument(2);
            int idx = body.indexOf("http://localhost:3000/reset-password?token=");
            assertTrue(idx >= 0);
            String rest = body.substring(idx + "http://localhost:3000/reset-password?token=".length());
            String token = rest.split("\\s+")[0].trim();
            capturedLink.set(token);
            return false;
        });

        passwordReset.requestReset("buyer@example.com");
        String linkToken = capturedLink.get();
        assertNotNull(linkToken);

        // Expire the stored row
        stored.get().setExpiresAt(Instant.now().minusSeconds(60));
        assertThrows(ResponseStatusException.class, () -> passwordReset.resetPassword(linkToken, "newpass1"));

        // Fresh token
        passwordReset.requestReset("buyer@example.com");
        String freshToken = capturedLink.get();
        passwordReset.resetPassword(freshToken, "newpass1");
        assertTrue(encoder.matches("newpass1", user.getPasswordHash()));
        assertNotNull(stored.get().getUsedAt());

        assertThrows(ResponseStatusException.class, () -> passwordReset.resetPassword(freshToken, "another1"));
    }

    @Test
    void forgedJwtWithWrongSecretIsRejected() {
        SecretKey other = Keys.hmacShaKeyFor(
                "totally-different-secret-key-32b!".getBytes(StandardCharsets.UTF_8));
        String forged = Jwts.builder()
                .subject("password_reset")
                .claim("tid", "prt-fake")
                .claim("sec", "deadbeef")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(other)
                .compact();
        assertThrows(ResponseStatusException.class, () -> passwordReset.resetPassword(forged, "newpass1"));
    }

    @Test
    void forgotPasswordForKnownUserCreatesHashedTokenNotPlaintext() {
        AppUser user = new AppUser();
        user.setId("u-2");
        user.setEmail("known@example.com");
        user.setName("Known");
        user.setRole(Role.BUYER);
        user.setPasswordHash(encoder.encode("oldpass1"));
        when(users.findByEmailIgnoreCase("known@example.com")).thenReturn(Optional.of(user));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        passwordReset.requestReset("known@example.com");
        verify(tokens).save(captor.capture());
        PasswordResetToken row = captor.getValue();
        assertEquals(64, row.getTokenHash().length());
        assertFalse(row.getTokenHash().contains(" "));
        assertNull(row.getUsedAt());
        verify(mail).sendText(eq("known@example.com"), anyString(), anyString());
    }
}
