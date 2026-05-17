package eu.materadios.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project_labels")
public class ProjectLabel {

    @Id
    private Long projectId;

    private String projectTitle;

    @Column(unique = true)
    private String gmailLabelId;

    private String gmailLabelName;

    private Instant syncedAt;

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getProjectTitle() { return projectTitle; }
    public void setProjectTitle(String projectTitle) { this.projectTitle = projectTitle; }

    public String getGmailLabelId() { return gmailLabelId; }
    public void setGmailLabelId(String gmailLabelId) { this.gmailLabelId = gmailLabelId; }

    public String getGmailLabelName() { return gmailLabelName; }
    public void setGmailLabelName(String gmailLabelName) { this.gmailLabelName = gmailLabelName; }

    public Instant getSyncedAt() { return syncedAt; }
    public void setSyncedAt(Instant syncedAt) { this.syncedAt = syncedAt; }
}
