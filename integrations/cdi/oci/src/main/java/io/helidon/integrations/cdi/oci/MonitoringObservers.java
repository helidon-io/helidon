/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.integrations.cdi.oci;

import java.io.IOException;
import java.net.URI;

import javax.annotation.Priority;
import javax.enterprise.event.Observes;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.UriBuilder;

import com.oracle.bmc.http.ClientConfigurator;
import com.oracle.bmc.monitoring.MonitoringAsyncClient;
import com.oracle.bmc.monitoring.MonitoringClient;

import static javax.interceptor.Interceptor.Priority.PLATFORM_BEFORE;

final class MonitoringObservers {


    /*
     * Constructors.
     */


    @Deprecated
    private MonitoringObservers() {
        super();
        throw new AssertionError();
    }


    /*
     * Observer methods.
     */


    private static void customizeBuilder(@Observes @Priority(PLATFORM_BEFORE) MonitoringClient.Builder builder) {
        builder.additionalClientConfigurator(PostMetricDataClientConfigurator.INSTANCE);
    }

    private static void customizeAsyncBuilder(@Observes @Priority(PLATFORM_BEFORE) MonitoringAsyncClient.Builder builder) {
        builder.additionalClientConfigurator(PostMetricDataClientConfigurator.INSTANCE);
    }


    /*
     * Inner and nested classes.
     */


    private static class PostMetricDataClientConfigurator implements ClientConfigurator {


        /*
         * Static fields.
         */


        private static final PostMetricDataClientConfigurator INSTANCE = new PostMetricDataClientConfigurator();


        /*
         * Constructors.
         */


        private PostMetricDataClientConfigurator() {
            super();
        }


        /*
         * Instance methods.
         */


        @Override
        public final void customizeBuilder(ClientBuilder builder) {
            builder.register(PostMetricDataClientRequestFilter.INSTANCE,
                             PostMetricDataClientRequestFilter.PRE_AUTHENTICATION_PRIORITY);
        }

        @Override
        public final void customizeClient(Client client) {
        }


        /*
         * Inner and nested classes.
         */


        private static class PostMetricDataClientRequestFilter implements ClientRequestFilter {


            /*
             * Static fields.
             */


            private static final PostMetricDataClientRequestFilter INSTANCE = new PostMetricDataClientRequestFilter();

            private static final int PRE_AUTHENTICATION_PRIORITY = Priorities.AUTHENTICATION - 500; // 1000 - 500 = 500


            /*
             * Constructors.
             */


            private PostMetricDataClientRequestFilter() {
                super();
            }


            /*
             * Instance methods.
             */


            @Override
            public final void filter(ClientRequestContext clientRequestContext) throws IOException {
                // https://docs.oracle.com/en-us/iaas/tools/java/2.18.0/com/oracle/bmc/monitoring/MonitoringAsync.html#postMetricData-com.oracle.bmc.monitoring.requests.PostMetricDataRequest-com.oracle.bmc.responses.AsyncHandler-
                //
                // "The endpoints for this [particular POST] operation
                // differ from other Monitoring operations. Replace
                // the string telemetry with telemetry-ingestion in
                // the endpoint, as in the following example:
                // https://telemetry-ingestion.eu-frankfurt-1.oraclecloud.com"
                //
                // Doing this in an application that uses a
                // MonitoringClient or a MonitoringAsyncClient from
                // several threads, not all of which are POSTing, is,
                // of course, unsafe.  This filter repairs this flaw
                // and is installed by OCI-SDK-supported client
                // customization facilities.
                //
                // The documented instructions above are imprecise.
                // This filter implements what it seems was meant.
                //
                // The intent seems to be to replace "telemetry." with
                // "telemetry-ingestion.", and even that could run
                // afoul of things in the future (consider
                // "super-telemetry.oraclecloud.com" where these rules
                // might not be intended to apply).
                //
                // Additionally, we want to guard against a future
                // where the hostname might *already* have
                // "telemetry-ingestion" in it, so clearly we cannot
                // simply replace "telemetry", wherever it occurs,
                // with "telemetry-ingestion".
                //
                // So we reinterpret the above to mean: "In the
                // hostname, replace the first occurrence of the
                // String matching the regex ^telemetry\. with
                // telemetry-ingestion." (but without using regexes,
                // because they're overkill in this situation).
                //
                // This filter is written defensively with respect to
                // nulls to ensure maximum non-interference: it gets
                // out of the way at the first sign of trouble.
                //
                // This method is safe for concurrent use by multiple
                // threads.
                if ("POST".equalsIgnoreCase(clientRequestContext.getMethod())) {
                    URI uri = clientRequestContext.getUri();
                    if (uri != null) {
                        String host = uri.getHost();
                        if (host != null && host.startsWith("telemetry.")) {
                            String path = uri.getPath();
                            if (path != null && path.endsWith("/metrics")) {
                                clientRequestContext.setUri(UriBuilder.fromUri(uri)
                                                            .host("telemetry-ingestion." + host.substring("telemetry.".length()))
                                                            .build());
                            }
                        }
                    }
                }
            }

        }

    }

}
