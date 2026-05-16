package eu.materadios.api;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(long id, String content, OffsetDateTime created_at, boolean posted_as_council, long topic_id) {

}
