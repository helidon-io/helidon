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

package io.helidon.quic;

import java.net.IDN;
import java.net.InetAddress;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.Normalizer;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.naming.NamingException;
import javax.naming.ldap.LdapName;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.StandardConstants;

final class QuicTlsEndpointIdentity {
    private QuicTlsEndpointIdentity() {
    }

    static void checkServerIdentity(X509Certificate[] chain, SSLEngine engine) throws CertificateException {
        if (engine == null) {
            return;
        }
        String algorithm = engine.getSSLParameters().getEndpointIdentificationAlgorithm();
        if (algorithm == null || algorithm.isEmpty()) {
            return;
        }
        if (!"HTTPS".equalsIgnoreCase(algorithm)
                && !"LDAP".equalsIgnoreCase(algorithm)
                && !"LDAPS".equalsIgnoreCase(algorithm)) {
            throw new CertificateException("Unsupported endpoint identification algorithm: " + algorithm);
        }
        SSLSession session = engine.getHandshakeSession();
        if (session == null) {
            throw new CertificateException("Endpoint identification requires a TLS handshake session");
        }
        if (chain == null || chain.length == 0 || chain[0] == null) {
            throw new CertificateException("Endpoint identification requires a server certificate");
        }
        // The delegate has already validated the chain. Its trust policy and issuer list do not define the peer identity.
        String peerHost = session.getPeerHost();
        if (peerHost != null && peerHost.endsWith(".")) {
            peerHost = peerHost.substring(0, peerHost.length() - 1);
        }
        if (session instanceof ExtendedSSLSession extendedSession) {
            for (SNIServerName serverName : extendedSession.getRequestedServerNames()) {
                if (serverName.getType() == StandardConstants.SNI_HOST_NAME) {
                    String sniHost = serverName(serverName);
                    try {
                        checkIdentity(chain[0], sniHost);
                        return;
                    } catch (CertificateException e) {
                        if (peerHost == null || sniHost.equalsIgnoreCase(peerHost)) {
                            throw e;
                        }
                    }
                    break;
                }
            }
        }
        checkIdentity(chain[0], peerHost);
    }

    private static String serverName(SNIServerName serverName) throws CertificateException {
        try {
            return serverName instanceof SNIHostName hostName
                    ? hostName.getAsciiName()
                    : new SNIHostName(serverName.getEncoded()).getAsciiName();
        } catch (IllegalArgumentException e) {
            throw new CertificateException("Invalid TLS server name", e);
        }
    }

    private static void checkIdentity(X509Certificate certificate, String host) throws CertificateException {
        if (host == null || host.isEmpty()) {
            throw new CertificateException("Endpoint identification requires a peer hostname");
        }
        InetAddress address = literalAddress(host);
        Collection<List<?>> alternativeNames = certificate.getSubjectAlternativeNames();
        if (address != null) {
            if (alternativeNames != null) {
                for (List<?> alternativeName : alternativeNames) {
                    if (Integer.valueOf(7).equals(alternativeName.getFirst())
                            && alternativeName.get(1) instanceof String value
                            && address.equals(literalAddress(value))) {
                        return;
                    }
                }
            }
            throw new CertificateException("No subject alternative IP address matches " + host);
        }

        try {
            new SNIHostName(host);
        } catch (IllegalArgumentException e) {
            throw new CertificateException("Invalid peer hostname: " + host, e);
        }
        boolean dnsAlternativeName = false;
        if (alternativeNames != null) {
            for (List<?> alternativeName : alternativeNames) {
                if (Integer.valueOf(2).equals(alternativeName.getFirst())) {
                    dnsAlternativeName = true;
                    if (alternativeName.get(1) instanceof String value && matchesDns(host, value)) {
                        return;
                    }
                }
            }
        }
        if (!dnsAlternativeName) {
            String commonName = commonName(certificate);
            if (commonName != null && matchesDns(host, commonName)) {
                return;
            }
        }
        throw new CertificateException("No server certificate identity matches " + host);
    }

    private static InetAddress literalAddress(String host) {
        try {
            // Unlike getByName, ofLiteral never resolves a DNS name during certificate validation.
            return InetAddress.ofLiteral(host);
        } catch (IllegalArgumentException _) {
            return null;
        }
    }

    private static String commonName(X509Certificate certificate) throws CertificateException {
        try {
            var rdns = new LdapName(certificate.getSubjectX500Principal().getName()).getRdns();
            for (int i = rdns.size() - 1; i >= 0; i--) {
                var attribute = rdns.get(i).toAttributes().get("CN");
                if (attribute != null) {
                    if (attribute.get() instanceof String name && Normalizer.isNormalized(name, Normalizer.Form.NFKC)) {
                        return name;
                    }
                    throw new CertificateException("Invalid server certificate common name");
                }
            }
            return null;
        } catch (NamingException e) {
            throw new CertificateException("Invalid server certificate subject", e);
        }
    }

    private static boolean matchesDns(String host, String certificateName) {
        try {
            host = IDN.toUnicode(IDN.toASCII(host)).toLowerCase(Locale.ROOT);
            certificateName = IDN.toUnicode(IDN.toASCII(certificateName)).toLowerCase(Locale.ROOT);
            new SNIHostName(certificateName.replace('*', 'z'));
        } catch (IllegalArgumentException _) {
            return false;
        }
        if (certificateName.indexOf('*') < 0) {
            return host.equals(certificateName);
        }
        int hostDot = host.indexOf('.');
        int certificateDot = certificateName.indexOf('.');
        if (hostDot < 0 || certificateDot < 0 || !host.substring(hostDot).equals(certificateName.substring(certificateDot))) {
            return false;
        }
        // Wildcards may consume characters only within the leftmost DNS label.
        String label = host.substring(0, hostDot);
        String pattern = certificateName.substring(0, certificateDot);
        int position = 0;
        int patternPosition = 0;
        int wildcard = pattern.indexOf('*');
        while (wildcard >= 0) {
            String part = pattern.substring(patternPosition, wildcard);
            int match = label.indexOf(part, position);
            if (match < 0 || (patternPosition == 0 && match != 0)) {
                return false;
            }
            position = match + part.length();
            patternPosition = wildcard + 1;
            wildcard = pattern.indexOf('*', patternPosition);
        }
        return label.substring(position).endsWith(pattern.substring(patternPosition));
    }
}
