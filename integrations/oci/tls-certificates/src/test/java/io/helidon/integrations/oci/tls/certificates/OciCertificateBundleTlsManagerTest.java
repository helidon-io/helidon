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

package io.helidon.integrations.oci.tls.certificates;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.pki.PemReader;
import io.helidon.common.tls.Tls;
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.scheduling.Task;
import io.helidon.scheduling.TaskManager;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

import com.oracle.bmc.model.BmcException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OciCertificateBundleTlsManagerTest {
    private static final String INACTIVE_SCHEDULE = "0 * * * * ? 2099";
    private static final String SECONDLY_SCHEDULE = "* * * * * ? *";

    private TaskManager taskManager;
    private Set<Task> existingTasks;

    @BeforeEach
    void recordExistingTasks() {
        taskManager = GlobalServiceRegistry.registry().get(TaskManager.class);
        existingTasks = Set.copyOf(taskManager.tasks());
    }

    @AfterEach
    void reset() {
        tasksCreatedByTest().forEach(Task::close);
        TestOciCertificatesDownloader.reset();
    }

    @Test
    void providerSharedManagerInitializesOnlyOnce() throws Exception {
        String certificateOcid = "shared-test-" + System.nanoTime();
        Config config = Config.just(ConfigSources.create(Map.of(
                "manager.oci-certificate-bundle-tls-manager.schedule", INACTIVE_SCHEDULE,
                "manager.oci-certificate-bundle-tls-manager.ca-ocid", "test-ca",
                "manager.oci-certificate-bundle-tls-manager.cert-ocid", certificateOcid)));

        Tls first = Tls.create(config);
        Tls second = Tls.create(config);

        assertThat(first.prototype().manager(), sameInstance(second.prototype().manager()));
        assertThat(first.sslContext(), sameInstance(second.sslContext()));
        assertThat(tasksCreatedByTest().size(), is(1));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificates, is(1));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, is(1));
        assertThat(TestOciCertificatesDownloader.callCount_loadCACertificate, is(1));

        Future<Boolean> daemon = tasksCreatedByTest().getFirst().executor()
                .submit(() -> Thread.currentThread().isDaemon());
        assertThat("managed scheduler must not prevent JVM termination",
                   daemon.get(5, TimeUnit.SECONDS),
                   is(true));
    }

    @Test
    void sharedManagerRejectsIncompatibleTlsContextConfiguration() {
        OciCertificateBundleTlsManager manager = newManager(false, INACTIVE_SCHEDULE);
        Tls.create(builder -> builder.manager(manager).protocol("TLS"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Tls.create(builder -> builder.manager(manager).protocol("TLSv1.2")));

        assertThat(exception.getMessage(), is("A shared OCI certificate-bundle TLS manager requires matching "
                                                      + "TLS context configuration"));
    }

    @Test
    void externalTlsReloadIsRejected() {
        OciCertificateBundleTlsManager manager = newManager(false, INACTIVE_SCHEDULE);
        Tls tls = Tls.create(builder -> builder.manager(manager));
        Tls replacement = Tls.builder().build();

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class,
                                                               () -> tls.reload(replacement));

        assertThat(exception.getMessage(), is("OCI certificate-bundle TLS material is reloaded from OCI"));
    }

    @Test
    void reconstructingSharedManagerRestartsPollingAfterTaskManagerShutdown() throws Exception {
        ServiceRegistry originalRegistry = GlobalServiceRegistry.registry();
        TestTaskManager isolatedTaskManager = new TestTaskManager();
        GlobalServiceRegistry.registry(registryWithTaskManager(originalRegistry, isolatedTaskManager));
        try {
            String certificateOcid = "restart-test-" + System.nanoTime();
            Config config = Config.just(ConfigSources.create(Map.of(
                    "manager.oci-certificate-bundle-tls-manager.schedule", SECONDLY_SCHEDULE,
                    "manager.oci-certificate-bundle-tls-manager.ca-ocid", "test-ca",
                    "manager.oci-certificate-bundle-tls-manager.cert-ocid", certificateOcid)));
            Tls first = Tls.create(config);
            assertThat(isolatedTaskManager.tasks().size(), is(1));
            await(() -> TestOciCertificatesDownloader.publicLoadCount(certificateOcid) >= 2,
                  "managed polling to start");

            isolatedTaskManager.shutdown();
            TimeUnit.MILLISECONDS.sleep(100);
            int callsAfterShutdown = TestOciCertificatesDownloader.publicLoadCount(certificateOcid);
            TimeUnit.MILLISECONDS.sleep(1200);

            assertThat(isolatedTaskManager.tasks().isEmpty(), is(true));
            assertThat(TestOciCertificatesDownloader.publicLoadCount(certificateOcid), is(callsAfterShutdown));

            Tls second = Tls.create(config);
            assertThat(first.prototype().manager(), sameInstance(second.prototype().manager()));
            assertThat(first.sslContext(), sameInstance(second.sslContext()));
            assertThat(isolatedTaskManager.tasks().size(), is(1));
            assertThat(TestOciCertificatesDownloader.publicLoadCount(certificateOcid) > callsAfterShutdown, is(true));

            int callsAfterReconstruction = TestOciCertificatesDownloader.publicLoadCount(certificateOcid);
            await(() -> TestOciCertificatesDownloader.publicLoadCount(certificateOcid) > callsAfterReconstruction,
                  "restarted managed polling to run");
        } finally {
            isolatedTaskManager.shutdown();
            GlobalServiceRegistry.registry(originalRegistry);
        }
    }

    @Test
    void refreshFailureLogsSafeOciDiagnosticsAndRetries() throws Exception {
        OciCertificateBundleTlsManager manager = newManager(false, SECONDLY_SCHEDULE);
        Tls.create(builder -> builder.manager(manager));
        Logger logger = Logger.getLogger(DefaultOciCertificateBundleTlsManager.class.getName());

        try (TestLogHandler handler = new TestLogHandler(logger)) {
            TestOciCertificatesDownloader.version = "2";
            TestOciCertificatesDownloader.managedFailure =
                    new IllegalStateException("wrapper-secret",
                                              new BmcException(404,
                                                               "NotAuthorizedOrNotFound",
                                                               "sdk-secret",
                                                               "opc-test"));

            await(() -> handler.records().stream().anyMatch(record -> record.getMessage()
                          .contains("phase: private-key-certificate-bundle")),
                  "an actionable OCI refresh warning");
            LogRecord warning = handler.records().stream()
                    .filter(record -> record.getMessage().contains("phase: private-key-certificate-bundle"))
                    .findFirst()
                    .orElseThrow();

            assertThat(warning.getMessage(), containsString("failure category: oci-service-failure"));
            assertThat(warning.getMessage(), containsString("status code: 404"));
            assertThat(warning.getMessage(), containsString("service code: NotAuthorizedOrNotFound"));
            assertThat(warning.getMessage(), containsString("opc request id: opc-test"));
            assertThat(warning.getMessage(), containsString("client side: false"));
            assertThat(warning.getMessage(), containsString("timeout: false"));
            assertThat(warning.getMessage(), not(containsString("wrapper-secret")));
            assertThat(warning.getMessage(), not(containsString("sdk-secret")));
            assertThat(warning.getThrown(), nullValue());
            assertThat(privateKeyAlgorithm(manager), is("RSA"));

            TestOciCertificatesDownloader.managedFailure = null;
            await(() -> "EC".equals(privateKeyAlgorithm(manager)), "the failed refresh to be retried");
        }
    }

    @Test
    void rotationAtomicallyUpdatesExistingTlsAndReplacesSessionCaches() throws Exception {
        OciCertificateBundleTlsManager manager = newManager(false, SECONDLY_SCHEDULE);
        Tls first = Tls.create(builder -> builder.manager(manager));
        Tls second = Tls.create(builder -> builder.manager(manager));
        SSLContext stableContext = first.sslContext();
        SSLServerSocketFactory cachedServerSocketFactory = stableContext.getServerSocketFactory();
        SSLSessionContext initialServerSessions = stableContext.getServerSessionContext();
        SSLSessionContext initialClientSessions = stableContext.getClientSessionContext();

        assertThat(second.sslContext(), sameInstance(stableContext));
        assertThat(peerCertificate(cachedServerSocketFactory).getPublicKey().getAlgorithm(), is("RSA"));
        await(() -> TestOciCertificatesDownloader.callCount_loadCertificates >= 2,
              "the unchanged public bundle to be polled");
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, is(1));
        assertThat(stableContext.getServerSessionContext(), sameInstance(initialServerSessions));

        TestOciCertificatesDownloader.caCertificateResource = "test-keys/ecCert.pem";
        TestOciCertificatesDownloader.version = "2";
        await(() -> "EC".equals(privateKeyAlgorithm(manager)), "the EC identity to be installed");

        assertThat(first.sslContext(), sameInstance(stableContext));
        assertThat(second.sslContext(), sameInstance(stableContext));
        assertThat(stableContext.getServerSessionContext(), not(sameInstance(initialServerSessions)));
        assertThat(stableContext.getClientSessionContext(), not(sameInstance(initialClientSessions)));
        assertThat(peerCertificate(cachedServerSocketFactory).getPublicKey().getAlgorithm(), is("EC"));
        assertThat(trustedCa(manager).getPublicKey().getAlgorithm(), is("EC"));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, is(2));
    }

    @Test
    void versionRaceKeepsOldContextAndRetriesCandidate() throws Exception {
        OciCertificateBundleTlsManager manager = newManager(false, SECONDLY_SCHEDULE);
        Tls tls = Tls.create(builder -> builder.manager(manager));
        SSLSessionContext initialSessions = tls.sslContext().getServerSessionContext();

        TestOciCertificatesDownloader.caCertificateResource = "test-keys/ecCert.pem";
        TestOciCertificatesDownloader.privateVersion = "3";
        TestOciCertificatesDownloader.version = "2";
        await(() -> TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey >= 2,
              "a raced private bundle to be rejected");

        assertThat(privateKeyAlgorithm(manager), is("RSA"));
        assertThat(tls.sslContext().getServerSessionContext(), sameInstance(initialSessions));

        TestOciCertificatesDownloader.privateVersion = null;
        await(() -> "EC".equals(privateKeyAlgorithm(manager)), "the stable candidate to be retried");
        assertThat(tls.sslContext().getServerSessionContext(), not(sameInstance(initialSessions)));
    }

    @Test
    void alwaysReloadRebuildsContextWithoutRetransmittingUnchangedPrivateKey() throws Exception {
        OciCertificateBundleTlsManager manager = newManager(true, SECONDLY_SCHEDULE);
        Tls tls = Tls.create(builder -> builder.manager(manager));
        SSLSessionContext initialSessions = tls.sslContext().getServerSessionContext();

        await(() -> tls.sslContext().getServerSessionContext() != initialSessions,
              "an explicitly requested reload");

        assertThat(TestOciCertificatesDownloader.callCount_loadCertificates >= 2, is(true));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, is(1));
        assertThat(privateKeyAlgorithm(manager), is("RSA"));
    }

    @Test
    void caRotationRejectsPreviouslyResumableMutualTlsSession() throws Exception {
        TestOciCertificatesDownloader.caCertificateResource = "test-keys/ecCert.pem";
        OciCertificateBundleTlsManager manager = newManager(false, SECONDLY_SCHEDULE);
        Tls tls = Tls.create(builder -> builder.manager(manager));
        SSLServerSocketFactory serverFactory = tls.sslContext().getServerSocketFactory();
        SSLSocketFactory clientFactory = mutualTlsClientFactory();

        Handshake first = mutualTlsHandshake(serverFactory, clientFactory, 0);
        Handshake resumed = mutualTlsHandshake(serverFactory, clientFactory, first.port());
        assertThat("the pre-rotation TLS 1.2 session should be resumable",
                   Arrays.equals(first.sessionId(), resumed.sessionId()),
                   is(true));

        TestOciCertificatesDownloader.caCertificateResource = "test-keys/serverCert.pem";
        await(() -> "RSA".equals(trustedCa(manager).getPublicKey().getAlgorithm()),
              "the replacement trust anchor to be installed");

        assertThrows(SSLHandshakeException.class,
                     () -> mutualTlsHandshake(serverFactory, clientFactory, first.port()));
        assertThat(TestOciCertificatesDownloader.callCount_loadCertificatesWithPrivateKey, is(1));
    }

    private static OciCertificateBundleTlsManager newManager(boolean alwaysReload, String schedule) {
        return OciCertificateBundleTlsManager.create(OciCertificateBundleTlsManagerConfig.builder()
                                                             .schedule(schedule)
                                                             .alwaysReload(alwaysReload)
                                                             .caOcid("test-ca")
                                                             .certOcid("test-cert")
                                                             .buildPrototype());
    }

    private static ServiceRegistry registryWithTaskManager(ServiceRegistry delegate, TaskManager taskManager) {
        TypeName taskManagerType = TypeName.create(TaskManager.class);
        return (ServiceRegistry) Proxy.newProxyInstance(
                ServiceRegistry.class.getClassLoader(),
                new Class<?>[] {ServiceRegistry.class},
                (proxy, method, args) -> {
                    if (args != null
                            && args.length == 1
                            && (taskManagerType.equals(args[0]) || TaskManager.class.equals(args[0]))) {
                        if (method.getName().equals("first")) {
                            return Optional.of(taskManager);
                        }
                        if (method.getName().equals("get")) {
                            return taskManager;
                        }
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static String privateKeyAlgorithm(OciCertificateBundleTlsManager manager) {
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
        return "";
    }

    private static X509Certificate trustedCa(OciCertificateBundleTlsManager manager) {
        X509Certificate[] acceptedIssuers = manager.trustManager().orElseThrow().getAcceptedIssuers();
        assertThat(acceptedIssuers.length, is(1));
        return acceptedIssuers[0];
    }

    private static X509Certificate peerCertificate(SSLServerSocketFactory serverSocketFactory) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (SSLServerSocket serverSocket = (SSLServerSocket) serverSocketFactory.createServerSocket()) {
            serverSocket.bind(new InetSocketAddress(loopback, 0));
            serverSocket.setEnabledProtocols(new String[] {"TLSv1.2"});

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> serverHandshake = executor.submit(() -> {
                    try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                        socket.setEnabledProtocols(new String[] {"TLSv1.2"});
                        socket.startHandshake();
                    }
                    return null;
                });

                SSLContext clientContext = trustAllContext();
                try (SSLSocket client = (SSLSocket) clientContext.getSocketFactory()
                        .createSocket(loopback, serverSocket.getLocalPort())) {
                    client.setEnabledProtocols(new String[] {"TLSv1.2"});
                    client.startHandshake();
                    X509Certificate peer = (X509Certificate) client.getSession().getPeerCertificates()[0];
                    serverHandshake.get(5, TimeUnit.SECONDS);
                    return peer;
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private static SSLSocketFactory mutualTlsClientFactory() {
        String keyPem = testResource("ecKey.pem");
        PrivateKey privateKey = Keys.builder()
                .pem(pem -> pem.key(Resource.create("test client private key", keyPem)))
                .build()
                .privateKey()
                .orElseThrow();
        X509Certificate certificate = PemReader.readCertificates(new ByteArrayInputStream(
                testResource("ecCert.pem").getBytes(StandardCharsets.US_ASCII))).getFirst();
        Tls tls = Tls.builder()
                .privateKey(privateKey)
                .privateKeyCertChain(List.of(certificate))
                .trustAll(true)
                .build();
        return tls.sslContext().getSocketFactory();
    }

    private static Handshake mutualTlsHandshake(SSLServerSocketFactory serverFactory,
                                                SSLSocketFactory clientFactory,
                                                int port) throws Exception {
        InetAddress loopback = InetAddress.getLoopbackAddress();
        try (SSLServerSocket serverSocket = (SSLServerSocket) serverFactory.createServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(loopback, port));
            serverSocket.setEnabledProtocols(new String[] {"TLSv1.2"});
            serverSocket.setNeedClientAuth(true);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<byte[]> serverHandshake = executor.submit(() -> {
                    try (SSLSocket socket = (SSLSocket) serverSocket.accept()) {
                        socket.setEnabledProtocols(new String[] {"TLSv1.2"});
                        socket.setSoTimeout(5000);
                        socket.startHandshake();
                        return socket.getSession().getId();
                    }
                });

                try (SSLSocket client = (SSLSocket) clientFactory.createSocket(loopback,
                                                                               serverSocket.getLocalPort())) {
                    client.setEnabledProtocols(new String[] {"TLSv1.2"});
                    client.setSoTimeout(5000);
                    try {
                        client.startHandshake();
                    } catch (IOException e) {
                        try {
                            serverHandshake.get(5, TimeUnit.SECONDS);
                        } catch (ExecutionException serverFailure) {
                            if (serverFailure.getCause() instanceof SSLHandshakeException handshakeFailure) {
                                throw handshakeFailure;
                            }
                            e.addSuppressed(serverFailure.getCause());
                        } catch (Exception serverFailure) {
                            e.addSuppressed(serverFailure);
                        }
                        throw e;
                    }
                    byte[] sessionId = client.getSession().getId();
                    assertThat(Arrays.equals(serverHandshake.get(5, TimeUnit.SECONDS), sessionId), is(true));
                    return new Handshake(serverSocket.getLocalPort(), sessionId);
                }
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(5, TimeUnit.SECONDS), is(true));
            }
        }
    }

    private static String testResource(String name) {
        try (InputStream input = OciCertificateBundleTlsManagerTest.class
                .getResourceAsStream("/test-keys/" + name)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.US_ASCII);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test resource: " + name, e);
        }
    }

    private static SSLContext trustAllContext() {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {new TrustAllManager()}, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new AssertionError("Failed to create test TLS context", e);
        }
    }

    private static void await(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat("Timed out waiting for " + description, condition.getAsBoolean(), is(true));
    }

    private List<Task> tasksCreatedByTest() {
        return taskManager.tasks()
                .stream()
                .filter(task -> !existingTasks.contains(task))
                .toList();
    }

    private record Handshake(int port, byte[] sessionId) {
        private Handshake {
            sessionId = sessionId.clone();
        }

        @Override
        public byte[] sessionId() {
            return sessionId.clone();
        }
    }

    private static final class TestTaskManager implements TaskManager {
        private final Map<String, Task> tasks = new ConcurrentHashMap<>();

        @Override
        public void shutdown() {
            tasks().forEach(Task::close);
        }

        @Override
        public void register(Task task) {
            tasks.put(task.id(), task);
        }

        @Override
        public boolean remove(Task task) {
            return tasks.remove(task.id(), task);
        }

        @Override
        public List<Task> tasks() {
            return List.copyOf(tasks.values());
        }
    }

    private static final class TestLogHandler extends Handler implements AutoCloseable {
        private final List<LogRecord> records = new CopyOnWriteArrayList<>();
        private final Logger logger;

        private TestLogHandler(Logger logger) {
            this.logger = logger;
            logger.addHandler(this);
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
        }

        private List<LogRecord> records() {
            return records;
        }
    }

    private static final class TrustAllManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
