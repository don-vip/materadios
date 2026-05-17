package eu.materadios.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import eu.materadios.api.MailboxThread;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MailboxExportMetadata(MailboxThread thread, MailboxThread.Email email,
        List<String> attachmentLocalPaths) {

    public MailboxExportMetadata(MailboxThread thread, MailboxThread.Email email) {
        this(thread, email, List.of());
    }
}
