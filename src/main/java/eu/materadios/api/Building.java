package eu.materadios.api;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Building(long id, String account_type, String address_city, String address_street, String address_zipcode,
		String country, OffsetDateTime created_at, boolean demo, boolean freemium, String full_address, String locale,
		boolean lots_and_shares_filled, String name, String syndic_type, boolean valid_address) {

}
