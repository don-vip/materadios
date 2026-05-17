package eu.materadios.repository;

import eu.materadios.model.ExportedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ExportedItemRepository extends JpaRepository<ExportedItem, Long> {
    List<ExportedItem> findByCreatedAtBetween(Instant start, Instant end);
    List<ExportedItem> findByMateraIdStartingWith(String prefix);
}
