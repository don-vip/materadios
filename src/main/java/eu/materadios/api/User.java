package eu.materadios.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record User(long id, String email, String entity_leader_name, String first_name, String full_name,
		String full_name_inverted, String last_name, Boolean physical, String role, String type,
		String visible_role) {
}