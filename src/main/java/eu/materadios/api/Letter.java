package eu.materadios.api;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Letter(long id, OffsetDateTime created_at, OffsetDateTime expected_delivery_date,
		OffsetDateTime expected_sending_date, String external_id, String kind, String kind_translation,
		String postage_type, double price, OffsetDateTime received_at, String recipient_address, String sender_address,
		OffsetDateTime sent_at, String status, String tracking_number, String type) {
}
