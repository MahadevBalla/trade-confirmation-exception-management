package com.tcem.workitem.couchdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CouchDbClientTest {

    private MockRestServiceServer mockServer;
    private CouchDbClient couchDbClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:5984");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        couchDbClient = new CouchDbClient(builder.build(), objectMapper);
    }

    @Test
    void isHealthy_returnsTrue_whenServerAnswers200() {
        mockServer.expect(requestTo("http://localhost:5984/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"couchdb\":\"Welcome\",\"version\":\"3.3.3\"}", MediaType.APPLICATION_JSON));

        boolean healthy = couchDbClient.isHealthy();

        assertThat(healthy).isTrue();
        mockServer.verify();
    }

    @Test
    void isHealthy_returnsFalse_whenServerErrors() {
        mockServer.expect(requestTo("http://localhost:5984/"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        boolean healthy = couchDbClient.isHealthy();

        assertThat(healthy).isFalse();
        mockServer.verify();
    }

    @Test
    void ensureDatabaseExists_returnsTrue_whenDbAlreadyExists() {
        mockServer.expect(requestTo("http://localhost:5984/trades"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"db_name\":\"trades\"}", MediaType.APPLICATION_JSON));

        boolean exists = couchDbClient.ensureDatabaseExists("trades");

        assertThat(exists).isTrue();
        mockServer.verify();
    }

    @Test
    void ensureDatabaseExists_createsDb_whenDbMissing() {
        mockServer.expect(requestTo("http://localhost:5984/trades"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        mockServer.expect(requestTo("http://localhost:5984/trades"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CREATED).body("{\"ok\":true}").contentType(MediaType.APPLICATION_JSON));

        boolean created = couchDbClient.ensureDatabaseExists("trades");

        assertThat(created).isTrue();
        mockServer.verify();
    }

    @Test
    void getDocument_returnsDocument_whenFound() {
        mockServer.expect(requestTo("http://localhost:5984/trades/TRD-10001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"_id\":\"TRD-10001\",\"tradeId\":\"TRD-10001\"}", MediaType.APPLICATION_JSON));

        Optional<Map> doc = couchDbClient.getDocument("trades", "TRD-10001", Map.class);

        assertThat(doc).isPresent();
        assertThat(doc.get().get("tradeId")).isEqualTo("TRD-10001");
        mockServer.verify();
    }

    @Test
    void getDocument_returnsEmpty_whenNotFound() {
        mockServer.expect(requestTo("http://localhost:5984/trades/TRD-NONEXISTENT"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<Map> doc = couchDbClient.getDocument("trades", "TRD-NONEXISTENT", Map.class);

        assertThat(doc).isEmpty();
        mockServer.verify();
    }

    @Test
    void putDocument_createsDocumentSuccessfully() {
        // First check for current rev returns 404 (new doc)
        mockServer.expect(requestTo("http://localhost:5984/trades/TRD-10001"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // Put request
        mockServer.expect(requestTo("http://localhost:5984/trades/TRD-10001"))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withStatus(HttpStatus.CREATED)
                        .body("{\"ok\":true,\"id\":\"TRD-10001\",\"rev\":\"1-abc\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        Map<String, Object> data = Map.of("tradeId", "TRD-10001");
        CouchDbResponse response = couchDbClient.putDocument("trades", "TRD-10001", data);

        assertThat(response).isNotNull();
        assertThat(response.getOk()).isTrue();
        assertThat(response.getRev()).isEqualTo("1-abc");
        mockServer.verify();
    }

    @Test
    void find_returnsListOfDocuments() {
        FindQueryRequest query = FindQueryRequest.builder()
                .selector(Map.of("tradeId", "TRD-10001"))
                .limit(10)
                .build();

        mockServer.expect(requestTo("http://localhost:5984/trades/_find"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"docs\":[{\"tradeId\":\"TRD-10001\"}]}", MediaType.APPLICATION_JSON));

        List<Map> results = couchDbClient.find("trades", query, Map.class);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("tradeId")).isEqualTo("TRD-10001");
        mockServer.verify();
    }
}
