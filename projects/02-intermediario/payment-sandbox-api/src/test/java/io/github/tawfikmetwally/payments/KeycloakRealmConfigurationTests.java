package io.github.tawfikmetwally.payments;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeycloakRealmConfigurationTests {

    private static final Path REALM_FILE = Path.of(
            "docker/keycloak/payment-sandbox-realm.json");

    private JsonNode realm;

    @BeforeEach
    void loadRealmConfiguration() throws IOException {
        realm = new ObjectMapper().readTree(REALM_FILE.toFile());
    }

    @Test
    void definesIsolatedEnabledRealmAndApiAudience() {
        assertThat(realm.path("realm").asText())
                .isEqualTo("payment-sandbox");
        assertThat(realm.path("enabled").asBoolean()).isTrue();

        JsonNode api = client("payment-sandbox-api");
        assertThat(api.path("bearerOnly").asBoolean()).isTrue();
        assertThat(api.path("serviceAccountsEnabled").asBoolean()).isFalse();

        JsonNode audience = clientScope("payment-sandbox-api-audience");
        JsonNode mapper = audience.path("protocolMappers").get(0);
        assertThat(mapper.path("protocolMapper").asText())
                .isEqualTo("oidc-audience-mapper");
        assertThat(mapper.path("config")
                .path("included.client.audience")
                .asText()).isEqualTo("payment-sandbox-api");
        assertThat(mapper.path("config")
                .path("access.token.claim")
                .asBoolean()).isTrue();
    }

    @Test
    void configuresMerchantAAsConfidentialMachineClient() {
        JsonNode client = client("merchant-a-client");

        assertMachineClient(client, "${MERCHANT_A_CLIENT_SECRET}");
        assertThat(textValues(client.path("defaultClientScopes")))
                .containsExactlyInAnyOrder(
                        "payments:create",
                        "payments:read",
                        "refunds:create",
                        "payment-sandbox-api-audience");
    }

    @Test
    void limitsMerchantBToPaymentCreationAndReading() {
        JsonNode client = client("merchant-b-client");

        assertMachineClient(client, "${MERCHANT_B_CLIENT_SECRET}");
        assertThat(textValues(client.path("defaultClientScopes")))
                .containsExactlyInAnyOrder(
                        "payments:create",
                        "payments:read",
                        "payment-sandbox-api-audience")
                .doesNotContain("refunds:create");
    }

    @Test
    void exposesOnlyTheThreeBusinessScopesInTokenScope() {
        assertThat(List.of(
                "payments:create",
                "payments:read",
                "refunds:create"))
                .allSatisfy(scopeName -> {
                    JsonNode scope = clientScope(scopeName);
                    assertThat(scope.path("protocol").asText())
                            .isEqualTo("openid-connect");
                    assertThat(scope.path("attributes")
                            .path("include.in.token.scope")
                            .asBoolean()).isTrue();
                });
        assertThat(clientScope("payment-sandbox-api-audience")
                .path("attributes")
                .path("include.in.token.scope")
                .asBoolean()).isFalse();
    }

    private void assertMachineClient(JsonNode client, String secret) {
        assertThat(client.path("enabled").asBoolean()).isTrue();
        assertThat(client.path("publicClient").asBoolean()).isFalse();
        assertThat(client.path("serviceAccountsEnabled").asBoolean()).isTrue();
        assertThat(client.path("standardFlowEnabled").asBoolean()).isFalse();
        assertThat(client.path("directAccessGrantsEnabled").asBoolean())
                .isFalse();
        assertThat(client.path("implicitFlowEnabled").asBoolean()).isFalse();
        assertThat(client.path("fullScopeAllowed").asBoolean()).isFalse();
        assertThat(client.path("secret").asText()).isEqualTo(secret);
    }

    private JsonNode client(String clientId) {
        return findByName(realm.path("clients"), "clientId", clientId);
    }

    private JsonNode clientScope(String name) {
        return findByName(realm.path("clientScopes"), "name", name);
    }

    private JsonNode findByName(
            JsonNode values,
            String field,
            String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.path(field).asText())) {
                return value;
            }
        }
        throw new AssertionError(expected + " was not found in " + field);
    }

    private List<String> textValues(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }
}
