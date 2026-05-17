package eu.materadios.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DocumentsResponse(List<Document> results, Meta meta) {
}
