/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webclient.tests;

import java.util.List;

import io.helidon.security.Security;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientConfig;
import io.helidon.webclient.security.WebClientSecurity;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

public class WebClientServiceTest {

    @Test
    public void noServiceRegistered() {
        Http1Client client = Http1Client.builder()
                .servicesDiscoverServices(false)
                .build();
        Http1ClientConfig config = client.prototype();

        assertThat(config.services(), is(empty()));
    }

    @Test
    public void noServiceAutomaticallyDiscovered() {
        Security security = Security.builder().build();
        WebClientSecurity webClientSecurity = WebClientSecurity.create(security);
        
        Http1Client client = Http1Client.builder()
                .servicesDiscoverServices(false)
                .addService(webClientSecurity)
                .build();
        Http1ClientConfig config = client.prototype();

        assertThat(config.services(), is(List.of(webClientSecurity)));
    }

    @Test
    public void servicesFound() {
        Http1Client client = Http1Client.builder().build();
        Http1ClientConfig config = client.prototype();

        assertThat(config.services(), is(not(empty())));
    }


    @Test
    public void servicesNotDuplicated() {
        Http1Client client = Http1Client.builder().build();
        Http1ClientConfig config = client.prototype();

        assertThat(config.services(), is(not(empty())));

        int numberOfServicesFound = config.services().size();

        Security security = Security.builder().build();
        WebClientSecurity webClientSecurity = WebClientSecurity.create(security);

        client = Http1Client.builder()
                .addService(webClientSecurity)
                .build();
        config = client.prototype();

        //The number of services has to match. otherwise there has been duplication
        assertThat(config.services().size(), is(numberOfServicesFound));
        assertThat(config.services(), hasItem(webClientSecurity));
    }

}
