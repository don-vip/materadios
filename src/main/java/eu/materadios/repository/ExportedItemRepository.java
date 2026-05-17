package eu.materadios.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import eu.materadios.model.ExportedItem;

@Repository
public interface ExportedItemRepository extends JpaRepository<ExportedItem, Long> {
    List<ExportedItem> findByCreatedAtBetween(Instant start, Instant end);

    Optional<ExportedItem> findByMateraId(String materaId);

    List<ExportedItem> findByMateraIdStartingWith(String prefix);

    boolean existsByMateraId(String materaId);
}
