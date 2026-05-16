package eu.materadios.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TopicsResponse(List<Topic> results, Meta meta) {
}
