package eu.materadios.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import eu.materadios.model.ProjectLabel;

@Repository
public interface ProjectLabelRepository extends JpaRepository<ProjectLabel, Long> {
    Optional<ProjectLabel> findByGmailLabelId(String gmailLabelId);
}
