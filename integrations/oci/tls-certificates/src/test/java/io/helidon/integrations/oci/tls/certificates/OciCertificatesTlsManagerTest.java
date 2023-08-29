/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Services;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

class OciCertificatesTlsManagerTest {

    @BeforeAll
    static void init() {
        OciTestUtils.init();
    }

    @AfterEach
    void reset() {
        TestOciCertificatesDownloader.callCount_loadCertificates = 0;
        TestOciCertificatesDownloader.callCount_loadCACertificate = 0;
        TestOciPrivateKeyDownloader.callCount = 0;
        TestingCdiExtension.shutdownCalled = false;
    }

//    @Test
    @RepeatedTest(10)
    void serverRuntime() throws Exception {
        Services services = InjectionServices.realizedServices();
        TestingMetricsHelper metrics = services.lookupFirst(TestingMetricsHelper.class).get();
        LifecycleHook lifecycleHook = services.lookupFirst(LifecycleHook.class).get();
        CountDownLatch startup = new CountDownLatch(1);

        lifecycleHook.registerStartupConsumer(c -> startup.countDown());
        Server server = startServer();
        boolean res = startup.await(10, TimeUnit.SECONDS);
        assertThat(res,
                   is(true));
        assertThat(metrics.metrics(),
                   equalTo(2));

        CountDownLatch shutdown = new CountDownLatch(1);
        lifecycleHook.registerShutdownConsumer(c -> shutdown.countDown());

        int certDownloadCountBaseline = TestOciCertificatesDownloader.callCount_loadCertificates;
        int caCertDownloadCountBaseline = TestOciCertificatesDownloader.callCount_loadCACertificate;
        int pkDownloadCountBaseLine = TestOciPrivateKeyDownloader.callCount;
        try {
            assertThat(certDownloadCountBaseline,
                       equalTo(1));
            assertThat(caCertDownloadCountBaseline,
                       equalTo(1));
            assertThat(pkDownloadCountBaseLine,
                       equalTo(1));

            // check metrics that they match what we expect
            TestingMetricsHelper.Gauge checkGauge = metrics.metric(
                    DefaultOciCertificatesTlsManager.TYPE + "." + DefaultOciCertificatesTlsManager.TYPE + ".check");
            assertThat(checkGauge.count(),
                       equalTo(1L));
            assertThat(checkGauge.sum(),
                       greaterThan(0L));
            TestingMetricsHelper.Gauge updateGauge = metrics.metric(
                            DefaultOciCertificatesTlsManager.TYPE + "." + DefaultOciCertificatesTlsManager.TYPE + ".update");
            assertThat(checkGauge.count(),
                       equalTo(1L));
            assertThat(checkGauge.sum(),
                       greaterThan(0L));
        } finally {
            server.stop();
            res = shutdown.await(10, TimeUnit.SECONDS);
            assertThat(res,
                       is(true));
        }

        assertThat(TestingCdiExtension.shutdownCalled, is(true));
    }

    @Test
    void managerCreation() {
        Config tlsManagerConfig = Config.create()
                .get("server.sockets.0.tls.manager.oci-certificates-tls-manager");
        OciCertificatesTlsManagerConfig cfg = OciCertificatesTlsManagerConfig
                .create(tlsManagerConfig);
        OciCertificatesTlsManager tlsManager = OciCertificatesTlsManager.create(cfg);
        assertThat(tlsManager,
                   notNullValue());
    }

    @Test
    void configIsMonitoredForChange() throws Exception {
        TestingConfigSource testingConfigSource =
                new TestingConfigSource(
                        "server.sockets.0.tls.server.sockets.0.tls.manager.oci-certificates-tls-manager.key-password");
        Config config = Config.just(testingConfigSource,
                                    ConfigSources.systemProperties(),
                                    ConfigSources.classpath("application.yaml"));
        assertThat(config.exists(),
                   is(true));
        Config tlsConfig = config.get("server.sockets.0.tls");
        assertThat(tlsConfig.exists(),
                   is(true));

        int certDownloadCountBaseline0 = TestOciCertificatesDownloader.callCount_loadCertificates;
        int caCertDownloadCountBaseline0 = TestOciCertificatesDownloader.callCount_loadCACertificate;
        int pkDownloadCountBaseLine0 = TestOciPrivateKeyDownloader.callCount;
        assertThat("sanity",
                   certDownloadCountBaseline0,
                   equalTo(0));
        assertThat("sanity",
                   caCertDownloadCountBaseline0,
                   equalTo(0));
        assertThat("santiy",
                   pkDownloadCountBaseLine0,
                   equalTo(0));

        Tls tls = Tls.create(tlsConfig);
        assertThat(tls.prototype().manager(),
                   instanceOf(DefaultOciCertificatesTlsManager.class));

        int certDownloadCountBaseline = TestOciCertificatesDownloader.callCount_loadCertificates;
        int caCertDownloadCountBaseline = TestOciCertificatesDownloader.callCount_loadCACertificate;
        int pkDownloadCountBaseLine = TestOciPrivateKeyDownloader.callCount;
        assertThat(certDownloadCountBaseline,
                   equalTo(1));
        assertThat(caCertDownloadCountBaseline,
                   equalTo(1));
        assertThat(pkDownloadCountBaseLine,
                   equalTo(1));

        Config pwdConfig = tlsConfig.get("manager.oci-certificates-tls-manager.key-password");
        assertThat(pwdConfig.exists(),
                   is(true));

        // mutate it
        TimeUnit.MILLISECONDS.sleep(1); // make sure timestamp changes
        testingConfigSource.update("changed");

        // TODO: remove and enhance, but this is just to monitor onChange() in DefaultOciCertificatesDownloader @tlanger
        Thread.sleep(10000);

    }

    static Server startServer() {
        return Server.create().start();
    }

}
