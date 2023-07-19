package io.helidon.nima.webclient.api;

import io.helidon.nima.common.tls.Tls;
import io.helidon.nima.webclient.spi.DnsResolver;

/**
 * Connection key instance contains all needed connection related information.
 *
 * @param scheme           uri address scheme
 * @param host             uri address host
 * @param port             uri address port
 * @param tls              TLS to be used in connection
 * @param dnsResolver      DNS resolver to be used
 * @param dnsAddressLookup DNS address lookup strategy
 * @param proxy            Proxy server to use for outgoing requests
 */
public record ConnectionKey(String scheme,
                            String host,
                            int port,
                            Tls tls,
                            DnsResolver dnsResolver,
                            DnsAddressLookup dnsAddressLookup,
                            Proxy proxy) {
}
