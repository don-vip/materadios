package eu.materadios.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Mutation(long id, String building_insurance_status, User buyer, OffsetDateTime created_at, LocalDate date,
		LocalDate date_notification, boolean draft, List<Lot> lots, String procedures_status, User seller,
		double seller_balance_with_past_fund_calls, State state) {

	public record State(boolean documents_sent_to_notary, boolean finished, boolean validated, boolean buyer_invited,
			boolean etat_date_signed, boolean etat_date_electronically_signed, boolean pre_etat_date_signed,
			boolean pre_etat_date_electronically_signed, boolean article_20_2_signed,
			boolean article_20_2_electronically_signed, boolean completed_form) {
	}
}
