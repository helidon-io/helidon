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
package io.helidon.webserver;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.helidon.webserver.http.ServerRequest;

/**
 * Binary (V2) Proxy protocol data parsed by {@link ProxyProtocolHandler}. This is a specialization of
 * the {@link ProxyProtocolData} interface. The object returned from {@link ServerRequest#proxyProtocolData()} will
 * additionally implement this interface when the proxy version used is the V2 (binary) version.
 */
public interface ProxyProtocolV2Data extends ProxyProtocolData {
    /**
     * The command kind, indicating whether the connection was established on purpose by the
     * proxy without being relayed (LOCAL) or on behalf of another relayed node (PROXY).
     * @return The command.
     */
    Command command();

    /**
     * The source address, which may be either an {@link java.net.InetSocketAddress} or a {@link java.net.UnixDomainSocketAddress}.
     * If the address family is {@link io.helidon.webserver.ProxyProtocolData.Family#UNKNOWN}, then
     * this will contain an {@link java.net.InetSocketAddress} with the contents "0.0.0.0:0".
     * @return The source socket address.
     */
    SocketAddress sourceSocketAddress();

    /**
     * The destination address, which may be either an {@link java.net.InetSocketAddress} or a {@link java.net.UnixDomainSocketAddress}.
     * If the address family is {@link io.helidon.webserver.ProxyProtocolData.Family#UNKNOWN}, then
     * this will contain an {@link java.net.InetSocketAddress} with the contents "0.0.0.0:0".
     * @return The destination socket address.
     */
    SocketAddress destSocketAddress();

    /**
     * The possibly-empty list of additional Tag-Length-Value vectors included in the proxy header.
     * @return A never-null list of Tag-Length-Value data.
     */
    List<Tlv> tlvs();

    /**
     * The proxy command. {@link Command#LOCAL} indicates that this connection represents
     * a non-proxied connection. In that case, the server should ignore the rest of the
     * information exposed by this proxy header data.
     */
    enum Command {
        /**
         * The connection was established on purpose by the proxy without being relayed.
         * The connection endpoints are the sender and the receiver. Such connections
         * exist when the proxy sends health-checks to the server.
         */
        LOCAL,

        /**
         * The connection was established on behalf of another node, and reflects the
         * original connection endpoints.
         */
        PROXY
    }

    /**
     * A Tag-Length-Value vector.
     */
    sealed interface Tlv {
        /**
         * The PP2_TYPE_ALPN TLV type identifier.
         */
        int PP2_TYPE_ALPN = 0x01;
        /**
         * The PP2_TYPE_AUTHORITY TLV type identifier.
         */
        int PP2_TYPE_AUTHORITY = 0x02;
        /**
         * The PP2_TYPE_CRC32C TLV type identifier.
         */
        int PP2_TYPE_CRC32C = 0x03;
        /**
         * The PP2_TYPE_NOOP TLV type identifier.
         */
        int PP2_TYPE_NOOP = 0x04;
        /**
         * The PP2_TYPE_UNIQUE_ID TLV type identifier.
         */
        int PP2_TYPE_UNIQUE_ID = 0x05;
        /**
         * The PP2_TYPE_SSL TLV type identifier.
         */
        int PP2_TYPE_SSL = 0x20;
        /**
         * The PP2_SUBTYPE_SSL_VERSION TLV type identifier.
         */
        int PP2_SUBTYPE_SSL_VERSION = 0x21;
        /**
         * The PP2_SUBTYPE_SSL_CN TLV type identifier.
         */
        int PP2_SUBTYPE_SSL_CN = 0x22;
        /**
         * The PP2_SUBTYPE_SSL_CIPHER TLV type identifier.
         */
        int PP2_SUBTYPE_SSL_CIPHER = 0x23;
        /**
         * The PP2_SUBTYPE_SSL_SIG_ALG TLV type identifier.
         */
        int PP2_SUBTYPE_SSL_SIG_ALG = 0x24;
        /**
         * The PP2_SUBTYPE_SSL_KEY_ALG TLV type identifier.
         */
        int PP2_SUBTYPE_SSL_KEY_ALG = 0x25;
        /**
         * The PP2_TYPE_NETNS TLV type identifier.
         */
        int PP2_TYPE_NETNS = 0x30;

