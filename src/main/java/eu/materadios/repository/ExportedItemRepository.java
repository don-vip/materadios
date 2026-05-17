package eu.materadios.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import eu.materadios.model.ExportedItem;

@Repository
public interface ExportedItemRepository extends JpaRepository<ExportedItem, Long> {
    List<ExportedItem> findByCreatedAtBetween(Instant start, Instant end);

    List<ExportedItem> findByMateraIdStartingWith(String prefix);
}
