package io.helidon.webserver;

import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Binary (V2) Proxy protocol data parsed by {@link ProxyProtocolHandler}. This is a specialization of
 * the {@link ProxyProtocolData} interface, which is available when the upstream proxy used the V2 Proxy protocol.
 */
public interface ProxyProtocolV2Data extends ProxyProtocolData {
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
    sealed interface TLV {
        int PP2_TYPE_ALPN = 0x01;
        int PP2_TYPE_AUTHORITY = 0x02;
        int PP2_TYPE_CRC32C = 0x03;
        int PP2_TYPE_NOOP = 0x04;
        int PP2_TYPE_UNIQUE_ID = 0x05;
        int PP2_TYPE_SSL = 0x20;
        int PP2_SUBTYPE_SSL_VERSION = 0x21;
        int PP2_SUBTYPE_SSL_CN = 0x22;
        int PP2_SUBTYPE_SSL_CIPHER = 0x23;
        int PP2_SUBTYPE_SSL_SIG_ALG = 0x24;
        int PP2_SUBTYPE_SSL_KEY_ALG = 0x25;
        int PP2_TYPE_NETNS = 0x30;

        /**
         * Returns the TLV's type identifier.
         */
        int type();

        /**
         * Application-Layer Protocol Negotiation (ALPN). The most common use case
         * will be to pass the exact copy of the ALPN extension of the Transport Layer
         * Security (TLS) protocol as defined by RFC7301.
         * @param protocol A byte sequence defining the upper layer protocol in use over the connection.
         */
        record Alpn(byte[] protocol) implements TLV {
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
        record Authority(String hostName) implements TLV {
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
        record Crc32c(int checksum) implements TLV {
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
         */
        record Noop(byte[] bytes) implements TLV {
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
        record UniqueId(byte[] id) implements TLV {
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
        record Ssl(int client, int verify, List<TLV> subTlvs) implements TLV {
            public static final int PP2_CLIENT_SSL = 0x01;
            public static final int PP2_CLIENT_CERT_CONN = 0x02;
            public static final int PP2_CLIENT_CERT_SESS = 0x04;

            @Override
            public int type() {
                return  PP2_TYPE_SSL;
            }

            /**
             * Tests whether the client presented a certificate and it was successfully verified.
             */
            public boolean isSuccessfullyVerified() {
                return verify == 0;
            }

            /**
             * Tests whether the PP2_CLIENT_SSL flag is set.
             */
            public boolean hasClientSsl() {
                return (client & PP2_CLIENT_SSL) == PP2_CLIENT_SSL;
            }

            /**
             * Tests whether the PP2_CLIENT_CERT_CONN flag is set.
             */
            public boolean hasClientCertConn() {
                return (client & PP2_CLIENT_CERT_CONN) == PP2_CLIENT_CERT_CONN;
            }

            /**
             * Tests whether the PP2_CLIENT_CERT_SESS flag is set.
             */
            public boolean hasClientCertSess() {
                return (client & PP2_CLIENT_CERT_SESS) == PP2_CLIENT_CERT_SESS;
            }
        }

        record SslVersion(String version) implements TLV {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_VERSION;
            }
        }

        /**
         * Contains the representation of the Common Name field (OID: 2.5.4.3)
         * of the client certificate's Distinguished Name. For example, "example.com".
         */
        record SslCn(String commonName) implements TLV {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_CN;
            }
        }

        /**
         * The name of the used cipher, for example "ECDHE-RSA-AES128-GCM-SHA256".
         */
        record SslCipher(String cipher) implements TLV {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_CIPHER;
            }
        }

        /**
         * The name of the algorithm used to sign the certificate presented by the frontend when
         * the incoming connection was made over an SSL/TLS transport layer, for example "SHA256".
         */
        record SslSigAlg(String signatureAlgorithm) implements TLV {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_SIG_ALG;
            }
        }

        /**
         * The name of the algorithm used to generate the key of the certificate presented by the
         * frontend when the incoming connection was made over an SSL/TLS transport layer,
         * for example "RSA2048".
         */
        record SslKeyAlg(String keyAlgorithm) implements TLV {
            @Override
            public int type() {
                return PP2_SUBTYPE_SSL_KEY_ALG;
            }
        }

        /**
         * Defines the value as the US-ASCII string representation of the namespace's name.
         * @param namespaceName The namespace's name.
         */
        record Netns(String namespaceName) implements TLV {
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
        record Unregistered(int type, byte[] value) implements TLV {
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

    /**
     * The command kind, indicating whether the connection was established on purpose by the
     * proxy without being relayed (LOCAL) or on behalf of another relayed node (PROXY).
     */
    Command command();

    /**
     * The source address, which may be either an {@link java.net.InetSocketAddress} or a {@link java.net.UnixDomainSocketAddress}.
     * If the address family is {@link io.helidon.webserver.ProxyProtocolData.Family#UNKNOWN}, then
     * this will contain an {@link java.net.InetSocketAddress} with the contents "0.0.0.0:0".
     */
    SocketAddress sourceSocketAddress();

    /**
     * The destination address, which may be either an {@link java.net.InetSocketAddress} or a {@link java.net.UnixDomainSocketAddress}.
     * If the address family is {@link io.helidon.webserver.ProxyProtocolData.Family#UNKNOWN}, then
     * this will contain an {@link java.net.InetSocketAddress} with the contents "0.0.0.0:0".
     */
    SocketAddress destSocketAddress();

    /**
     * The possibly-empty list of additional Tag-Length-Value vectors included in the proxy header.
     */
    List<TLV> tlvs();
}
