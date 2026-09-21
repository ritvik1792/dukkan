package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Role;
import in.dukkan.repository.UserRepository;
import in.dukkan.security.JwtService;
import in.dukkan.web.dto.AuthDtos.AuthResponse;
import in.dukkan.web.dto.AuthDtos.LoginRequest;
import in.dukkan.web.dto.AuthDtos.SignupRequest;
import in.dukkan.web.dto.AuthDtos.UpdateProfileRequest;
import in.dukkan.web.dto.AuthDtos.UserResponse;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse login(LoginRequest request) {
        AppUser user = users.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return toAuth(user);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }
        AppUser user = new AppUser();
        user.setId(Ids.next("u"));
        user.setName(request.name());
        user.setEmail(request.email().toLowerCase());
        user.setPhone(request.phone());
        user.setPasswordHash(encoder.encode(request.password()));
        user.setRole(Role.BUYER);
        users.save(user);
        return toAuth(user);
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
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
            }
            user.setEmail(email);
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(OtpService.normalizePhone(request.phone()));
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
