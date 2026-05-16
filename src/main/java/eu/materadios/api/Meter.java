package eu.materadios.api;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Meter(long id, ColdWaterMeter cold_water_meter, Long common_area_distribution_key_id,
		long distribution_key_id, boolean fake, String fluid, Long hot_water_meter_id,
		double individual_consumption_ratio, LocalDate last_reading_date, boolean meter_read, String name,
		String reading_kind, Long residual_distribution_key_id, double spendings_transfer_ratio) {

	public record ColdWaterMeter(long id, String custom_type, String fluid, String meter_provider, String name,
			String reading_kind, Long supplier_contract_id) {
	}
}
