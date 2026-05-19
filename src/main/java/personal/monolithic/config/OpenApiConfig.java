package personal.monolithic.config;

import static personal.monolithic.constants.Constants.CORRELATION_ID_HEADER;

import java.util.List;
import java.util.UUID;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "Monolithic API",
        version = "v1",
        description = "JWT secured API documentation"
), security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

    @Bean
    public OpenApiCustomizer correlationIdHeaderCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }

            String defaultCorrelationId = UUID.randomUUID().toString();
            openApi.getPaths()
                    .values()
                    .forEach(pathItem -> pathItem.readOperations()
                            .forEach(operation -> addCorrelationIdHeader(operation, defaultCorrelationId)));
        };
    }

    private void addCorrelationIdHeader(Operation operation, String defaultCorrelationId) {
        List<Parameter> parameters = operation.getParameters();
        if (parameters != null && parameters.stream()
                .anyMatch(parameter -> "header".equals(parameter.getIn()) && CORRELATION_ID_HEADER.equalsIgnoreCase(
                        parameter.getName()))) {
            return;
        }

        Parameter parameter = new Parameter().in("header")
                .name(CORRELATION_ID_HEADER)
                .required(false)
                .description("Correlation ID used to trace this request across logs.")
                .schema(new StringSchema()._default(defaultCorrelationId).example(defaultCorrelationId));

        operation.addParametersItem(parameter);
    }
}
