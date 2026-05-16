package eu.materadios.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PrivateTopicsResponse(List<PrivateTopic> results, Meta meta) {
}
