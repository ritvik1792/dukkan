package in.dukkan.repository;

import in.dukkan.domain.Neighborhood;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeighborhoodRepository extends JpaRepository<Neighborhood, String> {}
