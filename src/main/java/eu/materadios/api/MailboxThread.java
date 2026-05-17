package eu.materadios.api;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailboxThread(boolean archived, List<User> assignees, Integer attachments_count, List<Email> emails,
        long id, boolean pinned, ThreadProject project, Boolean read, String state) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ThreadProject(long id, String kind, String title) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Email(List<Attachment> attachments, Integer attachments_size, List<String> bcc, long building_id,
			List<String> cc, String content_html, String content_text, OffsetDateTime date, boolean draft,
			boolean forwarded, String from, long id, String kind, List<ReadState> read_states, String references,
			String reply_to, User sender, List<Recipient> recipients, String status, String subject, long thread_id,
			List<String> to, String action_type) {
	}

	// Recipient can be either a User or a Supplier; add all fields from both types
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Recipient(
			// User fields
			long id, String email, String entity_leader_name, String first_name, String full_name,
			String full_name_inverted, String last_name, Boolean physical, String role, String type,
			String visible_role,
			// Supplier fields
			String account_holder, Boolean active, String address_city, String address_complement,
			String address_street, String address_zipcode, String bic_code, String comment, Boolean deletable,
			String detail, List<String> emails, String full_address, String iban_code, List<Kind> kinds,
			Boolean mailbox_notification_response_received, MainAccount main_account,
			Double my_sending_box_expected_next_debit_amount, String name, String notification_status,
			String phone_number, String siren, String siret, Boolean valid_address, Boolean valid_bank_details,
			Boolean valid_emails, String vat_number) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Attachment(long byte_size, String content_type, long id, String kind, String name,
			boolean saved_as_invoice, URL url) {
	}
}
