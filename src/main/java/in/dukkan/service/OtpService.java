package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.OtpChallenge;
import in.dukkan.domain.Role;
import in.dukkan.repository.OtpChallengeRepository;
import in.dukkan.repository.UserRepository;
import in.dukkan.security.JwtService;
import in.dukkan.web.dto.AuthDtos.AuthResponse;
import in.dukkan.web.dto.AuthDtos.OtpRequest;
import in.dukkan.web.dto.AuthDtos.OtpRequestResponse;
import in.dukkan.web.dto.AuthDtos.OtpVerifyRequest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository challenges;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final boolean exposeDevCode;
    private final long ttlSeconds;
    private final long minResendSeconds;

    public OtpService(
            OtpChallengeRepository challenges,
            UserRepository users,
            PasswordEncoder encoder,
            JwtService jwt,
            @Value("${app.otp.expose-dev-code:false}") boolean exposeDevCode,
            @Value("${app.otp.ttl-seconds:300}") long ttlSeconds,
            @Value("${app.otp.min-resend-seconds:20}") long minResendSeconds) {
        this.challenges = challenges;
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.exposeDevCode = exposeDevCode;
        this.ttlSeconds = ttlSeconds;
        this.minResendSeconds = minResendSeconds;
    }

    @Transactional
    public OtpRequestResponse request(OtpRequest request) {
        String destination = destination(request.phone(), request.email());
        String purpose = purpose(request.purpose());
        Instant now = Instant.now();
        List<OtpChallenge> active =
                challenges.findByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        destination, purpose);
        if (!active.isEmpty()) {
            OtpChallenge latest = active.get(0);
            if (latest.getCreatedAt().plusSeconds(minResendSeconds).isAfter(now)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS, "Wait before requesting another code");
            }
            challenges.deleteAll(active);
        }

        String code = String.format(Locale.ROOT, "%04d", RANDOM.nextInt(10000));
        OtpChallenge challenge = new OtpChallenge();
        challenge.setId(Ids.next("otp"));
        challenge.setDestination(destination);
        challenge.setCodeHash(encoder.encode(code));
        challenge.setExpiresAt(now.plusSeconds(ttlSeconds));
        challenge.setPurpose(purpose);
        challenge.setCreatedAt(now);
        challenges.save(challenge);
        log.info("OTP for {} ({}) is {}", destination, purpose, code);
        return new OtpRequestResponse(
                destination, purpose, challenge.getExpiresAt(), exposeDevCode ? code : null);
    }

    @Transactional
    public AuthResponse verify(OtpVerifyRequest request) {
        String destination = destination(request.phone(), request.email());
        String purpose = purpose(request.purpose());
        String code = request.code() == null ? "" : request.code().trim();
        if (!code.matches("\\d{4}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the 4-digit OTP");
        }
        Instant now = Instant.now();
        List<OtpChallenge> active =
                challenges.findByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                        destination, purpose);
        OtpChallenge match = active.stream()
                .filter(item -> item.getExpiresAt().isAfter(now) && encoder.matches(code, item.getCodeHash()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired OTP"));
        match.setConsumedAt(now);
        challenges.save(match);
        active.stream()
                .filter(item -> !item.getId().equals(match.getId()))
                .forEach(challenges::delete);

        AppUser user = resolveUser(request.phone(), request.email(), destination);
        return new AuthResponse(jwt.createToken(user.getId(), user.getRole().name()), AuthService.toUser(user));
    }

    private AppUser resolveUser(String phone, String email, String destination) {
        if (phone != null && !phone.isBlank()) {
            String normalized = normalizePhone(phone);
            return users.findFirstByPhone(normalized).orElseGet(() -> createPhoneUser(normalized));
        }
        String normalizedEmail = destination;
        return users.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No account for this email. Sign up first."));
    }

    private AppUser createPhoneUser(String phone) {
        AppUser user = new AppUser();
        user.setId(Ids.next("u"));
        user.setName("User " + phone.substring(Math.max(0, phone.length() - 4)));
        user.setEmail(phone + "@phone.dukkan");
        user.setPhone(phone);
        user.setPasswordHash(encoder.encode("phone-" + Ids.next("pw")));
        user.setRole(Role.BUYER);
        return users.save(user);
    }

    private static String destination(String phone, String email) {
        if (phone != null && !phone.isBlank()) {
            return normalizePhone(phone);
        }
        if (email != null && !email.isBlank()) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone or email is required");
    }

    private static String purpose(String purpose) {
        return purpose == null || purpose.isBlank() ? "login" : purpose.trim().toLowerCase(Locale.ROOT);
    }

    static String normalizePhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            return digits.substring(2);
        }
        if (digits.length() == 11 && digits.startsWith("0")) {
            return digits.substring(1);
        }
        return digits;
    }
}