        /**
         * Returns the TLV's type identifier.
         * @return The type identifier.
         */
        int type();

        /**
         * Application-Layer Protocol Negotiation (ALPN). The most common use case
         * will be to pass the exact copy of the ALPN extension of the Transport Layer
         * Security (TLS) protocol as defined by RFC7301.
         * @param protocol A byte sequence defining the upper layer protocol in use over the connection.
         */
        record Alpn(byte[] protocol) implements Tlv {
            @Override
            public int type() {
                return PP2_TYPE_ALPN;
            }

            @Override
            public boolean equals(final Object o) {
                if (!(o instanceof final Alpn alpn)) {
                    return false;
                }
                return Objects.deepEquals(protocol, alpn.protocol);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(protocol);
            }
        }

        /**
         * Contains the host name value passed by the client, as an UTF8-encoded string.
         * In case of TLS being used on the client connection, this is the exact copy of
         * the "server_name" extension as defined by RFC3546 [10], section 3.1, often
         * referred to as "SNI". There are probably other situations where an authority
         * can be mentioned on a connection without TLS being involved at all.
         * @param hostName The host name value passed by the client.
         */
        record Authority(String hostName) implements Tlv {
            @Override
            public int type() {
                return PP2_TYPE_AUTHORITY;
            }
        }

        /**
         * The value of the type PP2_TYPE_CRC32C is a 32-bit number storing the CRC32c
         * checksum of the PROXY protocol header. If this TLV is present, Helidon will
         * validate the checksum and reject connections which fail the check.
         * @param checksum The CRC32c checksum, validated by Helidon.
         */
        record Crc32c(int checksum) implements Tlv {
            @Override
            public int type() {
                return  PP2_TYPE_CRC32C;
            }

            @Override
            public String toString() {
                return String.format("Crc32c(0x%08X)", checksum);
            }
        }

        /**
         * The TLV of this type should be ignored when parsed. The value is zero or more
         * bytes. Can be used for data padding or alignment. Note that it can be used
         * to align only by 3 or more bytes because a TLV can not be smaller than that.
         * @param bytes Padding bytes.
         */
        record Noop(byte[] bytes) implements Tlv {
            @Override
            public int type() {
                return PP2_TYPE_NOOP;
            }

            @Override
            public boolean equals(final Object o) {
                if (!(o instanceof final Noop noop)) {
                    return false;
                }
                return Objects.deepEquals(bytes, noop.bytes);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(bytes);
            }
        }

        /**
         * The value of the type PP2_TYPE_UNIQUE_ID is an opaque byte sequence of up to
         * 128 bytes generated by the upstream proxy that uniquely identifies the
         * connection. The unique ID can be used to easily correlate connections across multiple
         * layers of proxies, without needing to look up IP addresses and port numbers.
         * @param id The opaque id.
         */
        record UniqueId(byte[] id) implements Tlv {
            @Override
            public int type() {
                return PP2_TYPE_UNIQUE_ID;
            }

            @Override
            public boolean equals(final Object o) {
                if (!(o instanceof final UniqueId uniqueId)) {
                    return false;
                }
                return Objects.deepEquals(id, uniqueId.id);
            }

            @Override
            public int hashCode() {
                return Arrays.hashCode(id);
            }
        }

