package eu.materadios.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MateraBankAccount(long id, boolean active, BankAccount bank_account, long building_id,
		String deductible_charges, String full_title, int number, Long parent_id, boolean prepaid_expenses_allowed,
		String recoverable_charges, String title) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record BankAccount(long id, String account_holder, String balance, String bic_code,
			boolean budget_insight_enabled, boolean closed, String created_at, String iban_code, String source,
			boolean treezor_enabled, long treezor_id, String treezor_tag, int unreconciled_bank_operations_count,
			boolean valid_bank_details) {
	}
}
