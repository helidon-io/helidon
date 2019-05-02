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
package io.helidon.grpc.core;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class GrpcSslDescriptorTest {

    @Test
    public void testDefaultSslDescriptor() {
        GrpcSslDescriptor desc = GrpcSslDescriptor.builder().build();
        assertThat(desc.isEnabled(), equalTo(true));
        assertThat(desc.isJdkSSL(), equalTo(false));
        assertThat(desc.tlsKey(), nullValue());
        assertThat(desc.tlsCert(), nullValue());
        assertThat(desc.tlsCaCert(), nullValue());
    }

    @Test
    public void testSslDescriptorWithBuilder() {
        GrpcSslDescriptor desc = GrpcSslDescriptor.builder()
                .tlsCaCert("/certs/cacert")
                .tlsCert("/certs/clientcert")
                .tlsKey("/certs/clientkey")
                .enabled(true)
                .jdkSSL(true)
                .build();
        assertThat(desc.isEnabled(), is(true));
        assertThat(desc.isJdkSSL(), is(true));
        assertThat(desc.tlsCaCert(), equalTo("/certs/cacert"));
        assertThat(desc.tlsCert(), equalTo("/certs/clientcert"));
        assertThat(desc.tlsKey(), equalTo("/certs/clientkey"));
    }

    @Test
    public void testLoadFromConfig() {
        Config cfg = Config.create(ConfigSources.classpath("config-ssl.yaml"));
        GrpcSslDescriptor desc = GrpcSslDescriptor.create(cfg.get("grpcserver.ssl"));

        assertThat(desc, notNullValue());
        assertThat(desc.isEnabled(), equalTo(true));
        assertThat(desc.isJdkSSL(), equalTo(false));
        String path = "src/test/resources/ssl/";

        assertThat(desc.tlsKey(), endsWith(path + "serverKey.pem"));
        assertThat(desc.tlsCert(), endsWith(path + "serverCert.pem"));
        assertThat(desc.tlsCaCert(), endsWith(path + "ca.pem"));
    }

}
