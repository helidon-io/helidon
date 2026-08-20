/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.integrations.langchain4j.providers.cohere;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.time.Duration;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.Testing;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import org.junit.jupiter.api.Test;

import static io.helidon.common.media.type.MediaTypes.APPLICATION_X_YAML;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static io.helidon.integrations.langchain4j.providers.cohere.CohereConstants.ConfigCategory.MODEL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class ScoringModelConfigTest {

    @Test
    void testDefaultRoot(Config c) {
        var config = CohereScoringModelConfig.create(CohereConstants.create(c, MODEL, "test-model"));

        assertThat(config, is(notNullValue()));
        assertThat(config.apiKey().isPresent(), equalTo(true));
        assertThat(config.apiKey().get(), equalTo("api-key"));
        assertThat(config.modelName().isPresent(), equalTo(true));
        assertThat(config.modelName().get(), equalTo("model-name"));
        assertThat(config.baseUrl().isPresent(), equalTo(true));
        assertThat(config.baseUrl().get(), equalTo("base-url"));
        assertThat(config.timeout().isPresent(), is(true));
        assertThat(config.timeout().get(), equalTo(Duration.parse("PT10M")));
        assertThat(config.maxRetries().isPresent(), is(true));
        assertThat(config.maxRetries().get(), is(5));
        assertThat(config.logRequests().isPresent(), is(true));
        assertThat(config.logRequests().get(), is(true));
        assertThat(config.logResponses().isPresent(), is(true));
        assertThat(config.logResponses().get(), is(true));
        assertThat(config.proxy().map(Proxy::toString), optionalValue(equalTo("defaultProxy")));
        assertThat(config.configuredBuilder().build(), is(notNullValue()));
    }

    @Test
    void testCustomProxy(ServiceRegistry registry) {

        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    test-model:
                      provider: cohere

                  providers:
                    cohere:
                      api-key: api-key
                      proxy.service-registry.named: customProxy
                """;

        var config = CohereScoringModelConfig.builder()
                .serviceRegistry(registry)
                .config(CohereConstants.create(Config.just(ConfigSources.create(yaml, APPLICATION_X_YAML)), MODEL, "test-model"))
                .build();

        assertThat(config.proxy().map(Proxy::toString), optionalValue(equalTo("customProxy")));
        assertThat(config.configuredBuilder().build(), is(notNullValue()));
    }

    @Test
    void testNoProxy(ServiceRegistry registry) {

        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    test-model:
                      provider: cohere
                
                  providers:
                    cohere:
                      proxy.service-registry.named:
                """;

        var config = CohereScoringModelConfig.builder()
                .serviceRegistry(registry)
                .config(CohereConstants.create(Config.just(ConfigSources.create(yaml, APPLICATION_X_YAML)), MODEL, "test-model"))
                .build();

        assertThat(config.proxy().map(Proxy::toString), optionalEmpty());
    }

    @Test
    void testHttpProxyAdapter() {
        var address = InetSocketAddress.createUnresolved("proxy.example", 8080);
        var proxy = new Proxy(Proxy.Type.HTTP, address);

        var selected = proxySelector(proxy).select(URI.create("https://api.cohere.com"));

        assertThat(selected, contains(proxy));
    }

    @Test
    void testNoProxyAdapter() {
        var selected = proxySelector(Proxy.NO_PROXY).select(URI.create("https://api.cohere.com"));

        assertThat(selected, contains(Proxy.NO_PROXY));
    }

    @SuppressWarnings("deprecation")
    @Test
    void testSocksProxyAdapter() throws Exception {
        try (var socks = new MockSocksProxy()) {
            var config = CohereScoringModelConfig.builder()
                    .baseUrl("http://cohere.invalid/")
                    .apiKey("api-key")
                    .modelName("rerank-model")
                    .timeout(Duration.ofSeconds(3))
                    .maxRetries(0)
                    .httpClientBuilderDiscoverServices(false)
                    .proxyDiscoverServices(false)
                    .proxy(socks.proxy())
                    .build();

            var response = config.configuredBuilder()
                    .build()
                    .scoreAll(List.of(TextSegment.from("legacy document")), "legacy query");
            var captured = socks.capturedRequest();

            assertThat(response.content(), contains(0.875));
            assertThat(response.tokenUsage().inputTokenCount(), is(7));
            assertThat(captured.targetHost(), is("cohere.invalid"));
            assertThat(captured.targetPort(), is(80));
            assertThat(captured.httpRequest(), containsString("POST /rerank HTTP/1.1"));
            assertThat(captured.httpRequest(), containsString("Bearer api-key"));
            assertThat(captured.httpRequest(), containsString("rerank-model"));
            assertThat(captured.httpRequest(), containsString("legacy query"));
            assertThat(captured.httpRequest(), containsString("legacy document"));
        }
    }

    @Test
    void testNamedHttpClientBuilderTakesPrecedenceOverSocksProxy(ServiceRegistry registry) {
        // language=YAML
        var yaml = """
                langchain4j:
                  models:
                    test-model:
                      provider: cohere

                  providers:
                    cohere:
                      api-key: api-key
                      proxy.service-registry.named: socksProxy
                      http-client-builder.service-registry.named: customHttpClient
                """;

        var config = CohereScoringModelConfig.builder()
                .serviceRegistry(registry)
                .config(CohereConstants.create(Config.just(ConfigSources.create(yaml, APPLICATION_X_YAML)), MODEL, "test-model"))
                .build();

        var httpClientBuilder = config.httpClientBuilder().orElseThrow();
        assertThat(httpClientBuilder, instanceOf(MockHttpClientFactory.TrackingHttpClientBuilder.class));

        config.configuredBuilder().build();

        assertThat(((MockHttpClientFactory.TrackingHttpClientBuilder) httpClientBuilder).built(), is(true));
    }

    private static ProxySelector proxySelector(Proxy proxy) {
        var httpClientBuilder = (JdkHttpClientBuilder) CohereHttpClientSupport.create(proxy);
        return httpClientBuilder.httpClientBuilder()
                .build()
                .proxy()
                .orElseThrow();
    }
}
