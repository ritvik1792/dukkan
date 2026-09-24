package in.dukkan.repository;

import in.dukkan.domain.PasswordResetToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {

    Optional<PasswordResetToken> findByIdAndTokenHash(String id, String tokenHash);

    List<PasswordResetToken> findByUserIdAndUsedAtIsNull(String userId);

    @Modifying
    @Query(
            "update PasswordResetToken t set t.usedAt = :usedAt where t.userId = :userId and t.usedAt is null and t.id <> :exceptId")
    int markOutstandingUsed(
            @Param("userId") String userId,
            @Param("exceptId") String exceptId,
            @Param("usedAt") java.time.Instant usedAt);
}
