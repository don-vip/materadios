package eu.materadios.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Supplier(long id, String account_holder, boolean active, String address_city, String address_complement,
		String address_street, String address_zipcode, String bic_code, String comment, boolean deletable,
		String detail, List<String> emails, String full_address, String iban_code, List<Kind> kinds,
		boolean mailbox_notification_response_received, MainAccount main_account,
		Double my_sending_box_expected_next_debit_amount, String name, String notification_status, String phone_number,
		String siren, String siret, String type, boolean valid_address, boolean valid_bank_details,
		boolean valid_emails, String vat_number) {
}
