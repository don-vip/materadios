package eu.materadios.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MainAccount(long id, boolean active, double balance, long building_id, String full_title, int number,
		long parent_id, boolean prepaid_expenses_allowed, String title) {
}