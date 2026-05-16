package eu.materadios.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildingConfig(long id, long accountant_id, boolean ar24_opted_out, String ar24_user_id,
		boolean assemblies_use_unitary_key, boolean banks_api_activated, String billing_state,
		boolean budget_insight_activated, boolean budget_insight_enabled, long building_id,
		boolean can_fund_call_generate, String check_order, boolean churn_risk, String client_source,
		OffsetDateTime created_at, Csm csm, String delivery_full_address, boolean delivery_valid_address,
		boolean direct_debit_activated, boolean direct_debit_enabled, boolean email_brief_enabled,
		boolean fund_calls_by_check_allowed, int fund_calls_days_before, boolean fund_calls_generation,
		boolean fund_calls_sending, String generic_contact_email, String google_drive_folder_id, boolean hide_balances,
		boolean hide_lot_keys, long hubspot_assignee_id, long hubspot_csm_assignee_id, long hubspot_deal_id,
		long hubspot_lpa_deal_id, boolean internal_test, boolean letter_sender_is_building_name,
		String mailbox_email_address, String management_type, String matera_hub, String matera_legal_name,
		String meters_readings_type, boolean monthly_direct_debit, boolean monthly_payments, boolean msb_enabled,
		LocalDate next_billing_date, String official_email_address, String rebrandly_invitation_id,
		boolean seeuletter_opted_out, boolean seeuletter_payment_method, boolean social_landlord_building,
		long treezor_account_id, boolean treezor_activated, boolean treezor_enabled, String treezor_legal_name,
		int unpaid_actionable_threshold, boolean unpaid_automation, int unpaid_automation_threshold, int unpaid_days,
		boolean unpaid_formal_notice_automation, int unpaid_formal_notice_days,
		boolean unpaid_second_amicable_automation, int unpaid_second_amicable_days, OffsetDateTime updated_at,
		boolean vat_enabled, String visibility_addresses, String visibility_emails, String visibility_phone_numbers,
		Map<String, Object> warning_sms_dates) {

	public record Csm(long id, boolean archived, String community_name, String email, String first_name,
			String from_format, String full_name, String full_name_inverted, long hubspot_id, String last_name,
			String locale, String phone_nb, List<String> roles, String shared_slack_channel, String slack_channel,
			boolean super_admin, String type) {
	}
}
