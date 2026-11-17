package com.growercare.catalog.config;

import com.growercare.catalog.adapters.out.iam.IamPrincipalClient;
import com.growercare.outbox.OutboxWriter;
import com.growercare.security.PrincipalResolver;
import com.growercare.security.cache.CachingPrincipalResolver;
import com.growercare.security.web.GcSecurityProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/** catalog-registry-service wiring: principals from IAM behind the Redis cache (ADR-011), the outbox, import clients. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CatalogConfig.IamClientProperties.class, CatalogConfig.CatalogProperties.class})
public class CatalogConfig {

    /** {@code gc.iam.*}: where IAM lives and how long we wait for it. */
    @ConfigurationProperties("gc.iam")
    public record IamClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        public IamClientProperties {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(2) : readTimeout;
        }
    }

    /** {@code gc.catalog.*}: SPEC-CAT-01 D2, the service clients that may write the catalog (comma-separated). */
    @ConfigurationProperties("gc.catalog")
    public record CatalogProperties(String importClients) {
        public Set<String> importClientSet() {
            return importClients == null ? Set.of()
                    : Arrays.stream(importClients.split(",")).map(String::strip).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
        }
    }

    @Bean
    OAuth2AuthorizedClientManager iamTokenManager(ClientRegistrationRepository registrations, OAuth2AuthorizedClientService clients) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clients);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    @Bean
    IamPrincipalClient iamPrincipalClient(IamClientProperties iam, OAuth2AuthorizedClientManager tokens) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(iam.connectTimeout()).build());
        requestFactory.setReadTimeout(iam.readTimeout());
        return new IamPrincipalClient(RestClient.builder().baseUrl(iam.baseUrl()).requestFactory(requestFactory).build(), tokens);
    }

    @Bean
    PrincipalResolver principalResolver(IamPrincipalClient iam, StringRedisTemplate redis, JsonMapper json, GcSecurityProperties security) {
        return new CachingPrincipalResolver(iam::resolve, redis, json, security.principalCacheTtl());
    }

    @Bean
    OutboxWriter outboxWriter(JdbcClient jdbc, JsonMapper json) {
        return new OutboxWriter(jdbc, json);
    }
}
