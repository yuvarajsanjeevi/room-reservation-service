package com.example.reservation.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(info = @Info(
        title = "Room Reservation Service",
        version = "1.0.0",
        description = "Marvel Hospitality - confirms and tracks room reservations across cash, "
                + "credit card and bank transfer payments."),
        servers = @Server(url = "/"))
@Configuration
public class OpenApiConfig {

    public static final String ERROR_SCHEMA = "ApiError";
    public static final String ERROR_SCHEMA_REF = "#/components/schemas/" + ERROR_SCHEMA;

    @Bean
    OpenApiCustomizer errorSchemaCustomizer() {
        return openApi -> openApi.getComponents().addSchemas(ERROR_SCHEMA, errorSchema());
    }

    private static Schema<?> errorSchema() {
        return new ObjectSchema()
                .description("The body of every error response")
                .addProperty("type", new StringSchema().format("uri")
                        .example("https://example.com/room-reservation-service/errors/room-unavailable"))
                .addProperty("title", new StringSchema().example("Room unavailable"))
                .addProperty("status", new IntegerSchema().example(409))
                .addProperty("detail", new StringSchema()
                        .example("Room 204 is already reserved between 2026-10-01 and 2026-10-05."))
                .addProperty("instance", new StringSchema().format("uri").example("/api/v1/reservations"));
    }
}
