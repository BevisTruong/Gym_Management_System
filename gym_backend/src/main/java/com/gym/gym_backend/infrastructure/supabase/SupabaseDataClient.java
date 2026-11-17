package com.gym.gym_backend.infrastructure.supabase;

import com.gym.gym_backend.common.exception.ApiException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.beans.factory.annotation.Value;
import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class SupabaseDataClient {
    private static final ParameterizedTypeReference<List<Map<String, Object>>> ROWS = new ParameterizedTypeReference<>() {
    }

    ;
    private final RestClient client;
    private final String restUrl;

    public SupabaseDataClient(RestClient supabaseRestClient, @Value("${supabase.url}") String supabaseUrl) {
        this.client = supabaseRestClient;
        this.restUrl = supabaseUrl.replaceAll("/$", "") + "/rest/v1";
    }

    public List<Map<String, Object>> select(String table, Map<String, String> filters, String order, Integer limit) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(restUrl).pathSegment(table).queryParam("select",
                "*");
        filters.forEach(uri::queryParam);
        if (order != null && !order.isBlank())
            uri.queryParam("order", order);
        if (limit != null)
            uri.queryParam("limit", limit);
        return exchangeRows(() -> client.get().uri(uri.build().encode().toUri()).retrieve());
    }

    public List<Map<String, Object>> select(String table, Map<String, String> filters, String order, Integer limit,
            String projection) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(restUrl).pathSegment(table).queryParam("select",
                projection);
        filters.forEach(uri::queryParam);
        if (order != null && !order.isBlank())
            uri.queryParam("order", order);
        if (limit != null)
            uri.queryParam("limit", limit);
        return exchangeRows(() -> client.get().uri(uri.build().encode().toUri()).retrieve());
    }

    public List<Map<String, Object>> insert(String table, Map<String, Object> values) {
        return exchangeRows(
                () -> client.post().uri("/" + table).header("Prefer", "return=representation").body(values).retrieve());
    }

    public List<Map<String, Object>> update(String table, String idColumn, Object id, Map<String, Object> values) {
        URI uri = UriComponentsBuilder.fromUriString(restUrl).pathSegment(table).queryParam(idColumn, "eq." + id)
                .build().encode().toUri();
        return exchangeRows(
                () -> client.patch().uri(uri).header("Prefer", "return=representation").body(values).retrieve());
    }

    public List<Map<String, Object>> delete(String table, String idColumn, Object id) {
        URI uri = UriComponentsBuilder.fromUriString(restUrl).pathSegment(table).queryParam(idColumn, "eq." + id)
                .build().encode().toUri();
        return exchangeRows(() -> client.delete().uri(uri).header("Prefer", "return=representation").retrieve());
    }

    public List<Map<String, Object>> rpc(String function, Map<String, Object> values) {
        URI uri = UriComponentsBuilder.fromUriString(restUrl).pathSegment("rpc", function).build().encode().toUri();
        return exchangeRows(() -> client.post().uri(uri).body(values).retrieve());
    }

    private List<Map<String, Object>> exchangeRows(java.util.function.Supplier<RestClient.ResponseSpec> request) {
        try {
            return request.get().body(ROWS);
        } catch (RestClientResponseException ex) {
            String body = ex.getResponseBodyAsString();
            if (ex.getStatusCode().value() == 409 || body.contains("23505") || body.contains("23P01"))
                throw ApiException.conflict("Dữ liệu bị trùng hoặc xung đột với quy tắc hiện có");
            if (body.contains("23503"))
                throw ApiException.conflict("Không thể thao tác vì dữ liệu đang được tham chiếu");
            if (body.contains("23514") || body.contains("22P02"))
                throw ApiException.badRequest("Dữ liệu không thỏa mãn ràng buộc hệ thống");
            throw new SupabaseRequestException(ex.getStatusCode(), "Yêu cầu tới cơ sở dữ liệu thất bại");
        }
    }

    public static class SupabaseRequestException extends RuntimeException {
        private final HttpStatusCode status;

        SupabaseRequestException(HttpStatusCode status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatusCode status() {
            return status;
        }
    }

}
