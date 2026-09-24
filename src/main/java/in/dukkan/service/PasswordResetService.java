package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.PasswordResetToken;
import in.dukkan.repository.PasswordResetTokenRepository;
import in.dukkan.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CLAIM_TOKEN_ID = "tid";
    private static final String CLAIM_SECRET = "sec";
    private static final String PURPOSE = "password_reset";

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final MailDeliveryService mail;
    private final SecretKey signingKey;
    private final long ttlMinutes;
    private final String frontendBaseUrl;

    public PasswordResetService(
            UserRepository users,
            PasswordResetTokenRepository tokens,
            PasswordEncoder encoder,
            MailDeliveryService mail,
            @Value("${app.password-reset.secret}") String secret,
            @Value("${app.password-reset.ttl-minutes:45}") long ttlMinutes,
            @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mail = mail;
        this.signingKey = Keys.hmacShaKeyFor(pad(secret).getBytes(StandardCharsets.UTF_8));
        this.ttlMinutes = ttlMinutes;
        this.frontendBaseUrl = frontendBaseUrl.endsWith("/")
                ? frontendBaseUrl.substring(0, frontendBaseUrl.length() - 1)
                : frontendBaseUrl;
    }

    /**
     * Always completes without revealing whether the email exists. Creates a hashed token and
     * delivers (or logs) a signed reset link when the account is found.
     */
    @Transactional
    public void requestReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        Optional<AppUser> found = users.findByEmailIgnoreCase(normalized);
        if (found.isEmpty()) {
            log.info("Password reset requested for unknown email (no leak)");
            return;
        }
        AppUser user = found.get();
        Instant now = Instant.now();

        byte[] rawSecret = new byte[32];
        RANDOM.nextBytes(rawSecret);
        String secretHex = HexFormat.of().formatHex(rawSecret);
        String tokenId = Ids.next("prt");

        PasswordResetToken row = new PasswordResetToken();
        row.setId(tokenId);
        row.setUserId(user.getId());
        row.setTokenHash(sha256Hex(secretHex));
        row.setExpiresAt(now.plusSeconds(ttlMinutes * 60));
        row.setCreatedAt(now);
        tokens.save(row);

        String linkToken = Jwts.builder()
                .subject(PURPOSE)
                .claim(CLAIM_TOKEN_ID, tokenId)
                .claim(CLAIM_SECRET, secretHex)
                .issuedAt(Date.from(now))
                .expiration(Date.from(row.getExpiresAt()))
                .signWith(signingKey)
                .compact();

        String link = frontendBaseUrl + "/reset-password?token=" + linkToken;
        String body =
                """
                Reset your pinkCarrot password using this link (expires in %d minutes). \
                The link is single-use.

                %s

                If you did not request this, you can ignore this message.
                """
                        .formatted(ttlMinutes, link);

        boolean sent = mail.sendText(user.getEmail(), "Reset your pinkCarrot password", body);
        if (!sent) {
            log.info(
                    "Password reset link for user {} (dev — check MAIL log above): {}",
                    user.getId(),
                    link);
        }
    }

    @Transactional
    public void resetPassword(String linkToken, String newPassword) {
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(linkToken)
                    .getPayload();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
        }
        if (!PURPOSE.equals(claims.getSubject())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
        }
        String tokenId = claims.get(CLAIM_TOKEN_ID, String.class);
        String secretHex = claims.get(CLAIM_SECRET, String.class);
        if (tokenId == null || secretHex == null || secretHex.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
        }

        Instant now = Instant.now();
        PasswordResetToken row = tokens
                .findByIdAndTokenHash(tokenId, sha256Hex(secretHex))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid or expired reset link"));

        if (row.getUsedAt() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reset link already used");
        }
        if (!row.getExpiresAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link");
        }

        AppUser user = users.findById(row.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset link"));

        user.setPasswordHash(encoder.encode(newPassword.trim()));
        users.save(user);

        row.setUsedAt(now);
        tokens.save(row);
        tokens.markOutstandingUsed(user.getId(), row.getId(), now);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static String pad(String secret) {
        if (secret.length() >= 32) {
            return secret;
        }
        return secret + "0".repeat(32 - secret.length());
    }
}
