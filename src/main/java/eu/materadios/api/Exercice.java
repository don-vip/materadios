package eu.materadios.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Exercice(long id, boolean approval_locked, String approved_at, long building_id, String end_date,
		String start_date, String status, String year) {

}
