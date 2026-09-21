package in.dukkan.web.dto;

import in.dukkan.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public class AuthDtos {

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record SignupRequest(
            @NotBlank String name,
            @Email @NotBlank String email,
            String phone,
            @NotBlank @Size(min = 6) String password) {}

    public record OtpRequest(String phone, String email, String purpose) {}

    public record OtpRequestResponse(String destination, String purpose, Instant expiresAt, String devCode) {}

    public record OtpVerifyRequest(String phone, String email, @NotBlank String code, String purpose) {}

    public record UpdateProfileRequest(
            String name, String email, String phone, LocalDate dob, String pinCode, Integer shopRadiusKm) {}

    public record UserResponse(
            String id,
            String name,
            String email,
            String phone,
            Role role,
            String shopId,
            LocalDate dob,
            String pinCode,
            Integer shopRadiusKm) {}

    public record AuthResponse(String token, UserResponse user) {}
}
