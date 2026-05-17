package eu.materadios.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import eu.materadios.api.Building;
import eu.materadios.api.BuildingCharac;
import eu.materadios.api.BuildingConfig;
import eu.materadios.api.Context;
import eu.materadios.api.ElectronicLettersResponse;
import eu.materadios.api.Exercice;
import eu.materadios.api.ExercicesResponse;
import eu.materadios.api.LettersResponse;
import eu.materadios.api.MailboxInfo;
import eu.materadios.api.MailboxThread;
import eu.materadios.api.MailboxThreadsResponse;
import eu.materadios.api.MateraBankAccount;
import eu.materadios.api.MateraBankAccountsResponse;
import eu.materadios.api.MessagesResponse;
import eu.materadios.api.Meter;
import eu.materadios.api.MetersResponse;
import eu.materadios.api.Mutation;
import eu.materadios.api.MutationsResponse;
import eu.materadios.api.Owner;
import eu.materadios.api.OwnersResponse;
import eu.materadios.api.PrivateTopicsResponse;
import eu.materadios.api.Project;
import eu.materadios.api.ProjectsResponse;
import eu.materadios.api.Supplier;
import eu.materadios.api.SuppliersResponse;
import eu.materadios.api.Tenant;
import eu.materadios.api.TenantsResponse;
import eu.materadios.api.Topic;
import eu.materadios.api.TopicsResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class MateraApiService {
    private static final Logger log = LoggerFactory.getLogger(MateraApiService.class);

    private final MateraAuthService authService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Value("${matera.api.url:https://api-core.matera.eu}")
    private String apiUrl;

    public MateraApiService(MateraAuthService authService) {
        this.authService = authService;
    }

    public List<MateraBankAccount> getBankAccounts() {
        return callApi(
                "accounts/bank_accounts?includes[bank_account][]=balance&includes[bank_account][]=unreconciled_bank_operations_count",
                MateraBankAccountsResponse.class).results();
    }

    public Building getBuilding(long buildingId) {
        return callApi("buildings/" + buildingId + "?view=extended", Building.class);
    }

    public BuildingCharac getBuildingCharac(long buildingId) {
        return callApi("building_charac?id=" + buildingId, BuildingCharac.class);
    }

    public BuildingConfig getBuildingConfig(long buildingId) {
        return callApi("buildings/" + buildingId + "/config?includes[csm]=true", BuildingConfig.class);
    }

    public Context getContext() {
        return callApi("context", Context.class);
    }

    public ElectronicLettersResponse getElectronicLetters(String after) {
        return callApi("electronic_letters?includes[]=recipient&includes[]=status&order[id]=desc" + paging(25, after),
                ElectronicLettersResponse.class);
    }

    public List<Exercice> getExercices() {
        return callApi("exercices", ExercicesResponse.class).results();
    }

    public LettersResponse getLetters(String after) {
        return callApi("letters?includes[]=recipient&includes[]=status&order[id]=desc" + paging(25, after),
                LettersResponse.class);
    }

    public MailboxInfo getMailboxInfo() {
        return callApi("mailbox/info", MailboxInfo.class);
    }

    public MailboxThread getMailboxThread(long threadId) {
        return callApi("mailbox/threads/" + threadId
                + "?includes[assignees]=avatar&includes[emails][attachments]=&includes[emails][read_states]=&includes[emails][recipients]=&includes[emails][sender]=avatar&includes[project][read_group]=members&includes[project][title]=&includes[project][write_group]=members",
                MailboxThread.class);
    }

    public MailboxThreadsResponse getMailboxThreads(String after) {
        return callApi(
                "mailbox/threads?includes[assignees][avatar]=true&includes[emails][read_states]=true&includes[emails][recipients][avatar]=true&includes[emails][sender][avatar]=true&includes[project]=true&view=preview"
                        + paging(25, after),
                MailboxThreadsResponse.class);
    }

    public MessagesResponse getMessages(String after) {
        return callApi("messages" + paging(25, after), MessagesResponse.class);
    }

    public List<Meter> getMeters() {
        return callApi(
                "meters?includes[cold_water_meter]=true&includes[distribution_key_id]=true&includes[last_reading_date]=true&view=extended",
                MetersResponse.class).results();
    }

    public List<Mutation> getMutations() {
        return callApi(
                "mutations?includes[buyer]=true&includes[lots]=true&includes[seller]=true&includes[seller_balance_with_past_fund_calls]=true&includes[state]=true&order[created_at]=desc",
                MutationsResponse.class).results();
    }

    public List<Owner> getOwners() {
        return callApi(
                "owners?includes[active_or_canceled_direct_debit_mandate][document][file]=true&includes[avatar]=true&includes[config][monthly_direct_debit]=true&includes[lots]=true&includes[main_account][balance]=true&includes[mutations]=true&includes[preferences]=true&includes[scopes]=true&includes[work_fund_account][balance]=true&view=extended",
                OwnersResponse.class).results();
    }

    public PrivateTopicsResponse getPrivateTopics(String after) {
        return callApi(
                "private_topics?includes[author]=true&includes[messages][author]=true&includes[read_states][app_user]=true&includes[recipients]=true&order[MAX(private_messages.created_at)]=desc"
                        + paging(25, after),
                PrivateTopicsResponse.class);
    }

    public List<Project> getProjects() {
        return callApi("abstract_projects?order[created_at]=desc", ProjectsResponse.class).results();
    }

    public List<Supplier> getSuppliers() {
        return callApi("suppliers?includes[deletable]=true&includes[main_account][balance]=true",
                SuppliersResponse.class).results();
    }

    public List<Tenant> getTenants() {
        return callApi(
                "tenants?includes[avatar]=true&includes[config]=true&includes[lots]=true&includes[owner]=true&includes[users]=true&view=extended",
                TenantsResponse.class).results();
    }

    public Topic getTopic(long topicId) {
        return callApi("topics/" + topicId
                + "?includes[author]=avatar&includes[event]=&includes[follower_ids]=&includes[messages][author]=avatar&includes[messages][documents]=file&includes[poll][options][votes]=voter&includes[posted_as_council]=&includes[read_states]=person",
                Topic.class);
    }

    public TopicsResponse getTopics(String after) {
        return callApi(
                "topics?includes[author]=avatar&includes[current_user_unread_messages_count]=&includes[follower_ids]=&includes[messages]=author&includes[posted_as_council]=&includes[read_states_count]=&order[MAX(messages.created_at)]=desc&order[id]=desc"
                        + paging(25, after),
                TopicsResponse.class);
    }

    private static String paging(int limit, String after) {
        return "&limit=" + limit + (after != null ? "&after=" + after : "");
    }

    private <T> T callApi(String api, Class<T> responseClass) {
        try {
            HttpResponse<String> resp = callApi(httpClient, apiUrl, api, authService.getCookieHeader());
            String body = resp.body();
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                JsonNode root = mapper.readTree(body);
                JsonNode resultNode = root.get("result");
                return mapper.convertValue(resultNode != null ? resultNode : root, responseClass);
            } else {
                log.warn("Matera API call to {} failed: {} -> {}", api, resp.statusCode(), body);
                throw new RuntimeException("Matera API '" + api + "' returned status " + resp.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to call API from Matera", e);
        }
    }

    public static HttpResponse<String> callApi(HttpClient httpClient, String apiUrl, String api, String cookies)
            throws IOException, InterruptedException {
        HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(apiUrl + "/api/v1/" + api))
                .header("Accept", "application/json").GET();
        if (cookies != null && !cookies.isBlank()) {
            rb.header("Cookie", cookies);
        }
        return httpClient.send(rb.build(), BodyHandlers.ofString());
    }
}
