package eu.materadios.api;

import java.net.URL;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Tenant(long id, String address_city, String address_complement, String address_country,
		String address_name, String address_street, String address_zipcode, URL avatar_url, String birth_date,
		long building_id, TenantConfig config, String email, String first_name, String last_name, String full_address,
		String full_name, String full_name_inverted, String name, String phone_number, String status, String type,
		boolean valid_address, boolean valid_email, List<Lot> lots, User owner, List<TenantUser> users) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TenantConfig(long id, boolean can_message_create, boolean can_private_message_create) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TenantUser(long id, String community_name, Boolean confirmed, String created_at,
			String current_sign_in_at, String email, String first_name, String full_name, String invitation_accepted_at,
			String invitation_created_at, String invitation_sent_at, Integer invitations_count, String last_name,
			String password_updated_at, Boolean rental_management_access, String rm_last_invited_at,
			Integer sign_in_count, String type) {
	}
}
