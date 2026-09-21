package in.dukkan.repository;

import in.dukkan.domain.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingsRepository extends JpaRepository<PlatformSettings, String> {}
