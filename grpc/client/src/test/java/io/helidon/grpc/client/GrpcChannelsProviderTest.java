/*
 * Copyright (c) 2019, 2021 Oracle and/or its affiliates.
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
package io.helidon.grpc.client;

import java.util.Optional;

import io.helidon.common.configurable.Resource;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.grpc.core.GrpcTlsDescriptor;

import io.grpc.Channel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;

public class GrpcChannelsProviderTest {

    private static final String CLIENT_CERT = "ssl/clientCert.pem";
    private static final String CLIENT_KEY = "ssl/clientKey.pem";
    private static final String CA_CERT = "ssl/ca.pem";

    private static final String DEFAULT_HOST_PORT_CFG = "default_host_port";
    private static final String DEFAULT_HOST_CFG = "default_host";
    private static final String DEFAULT_PORT_CFG = "default_port";
    private static final String DEFAULT_HOST_PORT_SSL_DISABLED_CFG = "default_host_port_ssl_disabled";
    private static final String DEFAULT_HOST_SSL_ONE_WAY_CFG = "default_host_ssl_one_way";
    private static final String DEFAULT_PORT_SSL_CFG = "default_port_ssl";
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1408;
    private static GrpcChannelsProvider grpcConfig;

    @BeforeAll
    public static void initGrpcConfig() {
        Config cfg = Config.create(ConfigSources.classpath("test-client-config.yaml"));
        grpcConfig = GrpcChannelsProvider.create(cfg.get("grpc"));
    }

    @Test
    public void shouldBuildWithoutChannelsConfig() {
        GrpcChannelsProvider provider = GrpcChannelsProvider.create(Config.empty());

        // should only have the default channel
        Channel channel = provider.channel(GrpcChannelsProvider.DEFAULT_CHANNEL_NAME);
        assertThat(channel, is(notNullValue()));
    }

    @Test
    public void testDefaultChannelConfiguration() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder().build();
        assertThat(cfg.host(), equalTo(DEFAULT_HOST));
        assertThat(cfg.port(), equalTo(DEFAULT_PORT));
        assertThat(cfg.tlsDescriptor().isPresent(), is(false));
        assertThat(cfg.target().isPresent(), is(false));
        assertThat(cfg.loadBalancerPolicy().isPresent(), is(false));
        assertThat(cfg.nameResolverFactory().isPresent(), is(false));
    }

    @Test
    public void testChannelConfigurationWithHost() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder().host("abc.com").build();
        assertThat(cfg.host(), equalTo("abc.com"));
        assertThat(cfg.port(), equalTo(DEFAULT_PORT));
        assertThat(cfg.tlsDescriptor().isPresent(), is(false));
        assertThat(cfg.target().isPresent(), is(false));
        assertThat(cfg.loadBalancerPolicy().isPresent(), is(false));
        assertThat(cfg.nameResolverFactory().isPresent(), is(false));
    }

    @Test
    public void testChannelConfigurationWithPort() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder().port(4096).build();
        assertThat(cfg.host(), equalTo("localhost"));
        assertThat(cfg.port(), equalTo(4096));
        assertThat(cfg.tlsDescriptor().isPresent(), is(false));
        assertThat(cfg.target().isPresent(), is(false));
        assertThat(cfg.loadBalancerPolicy().isPresent(), is(false));
        assertThat(cfg.nameResolverFactory().isPresent(), is(false));
    }

    @Test
    public void testChannelConfigurationWithDefaultSsl() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder()
                .sslDescriptor(GrpcTlsDescriptor.builder().build())
                .build();
        assertThat(cfg.host(), equalTo("localhost"));
        assertThat(cfg.port(), equalTo(1408));
        Optional<GrpcTlsDescriptor> descriptor = cfg.tlsDescriptor();
        assertThat(descriptor.isPresent(), is(true));
        assertThat(descriptor.get().isEnabled(), is(true));
        assertThat(cfg.target().isPresent(), is(false));
        assertThat(cfg.loadBalancerPolicy().isPresent(), is(false));
        assertThat(cfg.nameResolverFactory().isPresent(), is(false));
    }

    @Test
    public void testChannelConfigurationWithSslConfig() {
        Resource certResource = mock(Resource.class);
        Resource keyResource = mock(Resource.class);
        Resource trustResource = mock(Resource.class);

        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder()
                .sslDescriptor(
                        GrpcTlsDescriptor.builder()
                                .tlsCaCert(trustResource)
                                .tlsCert(certResource)
                                .tlsKey(keyResource)
                                .build())
                .build();
        assertThat(cfg.host(), equalTo("localhost"));
        assertThat(cfg.port(), equalTo(1408));
        Optional<GrpcTlsDescriptor> descriptor = cfg.tlsDescriptor();
        assertThat(descriptor.isPresent(), is(true));
        GrpcTlsDescriptor tlsDescriptor = descriptor.get();
        assertThat(tlsDescriptor.isEnabled(), is(true));
        assertThat(tlsDescriptor.tlsCaCert(), is(sameInstance(trustResource)));
        assertThat(tlsDescriptor.tlsCert(), is(sameInstance(certResource)));
        assertThat(tlsDescriptor.tlsKey(), is(sameInstance(keyResource)));
    }

    @Test
    public void testChannelWithTarget() {
        String target = "dns://127.0.0.1:22";
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder()
                .target(target)
                .build();
        assertThat(cfg.target().isPresent(), is(true));
        assertThat(cfg.target().get(), is(target));
    }

    @Test
    public void testChannelWithLoadBalancer() {
        String policy = "round-robin";
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder()
                .loadBalancerPolicy(policy)
                .build();
        assertThat(cfg.loadBalancerPolicy().isPresent(), is(true));
        assertThat(cfg.loadBalancerPolicy().get(), is(policy));
    }

    @Test
    public void testConfigLoading() {
        String[] expectedChannelConfigNames = new String[] {
                DEFAULT_HOST_PORT_CFG, DEFAULT_HOST_CFG, DEFAULT_PORT_CFG,
                DEFAULT_HOST_SSL_ONE_WAY_CFG, DEFAULT_PORT_SSL_CFG,
                DEFAULT_HOST_PORT_SSL_DISABLED_CFG
        };

        assertThat(grpcConfig.channels().size(), equalTo(expectedChannelConfigNames.length + 2));
        assertThat(grpcConfig.channels().keySet(), hasItems(expectedChannelConfigNames));
        assertThat(grpcConfig.channels().keySet(), hasItems(GrpcChannelsProvider.DEFAULT_CHANNEL_NAME));
    }

    @Test
    public void testDefaultHostPortConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_PORT_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(1408));
        assertThat(chCfg.tlsDescriptor().isPresent(), is(false));
        assertThat(chCfg.target().isPresent(), is(false));
        assertThat(chCfg.loadBalancerPolicy().isPresent(), is(false));
        assertThat(chCfg.nameResolverFactory().isPresent(), is(false));
    }

    @Test
    public void testDefaultHostConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(4096));
        assertThat(chCfg.tlsDescriptor().isPresent(), is(false));
        assertThat(chCfg.target().isPresent(), is(false));
        assertThat(chCfg.loadBalancerPolicy().isPresent(), is(false));
        assertThat(chCfg.nameResolverFactory().isPresent(), is(false));
    }

    @Test
    public void testDefaultPortConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_PORT_CFG);
        assertThat(chCfg.host(), equalTo("non_default_host.com"));
        assertThat(chCfg.port(), equalTo(1408));
        assertThat(chCfg.tlsDescriptor().isPresent(), is(false));
        assertThat(chCfg.target().isPresent(), is(false));
        assertThat(chCfg.loadBalancerPolicy().isPresent(), is(false));
        assertThat(chCfg.nameResolverFactory().isPresent(), is(false));
    }

    @Test
    public void testDefaultHostPortSslDisabledConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_PORT_SSL_DISABLED_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(1408));

        Optional<GrpcTlsDescriptor> descriptor = chCfg.tlsDescriptor();
        assertThat(descriptor.isPresent(), is(false));
    }

    @Test
    public void testDefaultHostSslOneWay() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_SSL_ONE_WAY_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(4096));

        Resource trustResource = Resource.create(CA_CERT);

        Optional<GrpcTlsDescriptor> descriptor = chCfg.tlsDescriptor();
        assertThat(descriptor.isPresent(), is(true));
        GrpcTlsDescriptor ssl = descriptor.get();
        assertThat(ssl, notNullValue());
        assertThat(ssl.isEnabled(), equalTo(true));
        assertThat(ssl.tlsKey(), nullValue());
        assertThat(ssl.tlsCert(), nullValue());
        assertThat(ssl.tlsCaCert(), is(notNullValue()));
        assertThat(ssl.tlsCaCert().location(), endsWith(trustResource.location()));
    }

    @Test
    public void testDefaultPortSsl() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_PORT_SSL_CFG);
        assertThat(chCfg.host(), equalTo("non_default_host.com"));
        assertThat(chCfg.port(), equalTo(1408));

        Resource keyResource = Resource.create(CLIENT_KEY);
        Resource certResource = Resource.create(CLIENT_CERT);
        Resource trustResource = Resource.create(CA_CERT);

        Optional<GrpcTlsDescriptor> descriptor = chCfg.tlsDescriptor();
        assertThat(descriptor.isPresent(), is(true));
        GrpcTlsDescriptor ssl = descriptor.get();
        assertThat(ssl, notNullValue());
        assertThat(ssl.isEnabled(), equalTo(true));
        assertThat(ssl.tlsKey(), is(notNullValue()));
        assertThat(ssl.tlsKey().location(), is(keyResource.location()));
        assertThat(ssl.tlsCert(), is(notNullValue()));
        assertThat(ssl.tlsCert().location(), endsWith(certResource.location()));
        assertThat(ssl.tlsCaCert(), is(notNullValue()));
        assertThat(ssl.tlsCaCert().location(), endsWith(trustResource.location()));
    }

    @Test
    public void testBuilderCreate() {
        assertThat(GrpcChannelsProvider.create().channels().size(), equalTo(1));
        assertThat(GrpcChannelsProvider.create().channel(GrpcChannelsProvider.DEFAULT_CHANNEL_NAME), notNullValue());
    }
}
