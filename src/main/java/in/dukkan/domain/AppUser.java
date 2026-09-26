package in.dukkan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "app_users")
public class AppUser extends AuditableEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hashes are 60 chars; DB column is VARCHAR(100). Shorter columns truncate and break login. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** One account, one role. Many buyers, sellers, and admins can exist side by side. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "shop_id")
    private String shopId;

    private String phone;

    private LocalDate dob;

    @Column(name = "pin_code")
    private String pinCode;

    @Column(name = "shop_radius_km")
    private Integer shopRadiusKm;

    /** Null until a future phone OTP verification flow sets it. */
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getDob() {
        return dob;
    }

    public void setDob(LocalDate dob) {
        this.dob = dob;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public Integer getShopRadiusKm() {
        return shopRadiusKm;
    }

    public void setShopRadiusKm(Integer shopRadiusKm) {
        this.shopRadiusKm = shopRadiusKm;
    }

    public Instant getPhoneVerifiedAt() {
        return phoneVerifiedAt;
    }

    public void setPhoneVerifiedAt(Instant phoneVerifiedAt) {
        this.phoneVerifiedAt = phoneVerifiedAt;
    }
}
