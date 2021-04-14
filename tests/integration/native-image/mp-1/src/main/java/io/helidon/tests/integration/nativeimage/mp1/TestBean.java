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
package io.helidon.tests.integration.nativeimage.mp1;

import java.net.URI;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.Dependent;
import javax.enterprise.inject.spi.BeanManager;
import javax.inject.Inject;

import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.tests.integration.nativeimage.mp1.other.BeanProcessor;
import io.helidon.tests.integration.nativeimage.mp1.other.ProducedBean;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Simple bean.
 */
@Dependent
public class TestBean {
    @Inject
    @ConfigProperty(name = "app.message")
    private String message;

    @Inject
    @RestClient
    private RestClientIface restClientInjected;

    @Inject
    private BeanManager beanManager;

    @Inject
    private ProducedBean producedBean;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry baseRegistry;

    private final AtomicInteger retries = new AtomicInteger();

    @Timed
    public String config() {
        return message;
    }

    private RestClientIface restClient() {
        int port = beanManager.getExtension(ServerCdiExtension.class)
                .port();
        return RestClientBuilder.newBuilder()
                .baseUri(URI.create("http://localhost:" + port + "/cdi"))
                .build(RestClientIface.class);
    }

    public String restClientMessage() {
        return restClient().message();
    }

    public String restClientBeanType() {
        return restClient().beanType();
    }

    public String restClientJsonP() {
        return restClient()
                .jsonProcessing()
                .getString("message");
    }

    public String restClientJsonB() {
        return restClient()
                .jsonBinding()
                .getMessage();
    }

    public String restClientQuery(Long param) {
        return restClient()
                .queryParam(param);
    }

    public String restClientQuery(Boolean param) {
        return restClient()
                .queryParam(param);
    }

    @Fallback(fallbackMethod = "fallbackTo")
    public String fallback() {
        throw new RuntimeException("intentional failure");
    }

    public String fallbackTo() {
        return "Fallback success";
    }

    @Retry
    public String retry() {
        if (retries.incrementAndGet() < 3) {
            System.out.println("Failing for the " + retries.get() + ". time");
            throw new RuntimeException("Failed");
        }
        return "Success on " + retries.get() + ". try";
    }

    @Timeout(value = 1, unit = ChronoUnit.SECONDS)
    public String timeout() {
        long t = System.currentTimeMillis();
        try {
            Thread.currentThread().sleep(3000);
        } catch (InterruptedException e) {
            t = System.currentTimeMillis() - t;
            System.err.println("ABean.timeout() has been interrupted after " + t + " millis");
        }
        return "This method should have timed out after 1 second";
    }

    @Asynchronous
    public CompletionStage<String> asynchronous() {
        return CompletableFuture.completedFuture("Async response");
    }

    public String produced() {
        return BeanProcessor.getProducedName(producedBean);
    }

    public String appRegistry() {
        return "SimpleTimers.size(): " + metricRegistry.getSimpleTimers().size();
    }

    public String baseRegistry() {
        return "SimpleTimers.size(): " + metricRegistry.getSimpleTimers().size();
    }
}
