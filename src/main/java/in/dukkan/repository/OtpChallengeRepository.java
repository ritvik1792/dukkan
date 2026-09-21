package in.dukkan.repository;

import in.dukkan.domain.OtpChallenge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, String> {
    List<OtpChallenge> findByDestinationAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String destination, String purpose);
}
