/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.SSLException;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.grpc.core.GrpcSslDescriptor;

import io.grpc.Channel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class GrpcChannelsProviderTest {

    private static final String CLIENT_CERT = "/clientCert.pem";
    private static final String CLIENT_KEY = "/clientKey.pem";
    private static final String CA_CERT = "/ca.pem";

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
        assertThat(cfg.sslDescriptor(), nullValue());
    }

    @Test
    public void testChannelConfigurationWithHost() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder().host("abc.com").build();
        assertThat(cfg.host(), equalTo("abc.com"));
        assertThat(cfg.port(), equalTo(DEFAULT_PORT));
        assertThat(cfg.sslDescriptor(), nullValue());
    }

    @Test
    public void testChannelConfigurationWithPort() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder().port(4096).build();
        assertThat(cfg.host(), equalTo("localhost"));
        assertThat(cfg.port(), equalTo(4096));
        assertThat(cfg.sslDescriptor(), nullValue());
    }

    @Test
    public void testChannelConfigurationWithDefaultSsl() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder()
                .sslDescriptor(GrpcSslDescriptor.builder().build())
                .build();
        assertThat(cfg.host(), equalTo("localhost"));
        assertThat(cfg.port(), equalTo(1408));
        assertThat(cfg.sslDescriptor().isEnabled(), is(true));
    }

    @Test
    public void testChannelConfigurationWithSslConfig() {
        GrpcChannelDescriptor cfg = GrpcChannelDescriptor.builder()
                .sslDescriptor(
                        GrpcSslDescriptor.builder()
                                .tlsCaCert("/certs/cacert")
                                .tlsCert("/certs/clientcert")
                                .tlsKey("/certs/clientkey")
                                .build())
                .build();
        assertThat(cfg.host(), equalTo("localhost"));
        assertThat(cfg.port(), equalTo(1408));
        assertThat(cfg.sslDescriptor().isEnabled(), is(true));
        assertThat(cfg.sslDescriptor().tlsCaCert(), equalTo("/certs/cacert"));
        assertThat(cfg.sslDescriptor().tlsCert(), equalTo("/certs/clientcert"));
        assertThat(cfg.sslDescriptor().tlsKey(), equalTo("/certs/clientkey"));
    }

    @Test
    public void testConfigLoading() {
        String[] expectedChannelConfigNames = new String[] {
                DEFAULT_HOST_PORT_CFG, DEFAULT_HOST_CFG, DEFAULT_PORT_CFG,
                DEFAULT_HOST_SSL_ONE_WAY_CFG, DEFAULT_PORT_SSL_CFG,
                DEFAULT_HOST_PORT_SSL_DISABLED_CFG
        };

        assertThat(grpcConfig.channels().size(), equalTo(expectedChannelConfigNames.length + 1));
        assertThat(grpcConfig.channels().keySet(), hasItems(expectedChannelConfigNames));
        assertThat(grpcConfig.channels().keySet(), hasItems(GrpcChannelsProvider.DEFAULT_CHANNEL_NAME));
    }

    @Test
    public void testDefaultHostPortConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_PORT_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(1408));
        assertThat(chCfg.sslDescriptor(), nullValue());
    }

    @Test
    public void testDefaultHostConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(4096));
        assertThat(chCfg.sslDescriptor(), nullValue());
    }

    @Test
    public void testDefaultPortConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_PORT_CFG);
        assertThat(chCfg.host(), equalTo("non_default_host.com"));
        assertThat(chCfg.port(), equalTo(1408));
        assertThat(chCfg.sslDescriptor(), nullValue());
    }

    @Test
    public void testDefaultHostPortSslDisabledConfig() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_PORT_SSL_DISABLED_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(1408));

        GrpcSslDescriptor ssl = chCfg.sslDescriptor();
        assertThat(ssl, notNullValue());
        assertThat(ssl.isEnabled(), equalTo(false));
        assertThat(ssl.tlsKey(), endsWith(CLIENT_KEY));
        assertThat(ssl.tlsCert(), endsWith(CLIENT_CERT));
        assertThat(ssl.tlsCaCert(), endsWith(CA_CERT));
    }

    @Test
    public void testDefaultHostSslOneWay() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_HOST_SSL_ONE_WAY_CFG);
        assertThat(chCfg.host(), equalTo("localhost"));
        assertThat(chCfg.port(), equalTo(4096));

        GrpcSslDescriptor ssl = chCfg.sslDescriptor();
        assertThat(ssl, notNullValue());
        assertThat(ssl.isEnabled(), equalTo(true));
        assertThat(ssl.tlsKey(), nullValue());
        assertThat(ssl.tlsCert(), nullValue());
        assertThat(ssl.tlsCaCert(), endsWith(CA_CERT));
    }

    @Test
    public void testDefaultPortSsl() {
        GrpcChannelDescriptor chCfg = grpcConfig.channels().get(DEFAULT_PORT_SSL_CFG);
        assertThat(chCfg.host(), equalTo("non_default_host.com"));
        assertThat(chCfg.port(), equalTo(1408));

        GrpcSslDescriptor ssl = chCfg.sslDescriptor();
        assertThat(ssl, notNullValue());
        assertThat(ssl.isEnabled(), equalTo(true));
        assertThat(ssl.tlsKey(), endsWith(CLIENT_KEY));
        assertThat(ssl.tlsCert(), endsWith(CLIENT_CERT));
        assertThat(ssl.tlsCaCert(), endsWith(CA_CERT));
    }

    @Test
    public void testBuilderCreate() throws SSLException {
        assertThat(GrpcChannelsProvider.create().channels().size(), equalTo(1));
        assertThat(GrpcChannelsProvider.create().channel(GrpcChannelsProvider.DEFAULT_CHANNEL_NAME), notNullValue());
    }
}
