package eu.materadios.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BuildingCharac(long id, boolean air_conditioning, boolean antenna, boolean auto_fence,
		boolean automatic_garage_door, boolean cameras, boolean caretaker, boolean caretaking_cie, boolean cleaning_cie,
		boolean collective_heating, boolean commercial_premises, int construction_year, boolean digicode,
		boolean elevator, List<String> equipment_list, boolean green_area, boolean intercom, int number_main_lots,
		int number_of_current_lots, boolean parking, String registration_number, boolean smoke_extraction,
		List<Subbuilding> subbuildings, int surface, boolean swimming_pool, boolean trash_chute,
		boolean underground_parking, boolean ventilation_cie) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Subbuilding(String name, String floors) {
	}
}
