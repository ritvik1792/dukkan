package in.dukkan.web;

import in.dukkan.service.AuthService;
import in.dukkan.service.OtpService;
import in.dukkan.web.dto.AuthDtos.AuthResponse;
import in.dukkan.web.dto.AuthDtos.LoginRequest;
import in.dukkan.web.dto.AuthDtos.OtpRequest;
import in.dukkan.web.dto.AuthDtos.OtpRequestResponse;
import in.dukkan.web.dto.AuthDtos.OtpVerifyRequest;
import in.dukkan.web.dto.AuthDtos.SignupRequest;
import in.dukkan.web.dto.AuthDtos.UpdateProfileRequest;
import in.dukkan.web.dto.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final OtpService otp;

    public AuthController(AuthService auth, OtpService otp) {
        this.auth = auth;
        this.otp = otp;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return auth.signup(request);
    }

    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@RequestBody OtpRequest request) {
        return otp.request(request);
    }

    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return otp.verify(request);
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return auth.me((String) authentication.getPrincipal());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(Authentication authentication, @RequestBody UpdateProfileRequest request) {
        return auth.updateProfile((String) authentication.getPrincipal(), request);
    }
}
