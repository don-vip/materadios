package eu.materadios.model;

import eu.materadios.api.MailboxThread;

public record MailboxExportMetadata(MailboxThread thread, MailboxThread.Email email) {
}
