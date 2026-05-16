package eu.materadios.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Project(long id, boolean billable, OffsetDateTime created_at, String description, String kind,
		boolean notify_change, boolean notify_digest, String priority, long read_group_id, List<Long> read_user_ids,
		boolean readable, String status, String title, String type, boolean writable, long write_group_id) {
}
