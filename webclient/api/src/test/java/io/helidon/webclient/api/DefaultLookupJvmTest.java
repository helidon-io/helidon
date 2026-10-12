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
package io.helidon.webclient.api;

import java.io.File;
import java.net.InetAddress;
import java.net.spi.InetAddressResolverProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static io.helidon.webclient.api.TestAddresses.V4_A;
import static io.helidon.webclient.api.TestAddresses.V6_A;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultLookupJvmTest {

    @ParameterizedTest
    @MethodSource("io.helidon.webclient.api.DefaultAddressLookupFinderTest#properties")
    void testStartupProperties(String preferIPv4Stack,
                               String preferIPv6Addresses,
                               DnsAddressLookup expected,
                               @TempDir Path directory) throws Exception {
        Path service = directory.resolve("META-INF/services/java.net.spi.InetAddressResolverProvider");
        Files.createDirectories(service.getParent());
        Files.writeString(service, TestInetAddressResolverProvider.class.getName() + "\n");
        Path output = directory.resolve("child-output.txt");
        String executable = File.separatorChar == '\\' ? "java.exe" : "java";
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
        if (preferIPv4Stack != null) {
            command.add("-Djava.net.preferIPv4Stack=" + preferIPv4Stack);
        }
        if (preferIPv6Addresses != null) {
            command.add("-Djava.net.preferIPv6Addresses=" + preferIPv6Addresses);
        }
        command.add("-cp");
        command.add(directory + File.pathSeparator
                            + System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
        command.add(Probe.class.getName());
        command.add(preferIPv4Stack == null ? "<unset>" : preferIPv4Stack);
        command.add(preferIPv6Addresses == null ? "<unset>" : preferIPv6Addresses);
        command.add(expected.name());
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(output.toFile());
        builder.environment().remove("JAVA_TOOL_OPTIONS");
        builder.environment().remove("_JAVA_OPTIONS");
        builder.environment().remove("JDK_JAVA_OPTIONS");
        Process process = builder.start();
        boolean completed;
        try {
            completed = process.waitFor(30, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) {
                process.destroy();
                if (!awaitExit(process)) {
                    process.destroyForcibly();
                    assertThat("Child JVM did not terminate\n" + Files.readString(output), awaitExit(process), is(true));
                }
            }
        }
        String diagnostic = "Child JVM for preferIPv4Stack=" + preferIPv4Stack
                + ", preferIPv6Addresses=" + preferIPv6Addresses + "\n" + Files.readString(output);
        assertThat(diagnostic, completed, is(true));
        assertThat(diagnostic, process.exitValue(), is(0));
    }

    private static boolean awaitExit(Process process) {
        boolean interrupted = false;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (process.isAlive()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    return process.waitFor(remaining, TimeUnit.NANOSECONDS);
                } catch (InterruptedException _) {
                    interrupted = true;
                }
            }
            return true;
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static final class Probe {

        private Probe() {
        }

        public static void main(String[] args) {
            assertThat(System.getProperty(DefaultAddressLookupFinder.PREFER_IPV4_STACK),
                       is(property(args[0])));
            assertThat(System.getProperty(DefaultAddressLookupFinder.PREFER_IPV6_ADDRESSES),
                       is(property(args[1])));
            assertThat("Fixture provider must be discoverable before resolution",
                       ServiceLoader.load(InetAddressResolverProvider.class).stream()
                               .anyMatch(provider -> provider.type() == TestInetAddressResolverProvider.class),
                       is(true));
            DnsAddressLookup expected = DnsAddressLookup.valueOf(args[2]);
            assertThat(DnsAddressLookup.defaultLookup(), is(expected));
            DefaultDnsResolver resolver = DefaultDnsResolver.create();
            assertResolution(resolver, TestInetAddressResolverProvider.IPV4_FIRST, V4_A, expected);
            assertResolution(resolver, TestInetAddressResolverProvider.IPV6_FIRST, V6_A, expected);
            assertThat("JDK must use the fixture provider", TestInetAddressResolverProvider.installed(), is(true));
            IllegalArgumentException noMatchingFamily = assertThrows(IllegalArgumentException.class,
                    () -> resolver.resolveAddress(TestInetAddressResolverProvider.IPV4_ONLY, DnsAddressLookup.IPV6));
            assertThat(noMatchingFamily.getMessage(), containsString(TestInetAddressResolverProvider.IPV4_ONLY));
            IllegalArgumentException unknownHost = assertThrows(IllegalArgumentException.class,
                    () -> resolver.resolveAddress("unknown.fixture.invalid", DnsAddressLookup.SYSTEM));
            assertThat(unknownHost.getMessage(), containsString("unknown.fixture.invalid"));
        }

        private static String property(String argument) {
            return argument.equals("<unset>") ? null : argument;
        }

        private static void assertResolution(DefaultDnsResolver resolver,
                                             String hostname,
                                             InetAddress first,
                                             DnsAddressLookup expected) {
            // Expectations are independent of the production filtering implementation.
            InetAddress defaultAddress = switch (expected) {
            case IPV4, IPV4_PREFERRED -> V4_A;
            case IPV6, IPV6_PREFERRED -> V6_A;
            case SYSTEM -> first;
            };
            assertThat(hostname + " default lookup",
                       resolver.resolveAddress(hostname, DnsAddressLookup.defaultLookup()), is(defaultAddress));
            assertThat(hostname + " IPv4", resolver.resolveAddress(hostname, DnsAddressLookup.IPV4), is(V4_A));
            assertThat(hostname + " IPv6", resolver.resolveAddress(hostname, DnsAddressLookup.IPV6), is(V6_A));
            assertThat(hostname + " IPv4 preferred",
                       resolver.resolveAddress(hostname, DnsAddressLookup.IPV4_PREFERRED), is(V4_A));
            assertThat(hostname + " IPv6 preferred",
                       resolver.resolveAddress(hostname, DnsAddressLookup.IPV6_PREFERRED), is(V6_A));
            assertThat(hostname + " system", resolver.resolveAddress(hostname, DnsAddressLookup.SYSTEM), is(first));
        }
    }
}
