package eu.materadios.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivateTopic(long id, User author, long author_id, OffsetDateTime created_at,
		List<PrivateMessage> messages, List<ReadState> read_states, List<User> recipients, String title) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PrivateMessage(long id, User author, long author_id, String content, OffsetDateTime created_at,
			long topic_id) {
	}
}
