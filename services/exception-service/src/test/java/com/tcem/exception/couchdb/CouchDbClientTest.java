
package com.tcem.exception.couchdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CouchDbClientTest {

    private MockRestServiceServer mockServer;
    private CouchDbClient couchDbClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:5984");

        mockServer = MockRestServiceServer.bindTo(builder).build();

        couchDbClient = new CouchDbClient(
                builder.build(),
                new ObjectMapper()
        );
    }

    @Test
    void countByView_returnsReducedCount() {
        mockServer.expect(requestTo(
                        "http://localhost:5984/exceptions/_design/exception_counts/_view/total"))
                .andRespond(withSuccess(
                        "{\"rows\":[{\"key\":null,\"value\":1500}]}",
                        MediaType.APPLICATION_JSON
                ));

        long count = couchDbClient.countByView(
                "exceptions", "exception_counts", "total"
        );

        assertThat(count).isEqualTo(1500);
        mockServer.verify();
    }

    @Test
    void countByView_returnsZeroWhenRowsAreEmpty() {
        mockServer.expect(requestTo(
                        "http://localhost:5984/exceptions/_design/exception_counts/_view/total"))
                .andRespond(withSuccess(
                        "{\"rows\":[]}",
                        MediaType.APPLICATION_JSON
                ));

        long count = couchDbClient.countByView(
                "exceptions", "exception_counts", "total"
        );

        assertThat(count).isZero();
        mockServer.verify();
    }
}
