package eu.materadios.api;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Topic(long id, boolean announcement, User author, long building_id, String created_at,
		int current_user_unread_messages_count, boolean draft, List<Integer> follower_ids, List<Message> messages,
		boolean pinned, boolean posted_as_council, Long project_id, OffsetDateTime published_at, int read_states_count,
		String title, boolean visible_by_tenants) {
}
