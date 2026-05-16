package eu.materadios.api;

import java.net.URL;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Context(boolean tenant, BuildingContext building, Person person, User user, boolean commonhold_access,
		boolean rental_management_access, boolean commonhold_treezor_access) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record BuildingContext(long id, String account_type, String kind, String country, String locale,
			long hubspot_deal_id, String management_type, boolean data_processing_agreement_accepted) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Person(long id, URL avatar_url, String first_name, String full_name, String last_name, String role,
			String access_level, String real_access_level, boolean onboarded) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record User(long id, String email, String type, UUID uuid) {
	}
}