        /**
         * Contains information about the SSL presented by the proxied client.
         * @param client Client flags bitfield.
         * @param verify Verification status.
         * @param subTlvs The SSL subtype TLVs.
         */
        record Ssl(int client, int verify, List<Tlv> subTlvs) implements Tlv {
            /**
             * The PP2_CLIENT_SSL bitflag.
             */
            public static final int PP2_CLIENT_SSL = 0x01;
            /**
             * The PP2_CLIENT_CERT_CONN bitflag.
             */
            public static final int PP2_CLIENT_CERT_CONN = 0x02;
            /**
             * The PP2_CLIENT_CERT_SESS bitflag.
             */
            public static final int PP2_CLIENT_CERT_SESS = 0x04;

            @Override
            public int type() {
                return  PP2_TYPE_SSL;
            }

            /**
             * Tests whether the client presented a certificate and it was successfully verified.
             * @return True if the client cert was present and valid.
             */
            public boolean isSuccessfullyVerified() {
                return verify == 0;
            }

            /**
             * Tests whether the PP2_CLIENT_SSL flag is set.
             * @return True if the PP2_CLIENT_SSL is set.
             */
            public boolean hasClientSsl() {
                return (client & PP2_CLIENT_SSL) == PP2_CLIENT_SSL;
            }

            /**
             * Tests whether the PP2_CLIENT_CERT_CONN flag is set.
             * @return True if the PP2_CLIENT_CERT_CONN is set.
             */
            public boolean hasClientCertConn() {
                return (client & PP2_CLIENT_CERT_CONN) == PP2_CLIENT_CERT_CONN;
            }

            /**
             * Tests whether the PP2_CLIENT_CERT_SESS flag is set.
             * @return True if the PP2_CLIENT_CERT_SESS is set.
             */
            public boolean hasClientCertSess() {
                return (client & PP2_CLIENT_CERT_SESS) == PP2_CLIENT_CERT_SESS;
            }
        }

        /**
         * Contains informatino about the version of the TLS protocol used.
         * @param version TLS version.
         */
        record SslVersion(String version) implements Tlv {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_VERSION;
            }
        }

        /**
         * Contains the representation of the Common Name field (OID: 2.5.4.3)
         * of the client certificate's Distinguished Name. For example, "example.com".
         * @param commonName The client cert's Common Name.
         */
        record SslCn(String commonName) implements Tlv {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_CN;
            }
        }

        /**
         * The name of the used cipher, for example "ECDHE-RSA-AES128-GCM-SHA256".
         * @param cipher The cipher name.
         */
        record SslCipher(String cipher) implements Tlv {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_CIPHER;
            }
        }

        /**
         * The name of the algorithm used to sign the certificate presented by the frontend when
         * the incoming connection was made over an SSL/TLS transport layer, for example "SHA256".
         * @param signatureAlgorithm The signature algorithm name.
         */
        record SslSigAlg(String signatureAlgorithm) implements Tlv {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_SIG_ALG;
            }
        }

        /**
         * The name of the algorithm used to generate the key of the certificate presented by the
         * frontend when the incoming connection was made over an SSL/TLS transport layer,
         * for example "RSA2048".
         * @param keyAlgorithm The key algorithm name..
         */
        record SslKeyAlg(String keyAlgorithm) implements Tlv {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_KEY_ALG;
            }
        }

        /**
         * Defines the value as the US-ASCII string representation of the namespace's name.
         * @param namespaceName The namespace's name.
         */
        record Netns(String namespaceName) implements Tlv {
            @Override
            public int type() {
                return  PP2_TYPE_NETNS;
            }
        }

        /**
         * A TLV whose type is not defined in the protocol specification.
         * @param type The type byte value.
         * @param value The byte string containing the TLV value.
         */
        record Unregistered(int type, byte[] value) implements Tlv {
            @Override
            public int type() {
                return  type;
            }

            @Override
            public boolean equals(final Object o) {
                if (!(o instanceof final Unregistered that)) {
                    return false;
                }
                return type == that.type && Objects.deepEquals(value, that.value);
            }

            @Override
            public int hashCode() {
                return Objects.hash(type, Arrays.hashCode(value));
            }
        }
    }
}
