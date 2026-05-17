package eu.materadios.api;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentFolder(long id, String color, OffsetDateTime created_at, boolean deletable,
        int documents_count, boolean editable, Long holder_id, String holder_type, String kind,
        int materani_only_documents_count, String name, Long parent_id, OffsetDateTime updated_at) {
}
