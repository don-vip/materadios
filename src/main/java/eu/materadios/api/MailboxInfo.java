package eu.materadios.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailboxInfo(String mailbox_email_address) {

}
