package eu.materadios.api;

import java.net.URL;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Owner(long id, String access_level, boolean accounting_handover_balances_visible,
		String accounting_handover_history_management_status,
		ActiveOrCanceledDirectDebitMandate active_or_canceled_direct_debit_mandate, String additional_infos,
		String address_city, String address_complement, String address_country, String address_name,
		String address_street, String address_zipcode, URL avatar_url, LocalDate birth_date, long building_id,
		OwnerConfig config, String email, String entity_leader_name, String first_name, String full_address,
		String full_name, String full_name_inverted, String last_name, List<Lot> lots, MainAccount main_account,
		boolean monthly_payments, List<Object> mutations, String name, String nameless_full_address,
		String occupation_type, boolean only_send_fund_call_to_agency, Boolean other_buildings_lessor,
		String phone_number, boolean physical, Preferences preferences, String role, List<OwnerScope> scopes,
		String status, String type, boolean valid_address, boolean valid_agency_email, boolean valid_email,
		String visible_role, WorkFundAccount work_fund_account) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OwnerScope(long id, String kind, long owner_id, String title) {

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ActiveOrCanceledDirectDebitMandate(long id, boolean active, String address_city,
			String address_complement, String address_country, String address_street, String address_zipcode,
			String bic_code, Document document, String full_name, String iban_code, String phone_number,
			String signed_at, String state, String yousign_error_message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Document(long id, long building_id, File file, String kind, String name) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record File(OffsetDateTime created_at, String content_type, Integer height, long size, URL url,
			Integer width) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OwnerConfig(long id, boolean can_budget_read, boolean can_message_create,
			boolean can_private_message_create, boolean can_urgent_ticket_create, Boolean monthly_direct_debit,
			long owner_id, boolean unpaid_automation_email, boolean unpaid_automation_letter) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Preferences(long id, boolean fund_calls_letter_reception, String fund_calls_second_email,
			boolean fund_calls_send_to_primary_email, String fund_calls_third_email, long owner_id,
			String tracked_letters_preferences, boolean valid_address_for_fund_calls,
			boolean valid_emails_for_fund_calls) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record WorkFundAccount(long id, boolean active, double balance, long building_id, String full_title,
			int number, long parent_id, boolean prepaid_expenses_allowed, String title) {
	}
}
