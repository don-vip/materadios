package eu.materadios.api;

import java.net.URL;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailboxThread(boolean archived, List<User> assignees, List<Email> emails, long id, boolean pinned,
		String project, String state) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Email(List<Attachment> attachments, Integer attachments_size, List<String> bcc, long building_id,
			List<String> cc, String content_html, String content_text, OffsetDateTime date, boolean draft,
			boolean forwarded, String from, long id, String kind, List<ReadState> read_states, String references,
			String reply_to, User sender, List<String> recipients, String status, String subject, long thread_id,
			List<String> to, String action_type) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Attachment(long byte_size, String content_type, long id, String kind, String name,
			boolean saved_as_invoice, URL url) {
	}
}
