package eu.materadios.api;

public record User(long id, String email, String entity_leader_name, String first_name, String full_name,
		String full_name_inverted, String last_name, boolean physical, String role, String type,
		String visible_role) {
}