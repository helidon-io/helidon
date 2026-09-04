/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.quic;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import io.helidon.common.concurrency.limits.FixedLimit;
import io.helidon.common.concurrency.limits.Limit;
import io.helidon.common.context.Context;
import io.helidon.common.tls.Tls;
import io.helidon.common.uri.UriAuthority;
import io.helidon.http.encoding.ContentEncodingContext;
import io.helidon.http.media.MediaContext;
import io.helidon.quic.QuicConnection;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.ListenerConfig;
import io.helidon.webserver.ListenerTlsContext;
import io.helidon.webserver.Router;
import io.helidon.webserver.SniContext;
import io.helidon.webserver.SniMatchType;
import io.helidon.webserver.TransportBindingContext;
import io.helidon.webserver.http.DirectHandlers;
import io.helidon.webserver.quic.QuicSubProtocolConfigSupport.ResolvedRuntime;
import io.helidon.webserver.quic.spi.QuicSubProtocolConfig;
import io.helidon.webserver.quic.spi.QuicSubProtocolProvider;
import io.helidon.webserver.quic.spi.QuicSubProtocolRuntime;
import io.helidon.webserver.spi.TransportBinding;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QuicSubProtocolConfigTest {
    private static final TransportBindingContext TEST_CONTEXT = new TestTransportBindingContext();
    @SuppressWarnings("rawtypes")
    private static final List<QuicSubProtocolProvider> TEST_PROVIDERS =
            List.of(new FirstTestQuicSubProtocolProvider(), new SecondTestQuicSubProtocolProvider());

    @Test
    void directSubProtocolRequiresQuicBinding() {
        TestQuicSubProtocolConfig config = new TestQuicSubProtocolConfig(
                FirstTestQuicSubProtocolProvider.CONFIG_KEY,
                "alpha",
                true,
                List.of("test-alpha"));

        assertThat(config.transportBindingTypes(), is(Set.of(QuicTransportBindingTypes.QUIC)));
    }

    @Test
    void disabledDirectSubProtocolDoesNotRequireTransportBinding() {
        TestQuicSubProtocolConfig config = new TestQuicSubProtocolConfig(
                FirstTestQuicSubProtocolProvider.CONFIG_KEY,
                "alpha",
                false,
                List.of("test-alpha"));

        assertThat(config.transportBindingTypes(), is(Set.of()));
    }

    @Test
    void createsRuntimesFromInjectedProviders() {
        List<QuicSubProtocolConfig> protocols = List.of(
                new TestQuicSubProtocolConfig(FirstTestQuicSubProtocolProvider.CONFIG_KEY,
                                              "alpha",
                                              true,
                                              List.of("test-alpha")),
                new TestQuicSubProtocolConfig(SecondTestQuicSubProtocolProvider.CONFIG_KEY,
                                              "beta",
                                              true,
                                              List.of("test-beta")));

        List<ResolvedRuntime> runtimes =
                QuicSubProtocolConfigSupport.createRuntimes(TEST_CONTEXT, protocols, TEST_PROVIDERS);

        assertThat(runtimes, hasSize(2));
        assertThat(runtimes.stream().map(ResolvedRuntime::config).toList(),
                   contains(protocols.get(0), protocols.get(1)));
    }

    @Test
    void rejectsMissingInjectedProvider() {
        TestQuicSubProtocolConfig protocol = new TestQuicSubProtocolConfig(
                "missing",
                "alpha",
                true,
                List.of("test-alpha"));

        UnsupportedOperationException failure = assertThrows(
                UnsupportedOperationException.class,
                () -> QuicSubProtocolConfigSupport.createRuntimes(TEST_CONTEXT,
                                                                  List.of(protocol),
                                                                  TEST_PROVIDERS));

        assertThat(failure.getMessage(), containsString("No QUIC protocol provider is available"));
        assertThat(failure.getMessage(), containsString("missing(alpha)"));
    }

    @Test
    void rejectsDuplicateInjectedProviders() {
        TestQuicSubProtocolConfig protocol = new TestQuicSubProtocolConfig(
                FirstTestQuicSubProtocolProvider.CONFIG_KEY,
                "alpha",
                true,
                List.of("test-alpha"));
        @SuppressWarnings("rawtypes")
        List<QuicSubProtocolProvider> duplicateProviders =
                List.of(new FirstTestQuicSubProtocolProvider(), new FirstTestQuicSubProtocolProvider());

        UnsupportedOperationException failure = assertThrows(
                UnsupportedOperationException.class,
                () -> QuicSubProtocolConfigSupport.createRuntimes(TEST_CONTEXT,
                                                                  List.of(protocol),
                                                                  duplicateProviders));

        assertThat(failure.getMessage(), containsString("Multiple QUIC protocol providers match"));
        assertThat(failure.getMessage(), containsString("first-test(alpha)"));
    }

    @Test
    void emptyDirectSubProtocolListIsRejected() {
        UnsupportedOperationException failure = assertThrows(
                UnsupportedOperationException.class,
                () -> QuicSubProtocolConfigSupport.createRuntimes(TEST_CONTEXT, List.of(), TEST_PROVIDERS));

        assertThat(failure.getMessage(), containsString("no QUIC protocols are enabled"));
    }

    @Test
    void duplicateAlpnsAreRejected() {
        List<ResolvedRuntime> runtimes = QuicSubProtocolConfigSupport.createRuntimes(
                TEST_CONTEXT,
                List.of(new TestQuicSubProtocolConfig(FirstTestQuicSubProtocolProvider.CONFIG_KEY,
                                                      "alpha",
                                                      true,
                                                      List.of("test-duplicate")),
                        new TestQuicSubProtocolConfig(SecondTestQuicSubProtocolProvider.CONFIG_KEY,
                                                      "beta",
                                                      true,
                                                      List.of("test-duplicate"))),
                TEST_PROVIDERS);

        UnsupportedOperationException failure = assertThrows(
                UnsupportedOperationException.class,
                () -> QuicSubProtocolConfigSupport.resolveAlpns("test-listener", runtimes, List.of()));

        assertThat(failure.getMessage(), containsString("duplicate QUIC ALPN \"test-duplicate\""));
        assertThat(failure.getMessage(), containsString("first-test(alpha)"));
        assertThat(failure.getMessage(), containsString("second-test(beta)"));
    }

    @Test
    void preservesImmutableDefaultAndExplicitAlpnPreferenceOrder() {
        List<ResolvedRuntime> runtimes = runtimesWithMultipleAlpns();
        List<ResolvedRuntime> reversedRuntimes = List.of(runtimes.getLast(), runtimes.getFirst());

        SequencedMap<String, ResolvedRuntime> defaultOrder =
                QuicSubProtocolConfigSupport.resolveAlpns("test-listener", runtimes, List.of());
        SequencedMap<String, ResolvedRuntime> reversedDefaultOrder =
                QuicSubProtocolConfigSupport.resolveAlpns("test-listener", reversedRuntimes, List.of());
        SequencedMap<String, ResolvedRuntime> explicitOrder =
                QuicSubProtocolConfigSupport.resolveAlpns("test-listener",
                                                          reversedRuntimes,
                                                          List.of("beta",
                                                                  "alpha-fallback",
                                                                  "alpha-preferred"));

        assertThat(defaultOrder.sequencedKeySet(), contains("alpha-preferred", "alpha-fallback", "beta"));
        assertThat(reversedDefaultOrder.sequencedKeySet(), contains("alpha-preferred", "alpha-fallback", "beta"));
        assertThat(explicitOrder.sequencedKeySet(), contains("beta", "alpha-fallback", "alpha-preferred"));
        assertThrows(UnsupportedOperationException.class,
                     () -> reversedDefaultOrder.put("other", runtimes.getFirst()));
        assertThrows(UnsupportedOperationException.class, () -> explicitOrder.put("other", runtimes.getFirst()));
    }

    @Test
    void rejectsInvalidExplicitAlpnPreference() {
        List<ResolvedRuntime> runtimes = runtimesWithMultipleAlpns();

        UnsupportedOperationException duplicate = assertThrows(
                UnsupportedOperationException.class,
                () -> QuicSubProtocolConfigSupport.resolveAlpns(
                        "test-listener",
                        runtimes,
                        List.of("alpha-preferred", "alpha-preferred", "beta")));
        UnsupportedOperationException unknown = assertThrows(
                UnsupportedOperationException.class,
                () -> QuicSubProtocolConfigSupport.resolveAlpns(
                        "test-listener",
                        runtimes,
                        List.of("alpha-preferred", "alpha-fallback", "unknown")));
        UnsupportedOperationException missing = assertThrows(
                UnsupportedOperationException.class,
                () -> QuicSubProtocolConfigSupport.resolveAlpns(
                        "test-listener",
                        runtimes,
                        List.of("alpha-preferred", "beta")));

        assertThat(duplicate.getMessage(), containsString("duplicate QUIC ALPN preference \"alpha-preferred\""));
        assertThat(unknown.getMessage(), containsString("unknown QUIC ALPN preference \"unknown\""));
        assertThat(missing.getMessage(), containsString("omits registered identifiers [alpha-fallback]"));
    }

    private static List<ResolvedRuntime> runtimesWithMultipleAlpns() {
        return QuicSubProtocolConfigSupport.createRuntimes(
                TEST_CONTEXT,
                List.of(new TestQuicSubProtocolConfig(FirstTestQuicSubProtocolProvider.CONFIG_KEY,
                                                      "alpha",
                                                      true,
                                                      List.of("alpha-preferred", "alpha-fallback")),
                        new TestQuicSubProtocolConfig(SecondTestQuicSubProtocolProvider.CONFIG_KEY,
                                                      "beta",
                                                      true,
                                                      List.of("beta"))),
                TEST_PROVIDERS);
    }

    public static final class FirstTestQuicSubProtocolProvider extends AbstractTestQuicSubProtocolProvider {
        static final String CONFIG_KEY = "first-test";

        public FirstTestQuicSubProtocolProvider() {
            super(CONFIG_KEY);
        }
    }

    public static final class SecondTestQuicSubProtocolProvider extends AbstractTestQuicSubProtocolProvider {
        static final String CONFIG_KEY = "second-test";

        public SecondTestQuicSubProtocolProvider() {
            super(CONFIG_KEY);
        }
    }

    private record TestQuicSubProtocolConfig(String type,
                                             String name,
                                             boolean enabled,
                                             List<String> alpnIds) implements QuicSubProtocolConfig {
    }

    private abstract static class AbstractTestQuicSubProtocolProvider
            implements QuicSubProtocolProvider<TestQuicSubProtocolConfig> {
        private final String configKey;

        private AbstractTestQuicSubProtocolProvider(String configKey) {
            this.configKey = configKey;
        }

        @Override
        public String configKey() {
            return configKey;
        }

        @Override
        public Class<TestQuicSubProtocolConfig> protocolConfigType() {
            return TestQuicSubProtocolConfig.class;
        }

        @Override
        public QuicSubProtocolRuntime create(TransportBindingContext context,
                                             TestQuicSubProtocolConfig config) {
            return new QuicSubProtocolRuntime() {
                @Override
                public List<String> alpnIds() {
                    return config.alpnIds();
                }

                @Override
                public void accept(QuicConnection connection) {
                }
            };
        }
    }

    private static final class TestTransportBindingContext implements TransportBindingContext, ListenerContext {
        private final Limit requestLimit = FixedLimit.create();
        private final Limit connectionLimit = FixedLimit.create();

        @Override
        public SocketAddress configuredAddress() {
            return new InetSocketAddress(config().address(), Math.max(0, config().port()));
        }

        @Override
        public ListenerContext listenerContext() {
            return this;
        }

        @Override
        public Router router() {
            return Router.empty();
        }

        @Override
        public Limit requestLimit() {
            return requestLimit;
        }

        @Override
        public Limit connectionLimit() {
            return connectionLimit;
        }

        @Override
        public ListenerTlsContext listenerTls() {
            return new TestListenerTlsContext(config());
        }

        @Override
        public void fatalBindingFailure(TransportBinding binding, Throwable cause) {
            throw new IllegalStateException("Fatal binding failure", cause);
        }

        @Override
        public Context context() {
            return Context.create();
        }

        @Override
        public MediaContext mediaContext() {
            return MediaContext.create();
        }

        @Override
        public ContentEncodingContext contentEncodingContext() {
            return ContentEncodingContext.create();
        }

        @Override
        public DirectHandlers directHandlers() {
            return DirectHandlers.create();
        }

        @Override
        public ListenerConfig config() {
            return ListenerConfig.create();
        }

        @Override
        public ExecutorService executor() {
            throw new UnsupportedOperationException("executor() should not be used by this test");
        }
    }

    private record TestListenerTlsContext(ListenerConfig listenerConfig) implements ListenerTlsContext {
        @Override
        public Tls tls() {
            return listenerConfig.tls().orElseGet(() -> Tls.builder().enabled(false).build());
        }

        @Override
        public boolean virtualHostsEnabled() {
            return false;
        }

        @Override
        public void validateVirtualHosts() {
        }

        @Override
        public Selection select(String presentedHost) {
            return Selection.create(tls(),
                                    new TestSniContext(Optional.of(presentedHost),
                                                       Optional.empty(),
                                                       SniMatchType.FALLBACK_UNMATCHED));
        }

        @Override
        public Selection selectWithoutSni() {
            return Selection.create(tls(),
                                    new TestSniContext(Optional.empty(), Optional.empty(), SniMatchType.FALLBACK_MISSING));
        }
    }

    private record TestSniContext(Optional<String> presentedHost,
                                  Optional<String> matchedHost,
                                  SniMatchType matchType) implements SniContext {
        @Override
        public AuthorityCheck checkAuthority(UriAuthority authority) {
            return AuthorityCheck.ALLOWED;
        }
    }
}
