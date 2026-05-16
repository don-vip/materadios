package eu.materadios.api;

import java.net.URL;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ElectronicLetter(long id, URL content_header, OffsetDateTime created_at, String kind,
		String kind_translation, double price, User recipient, String recipient_address, String status) {

}
