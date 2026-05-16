package eu.materadios.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Lot(long id, Double area, String creation_date, boolean current, String deletion_date, boolean main,
		String name, String number, long owner_id, boolean professional) {
}