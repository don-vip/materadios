package eu.materadios.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Meta(long total_count, long count, String start_cursor, String end_cursor, boolean has_next_page,
		boolean has_previous_page, String view) {
}
