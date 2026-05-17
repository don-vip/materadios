package eu.materadios.api;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Document(long id, String name, String kind, Long folder_id, long building_id,
        boolean materani_only, boolean visible_by_owners, boolean has_associated_invoice,
        OffsetDateTime created_at, OffsetDateTime updated_at, DocumentFile file) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DocumentFile(String url, String content_type, Long size, Integer height, Integer width,
            OffsetDateTime created_at) {
    }
}
