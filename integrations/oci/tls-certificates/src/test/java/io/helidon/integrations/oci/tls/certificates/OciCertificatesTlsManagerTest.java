/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.net.ssl.X509KeyManager;

import io.helidon.common.tls.Tls;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.scheduling.Task;
import io.helidon.scheduling.TaskManager;
import io.helidon.service.registry.GlobalServiceRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// see pom.xml for system properties that can be used in these tests
class OciCertificatesTlsManagerTest {
    private TaskManager taskManager;
    private Set<Task> existingTasks;

    @BeforeEach
    void recordExistingTasks() {
        taskManager = GlobalServiceRegistry.registry().get(TaskManager.class);
        existingTasks = Set.copyOf(taskManager.tasks());
    }

    @AfterEach
    void reset() {
        closeTasksCreatedSince(taskManager, existingTasks);
        TestOciCertificatesDownloader.version = "1";
        TestOciCertificatesDownloader.caCertificateResource = "test-keys/ca.pem";
        TestOciCertificatesDownloader.callCount_loadCertificates = 0;
        TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey = 0;
        TestOciCertificatesDownloader.callCount_loadCACertificate = 0;
        TestOciCertificatesDownloader.managedDelayMillis = 0;
        TestOciCertificatesDownloader.managedFailure = null;
        TestOciCertificatesDownloader.caFailure = null;
        TestOciCertificatesDownloader.clearCaScript();
        TestOciPrivateKeyDownloader.callCount = 0;
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
    void managedBundleManagerIsCreatedFromTlsConfig() {
        Config tlsConfig = Config.just(ConfigSources.create(Map.of(
                "manager.oci-certificate-bundle-tls-manager.schedule", "0 * * * * ? 2099",
                "manager.oci-certificate-bundle-tls-manager.ca-ocid", "test-ca",
                "manager.oci-certificate-bundle-tls-manager.cert-ocid", "test-cert")));

        Tls tls = Tls.create(tlsConfig);

        assertThat(tls.prototype().manager(), instanceOf(DefaultOciCertificateBundleTlsManager.class));
        assertThat(tls.prototype().manager().type(), is("oci-certificate-bundle-tls-manager"));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, is(1));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, is(1));
        assertThat(TestOciPrivateKeyDownloader.callCount, is(0));
    }

    @Test
    void configIsMonitoredForChange() throws Exception {
        TestingConfigSource testingConfigSource =
                new TestingConfigSource(
                        "server.sockets.0.tls.manager.oci-certificates-tls-manager.key-password");
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
        assertThat("sanity",
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
        testingConfigSource.update("changed");
        assertThat(config.context().last()
                           .get("server.sockets.0.tls.manager.oci-certificates-tls-manager.key-password").asString().asOptional(),
                   is(Optional.of("changed")));

        // Config change delivery is asynchronous. Wait for the legacy Vault refresh to finish so it cannot
        // leak downloader calls into a subsequent test after the static counters have been reset.
        long deadline = System.nanoTime() + 5_000_000_000L;
        while ((TestOciCertificatesDownloader.callCount_loadCACertificate < caCertDownloadCountBaseline + 1
                || TestOciPrivateKeyDownloader.callCount < pkDownloadCountBaseLine + 1)
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificates, equalTo(certDownloadCountBaseline + 1));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(caCertDownloadCountBaseline + 1));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(pkDownloadCountBaseLine + 1));
    }

    @Test
    void legacyVaultReloadsWhenVersionIsUnchanged() {
        DefaultOciCertificatesTlsManager manager = newVaultManager();

        assertThat(TestOciCertificatesDownloader.callCount_loadCertificates, equalTo(1));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(1));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(1));

        assertTrue(manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificates, equalTo(2));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(2));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(2));
    }

    @Test
    void managedBundleLoadsAndRotatesWithoutVaultKeyDownloader() {
        DefaultOciCertificateBundleTlsManager manager = newBundleManager();

        assertThat(TestOciCertificatesDownloader.callCount_loadCertificates, equalTo(0));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, equalTo(1));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(1));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(0));
        assertThat(privateKeyAlgorithm(manager), is("RSA"));

        assertFalse(manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(2));

        TestOciCertificatesDownloader.version = "2";
        assertTrue(manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(3));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(0));
        assertThat(privateKeyAlgorithm(manager), is("EC"));

        assertFalse(manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(4));
    }

    @Test
    void managedBundleCanReloadWhenVersionIsUnchanged() {
        DefaultOciCertificateBundleTlsManager manager = newBundleManager(true);

        assertTrue(manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, equalTo(2));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(2));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(0));
        assertThat(privateKeyAlgorithm(manager), is("RSA"));
    }

    @Test
    void failedManagedRotationKeepsVersionRetryable() {
        DefaultOciCertificateBundleTlsManager manager = newBundleManager();
        TestOciCertificatesDownloader.version = "2";
        TestOciCertificatesDownloader.managedFailure = new IllegalStateException("synthetic managed download failure");

        assertThrows(IllegalStateException.class, () -> manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(1));
        assertThat(privateKeyAlgorithm(manager), is("RSA"));

        TestOciCertificatesDownloader.managedFailure = null;
        assertTrue(manager.loadContext(false));
        assertThat(privateKeyAlgorithm(manager), is("EC"));
        assertFalse(manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(3));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(0));
    }

    @Test
    void failureAfterManagedIdentityAcquisitionKeepsIdentityAndVersionRetryable() {
        DefaultOciCertificateBundleTlsManager manager = newBundleManager();
        TestOciCertificatesDownloader.version = "2";
        TestOciCertificatesDownloader.caFailure = new IllegalStateException("synthetic CA download failure");

        assertThrows(IllegalStateException.class, () -> manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, equalTo(2));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(2));
        assertThat(privateKeyAlgorithm(manager), is("RSA"));

        TestOciCertificatesDownloader.caFailure = null;
        assertTrue(manager.loadContext(false));
        assertThat(privateKeyAlgorithm(manager), is("EC"));
        assertFalse(manager.loadContext(false));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(4));
        assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(0));
    }

    @Test
    void caOnlyRotationFailuresAreLoggedAndRetried() throws Exception {
        RuntimeException[] failures = {
                new UnsupportedOperationException("secret unsupported detail"),
                new IllegalArgumentException("secret material"),
                new IllegalStateException("secret download detail"),
                new RuntimeException("secret runtime detail")
        };
        String[] failureCategories = {
                "unsupported-operation",
                "invalid-tls-material",
                "oci-download-or-tls-state",
                "runtime-failure"
        };
        TestOciCertificatesDownloader.scriptCaCertificate("test-keys/ca.pem");
        for (RuntimeException failure : failures) {
            TestOciCertificatesDownloader.scriptCaFailure(failure);
        }

        QueueLoggingHandler handler = QueueLoggingHandler.create(DefaultOciCertificateBundleTlsManager.class);
        try {
            DefaultOciCertificateBundleTlsManager manager =
                    newBundleManager(false, "* * * * * ? *");
            assertThat(tasksCreatedSince(taskManager, existingTasks).size(), is(1));
            X509Certificate initialCa = trustedCa(manager);

            for (int i = 0; i < failures.length; i++) {
                LogRecord record = handler.poll(5, TimeUnit.SECONDS);
                assertThat("scheduled warning " + (i + 1), record, notNullValue());
                assertThat(record.getLevel(), is(Level.WARNING));
                assertThat(record.getLoggerName(), is(DefaultOciCertificateBundleTlsManager.class.getName()));
                assertThat(record.getThrown(), nullValue());
                String warning = record.getMessage();
                assertThat(warning, containsString("Failed to refresh OCI certificate test-cert"));
                assertThat(warning, containsString("failure category: " + failureCategories[i]));
                assertThat(warning, containsString("previously installed TLS identity remains active"));
                assertThat(warning, not(containsString(failures[i].getMessage())));
                assertThat(trustedCa(manager), equalTo(initialCa));
            }

            TestOciCertificatesDownloader.caCertificateResource = "test-keys/ecCert.pem";
            X509Certificate rotatedCa = awaitCaRotation(manager, initialCa, 5, TimeUnit.SECONDS);
            assertThat(rotatedCa.getSubjectX500Principal().getName(), containsString("CN=managed-ec"));
            assertThat(privateKeyAlgorithm(manager), is("RSA"));

            assertTrue(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey >= failures.length + 2);
            assertTrue(TestOciCertificatesDownloader.callCount_loadCACertificate >= failures.length + 2);
            assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(0));
        } finally {
            handler.close();
        }
    }

    @Test
    void configCallbackPropagatesRefreshFailure() {
        DefaultOciCertificateBundleTlsManager manager = newBundleManager();
        TestOciCertificatesDownloader.version = "2";
        TestOciCertificatesDownloader.managedFailure = new IllegalStateException("synthetic managed download failure");

        assertThrows(IllegalStateException.class, () -> manager.config(Config.empty()));
        assertThat(privateKeyAlgorithm(manager), is("RSA"));
    }

    @Test
    void concurrentManagedRotationReloadsVersionOnce() throws Exception {
        DefaultOciCertificateBundleTlsManager manager = newBundleManager();
        TestOciCertificatesDownloader.version = "2";
        TestOciCertificatesDownloader.managedDelayMillis = 25;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> {
                start.await();
                return manager.loadContext(false);
            });
            Future<Boolean> second = executor.submit(() -> {
                start.await();
                return manager.loadContext(false);
            });
            start.countDown();

            boolean firstResult = first.get(5, TimeUnit.SECONDS);
            boolean secondResult = second.get(5, TimeUnit.SECONDS);
            assertTrue(firstResult || secondResult, "One concurrent refresh should install the new identity");
            assertFalse(firstResult && secondResult, "Only one concurrent refresh should install the new identity");
            assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, equalTo(3));
            assertThat(TestOciPrivateKeyDownloader.callCount, equalTo(0));
            assertThat(privateKeyAlgorithm(manager), is("EC"));
        } finally {
            executor.shutdownNow();
        }
    }

    private static DefaultOciCertificatesTlsManager newVaultManager() {
        OciCertificatesTlsManagerConfig config = OciCertificatesTlsManagerConfig.builder()
                .schedule("0 * * * * ? 2099")
                .vaultCryptoEndpoint(URI.create("https://vault.example.test"))
                .caOcid("test-ca")
                .certOcid("test-cert")
                .keyOcid("test-key")
                .keyPassword("test-password")
                .buildPrototype();
        DefaultOciCertificatesTlsManager manager = new DefaultOciCertificatesTlsManager(config);
        manager.init(Tls.builder().buildPrototype());
        return manager;
    }

    private static DefaultOciCertificateBundleTlsManager newBundleManager() {
        return newBundleManager(false);
    }

    private static DefaultOciCertificateBundleTlsManager newBundleManager(boolean alwaysReload) {
        return newBundleManager(alwaysReload, "0 * * * * ? 2099");
    }

    private static DefaultOciCertificateBundleTlsManager newBundleManager(boolean alwaysReload,
                                                                          String schedule) {
        OciCertificateBundleTlsManagerConfig config = OciCertificateBundleTlsManagerConfig.builder()
                .schedule(schedule)
                .alwaysReload(alwaysReload)
                .caOcid("test-ca")
                .certOcid("test-cert")
                .buildPrototype();
        DefaultOciCertificateBundleTlsManager manager = new DefaultOciCertificateBundleTlsManager(config);
        manager.init(Tls.builder().buildPrototype());
        return manager;
    }

    private static String privateKeyAlgorithm(DefaultOciCertificateBundleTlsManager manager) {
        X509KeyManager keyManager = manager.keyManager().orElseThrow();
        for (String algorithm : new String[] {"RSA", "EC"}) {
            String alias = keyManager.chooseServerAlias(algorithm, null, null);
            if (alias != null) {
                PrivateKey privateKey = keyManager.getPrivateKey(alias);
                if (privateKey != null) {
                    return privateKey.getAlgorithm();
                }
            }
        }
        throw new AssertionError("No RSA or EC server key alias was available");
    }

    private static X509Certificate trustedCa(DefaultOciCertificateBundleTlsManager manager) {
        X509Certificate[] acceptedIssuers = manager.trustManager().orElseThrow().getAcceptedIssuers();
        assertThat(acceptedIssuers.length, is(1));
        return acceptedIssuers[0];
    }

    private static X509Certificate awaitCaRotation(DefaultOciCertificateBundleTlsManager manager,
                                                   X509Certificate initialCa,
                                                   long timeout,
                                                   TimeUnit timeUnit) throws InterruptedException {
        long deadline = System.nanoTime() + timeUnit.toNanos(timeout);
        X509Certificate current = trustedCa(manager);
        while (current.equals(initialCa) && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
            current = trustedCa(manager);
        }
        assertThat("scheduled retry should install the rotated CA", current, not(equalTo(initialCa)));
        return current;
    }

    private static List<Task> tasksCreatedSince(TaskManager taskManager, Set<Task> existingTasks) {
        return taskManager.tasks()
                .stream()
                .filter(task -> !existingTasks.contains(task))
                .toList();
    }

    private static void closeTasksCreatedSince(TaskManager taskManager, Set<Task> existingTasks) {
        for (Task task : tasksCreatedSince(taskManager, existingTasks)) {
            task.close();
            task.executor().shutdownNow();
            try {
                assertTrue(task.executor().awaitTermination(5, TimeUnit.SECONDS),
                           "Scheduled task executor should terminate");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while stopping scheduled task executor", e);
            }
        }
    }

    private static final class QueueLoggingHandler extends Handler {
        private final Logger logger;
        private final BlockingQueue<LogRecord> records = new LinkedBlockingQueue<>();

        private QueueLoggingHandler(Class<?> loggerClass) {
            this.logger = Logger.getLogger(loggerClass.getName());
            logger.addHandler(this);
        }

        static QueueLoggingHandler create(Class<?> loggerClass) {
            return new QueueLoggingHandler(loggerClass);
        }

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
            logger.removeHandler(this);
            records.clear();
        }

        LogRecord poll(long timeout, TimeUnit timeUnit) throws InterruptedException {
            return records.poll(timeout, timeUnit);
        }
    }
}
