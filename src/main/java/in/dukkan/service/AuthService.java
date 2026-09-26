package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Role;
import in.dukkan.repository.UserRepository;
import in.dukkan.security.JwtService;
import in.dukkan.service.SellerOnboardingService.SellerIntent;
import in.dukkan.web.dto.AuthDtos.AuthResponse;
import in.dukkan.web.dto.AuthDtos.LoginRequest;
import in.dukkan.web.dto.AuthDtos.SignupRequest;
import in.dukkan.web.dto.AuthDtos.UpdateProfileRequest;
import in.dukkan.web.dto.AuthDtos.UserResponse;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final SellerOnboardingService onboarding;

    public AuthService(
            UserRepository users, PasswordEncoder encoder, JwtService jwt, SellerOnboardingService onboarding) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.onboarding = onboarding;
    }

    public AuthResponse login(LoginRequest request) {
        // Match signup/password-reset normalization so padded email/password still authenticate.
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        String password = request.password() == null ? "" : request.password().trim();
        AppUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (password.isEmpty() || !encoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return toAuth(user);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = request.email() == null ? "" : request.email().trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        }
        String phone = OtpService.normalizePhone(request.phone() == null ? "" : request.phone());
        if (!phone.matches("^[6-9]\\d{9}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Enter a valid 10-digit Indian mobile number");
        }
        if (request.password() == null || request.password().trim().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This email already has an account. Sign in to it. Signup does not replace or delete it.");
        }
        if (users.findFirstByPhone(phone).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This mobile number already has an account. Sign in to it. Signup does not replace or delete it.");
        }
        SellerIntent intent = intentFromSignup(request);
        boolean selling = SellerOnboardingService.wantsSellerAccount(intent);
        AppUser user = new AppUser();
        user.setId(Ids.next("u"));
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPhone(phone);
        // Phone verification is deferred; phone_verified_at stays null until a future OTP hook.
        user.setPhoneVerifiedAt(null);
        user.setPasswordHash(encoder.encode(request.password().trim()));
        user.setRole(selling ? Role.SELLER : Role.BUYER);
        users.save(user);
        if (selling) {
            onboarding.upsertProfile(user, intent);
        }
        return toAuth(user);
    }

    private static SellerIntent intentFromSignup(SignupRequest request) {
        return new SellerIntent(
                request.businessName(),
                request.name(),
                request.email(),
                request.phone(),
                request.address(),
                request.gstin(),
                request.notes(),
                request.categoryIds(),
                request.serviceCategoryIds(),
                Boolean.TRUE.equals(request.provideServices()),
                request.providerType(),
                request.profession(),
                request.serviceArea(),
                request.partnerDeliveryEnabled(),
                request.shopDeliveryEnabled(),
                request.lat(),
                request.lng());
    }

    public UserResponse me(String userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return toUser(user);
    }

    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail()) && users.existsByEmailIgnoreCase(email)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT, "That email belongs to another account and was left unchanged.");
            }
            user.setEmail(email);
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            String phone = OtpService.normalizePhone(request.phone());
            if (!phone.equals(user.getPhone())) {
                users.findFirstByPhone(phone)
                        .filter(other -> !other.getId().equals(user.getId()))
                        .ifPresent(other -> {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT,
                                    "That mobile number belongs to another account and was left unchanged.");
                        });
                user.setPhone(phone);
                // Changing phone clears verification until a future OTP flow re-verifies.
                user.setPhoneVerifiedAt(null);
            }
        }
        if (request.dob() != null) {
            user.setDob(request.dob());
        }
        if (request.pinCode() != null) {
            user.setPinCode(request.pinCode().trim());
        }
        if (request.shopRadiusKm() != null) {
            user.setShopRadiusKm(request.shopRadiusKm());
        }
        return toUser(users.save(user));
    }

    private AuthResponse toAuth(AppUser user) {
        return new AuthResponse(jwt.createToken(user.getId(), user.getRole().name()), toUser(user));
    }

    public static UserResponse toUser(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getShopId(),
                user.getDob(),
                user.getPinCode(),
                user.getShopRadiusKm());
    }
}
