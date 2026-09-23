package com.example.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Guards the published contract. A spec that only describes the happy path is worse than none - the
 * caller writes no error handling. These fail the build if an endpoint stops documenting failures.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentationTest {

    private static final Map<String, String> OPERATIONS = Map.of(
            "/api/v1/reservations", "post",
            "/api/v1/reservations/{reservationId}", "get");

    private static final Map<String, List<String>> FAILURE_STATUSES = Map.of(
            "/api/v1/reservations", List.of("400", "409", "422", "502", "500"),
            "/api/v1/reservations/{reservationId}", List.of("404", "500"));

    private static final Map<String, String> SUCCESS_STATUSES = Map.of(
            "/api/v1/reservations", "201",
            "/api/v1/reservations/{reservationId}", "200");

    private static final String ERROR_JSON = "application/json";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode spec;

    @BeforeEach
    void fetchSpec() throws UnsupportedEncodingException {
        String body = mvc.get().uri("/v3/api-docs").exchange().getResponse().getContentAsString();
        spec = objectMapper.readTree(body);
    }

    private JsonNode responses(String path) {
        String method = OPERATIONS.get(path);
        JsonNode responses = spec.path("paths").path(path).path(method).path("responses");
        assertThat(responses.isObject()).as("operation %s %s must be documented", method, path).isTrue();
        return responses;
    }

    @Test
    @DisplayName("every operation documents success and all its failure modes")
    void everyOperationDocumentsItsFailures() {
        for (String path : OPERATIONS.keySet()) {
            JsonNode responses = responses(path);
            List<String> expected = new ArrayList<>(FAILURE_STATUSES.get(path));
            expected.add(SUCCESS_STATUSES.get(path));
            assertThat(responses.propertyNames())
                    .as("documented statuses for %s %s", OPERATIONS.get(path), path)
                    .containsAll(expected);
            responses.propertyStream().forEach(entry ->
                    assertThat(entry.getValue().path("description").asString())
                            .as("%s %s response %s needs a description", OPERATIONS.get(path), path, entry.getKey())
                            .isNotBlank());
        }
    }

    @Test
    @DisplayName("every failure points at the shared error schema")
    void failuresPointAtTheSharedErrorSchema() {
        for (String path : OPERATIONS.keySet()) {
            for (String status : FAILURE_STATUSES.get(path)) {
                JsonNode content = responses(path).path(status).path("content").path(ERROR_JSON);
                assertThat(content.path("schema").path("$ref").asString())
                        .as("%s %s response %s schema", OPERATIONS.get(path), path, status)
                        .isEqualTo("#/components/schemas/ApiError");
            }
        }
    }

    @Test
    @DisplayName("each failure carries a worked example a caller can match on")
    void eachFailureCarriesAnExample() {
        for (String path : OPERATIONS.keySet()) {
            for (String status : FAILURE_STATUSES.get(path)) {
                JsonNode example = responses(path).path(status).path("content")
                        .path(ERROR_JSON).path("example");
                assertThat(example.isObject()).as("%s %s response %s example", OPERATIONS.get(path), path, status).isTrue();
                assertThat(example.path("status").asInt()).isEqualTo(Integer.parseInt(status));
                assertThat(example.path("type").asString()).startsWith("https://");
                assertThat(example.path("title").asString()).isNotBlank();
                assertThat(example.path("detail").asString()).isNotBlank();

                String documentedPath = path.replace("{reservationId}", "P4145478");
                assertThat(example.path("instance").asString())
                        .as("%s %s response %s example should reference its own path", OPERATIONS.get(path), path, status)
                        .isEqualTo(documentedPath);
            }
        }
    }

    @Test
    @DisplayName("success responses are declared as JSON rather than */*")
    void successIsDeclaredAsJson() {
        for (String path : OPERATIONS.keySet()) {
            assertThat(responses(path).path(SUCCESS_STATUSES.get(path)).path("content").propertyNames())
                    .as("%s %s success content types", OPERATIONS.get(path), path)
                    .containsExactly("application/json");
        }
    }

    @Test
    @DisplayName("the checked-in openapi.yaml still matches the code")
    void checkedInSpecIsUpToDate() throws Exception {
        String live = mvc.get().uri("/v3/api-docs.yaml").exchange().getResponse().getContentAsString();
        String checkedIn = Files.readString(Path.of("src/main/resources/static/openapi.yaml"));

        assertThat(checkedIn.strip())
                .as("openapi.yaml is generated. Regenerate it with:%n"
                        + "  ./mvnw spring-boot:run%n"
                        + "  curl -s localhost:8080/v3/api-docs.yaml -o src/main/resources/static/openapi.yaml")
                .isEqualTo(live.strip());
    }

    @Test
    @DisplayName("the error schema is published for clients to generate from")
    void errorSchemaIsPublished() {
        JsonNode errorSchema = spec.path("components").path("schemas").path("ApiError");

        assertThat(errorSchema.path("properties").propertyNames())
                .contains("type", "title", "status", "detail", "instance");
    }
}
