package in.dukkan.web;

import in.dukkan.service.AuthService;
import in.dukkan.service.OtpService;
import in.dukkan.service.PasswordResetService;
import in.dukkan.web.dto.AuthDtos.AuthResponse;
import in.dukkan.web.dto.AuthDtos.ForgotPasswordRequest;
import in.dukkan.web.dto.AuthDtos.ForgotPasswordResponse;
import in.dukkan.web.dto.AuthDtos.LoginRequest;
import in.dukkan.web.dto.AuthDtos.OtpRequest;
import in.dukkan.web.dto.AuthDtos.OtpRequestResponse;
import in.dukkan.web.dto.AuthDtos.OtpVerifyRequest;
import in.dukkan.web.dto.AuthDtos.ResetPasswordRequest;
import in.dukkan.web.dto.AuthDtos.ResetPasswordResponse;
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

    private static final String FORGOT_PASSWORD_MESSAGE =
            "If an account exists for that email, a password reset link has been sent.";

    private final AuthService auth;
    private final OtpService otp;
    private final PasswordResetService passwordReset;

    public AuthController(AuthService auth, OtpService otp, PasswordResetService passwordReset) {
        this.auth = auth;
        this.otp = otp;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    @PostMapping("/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return auth.signup(request);
    }

    /** Kept for a future phone-verification hook; signup/login no longer require OTP. */
    @PostMapping("/otp/request")
    public OtpRequestResponse requestOtp(@RequestBody OtpRequest request) {
        return otp.request(request);
    }

    /** Kept for a future phone-verification hook; signup/login no longer require OTP. */
    @PostMapping("/otp/verify")
    public AuthResponse verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        return otp.verify(request);
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordReset.requestReset(request.email());
        return new ForgotPasswordResponse(FORGOT_PASSWORD_MESSAGE);
    }

    @PostMapping("/reset-password")
    public ResetPasswordResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.resetPassword(request.token(), request.password());
        return new ResetPasswordResponse("Password updated. You can sign in with your new password.");
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
