package eu.materadios.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "exported_items")
public class ExportedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String materaId;

    @Column(nullable = false)
    private String type; // EMAIL or DOCUMENT

    @Column(nullable = false)
    private String localPath;

    private Boolean exported = false;

    private String googleUrl;

    private Instant exportedAt;

    private Instant createdAt = Instant.now();

    @Lob
    private String metadataJson;

    // Getters and setters omitted for brevity

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getMateraId() { return materaId; }
    public void setMateraId(String materaId) { this.materaId = materaId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getLocalPath() { return localPath; }
    public void setLocalPath(String localPath) { this.localPath = localPath; }
    public Boolean getExported() { return exported; }
    public void setExported(Boolean exported) { this.exported = exported; }
    public String getGoogleUrl() { return googleUrl; }
    public void setGoogleUrl(String googleUrl) { this.googleUrl = googleUrl; }
    public Instant getExportedAt() { return exportedAt; }
    public void setExportedAt(Instant exportedAt) { this.exportedAt = exportedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
