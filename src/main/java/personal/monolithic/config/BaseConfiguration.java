package personal.monolithic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BaseConfiguration {

    @Bean
    public RestClient restClient(@Value("${incoming.base-url:http://localhost:1234}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean
    public WebClient webClient(@Value("${incoming.base-url:http://localhost:1234}") String baseUrl) {
        return WebClient.builder().baseUrl(baseUrl).build();
    }

}
