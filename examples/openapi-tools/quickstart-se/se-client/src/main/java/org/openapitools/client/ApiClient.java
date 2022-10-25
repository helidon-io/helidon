/**
 * OpenAPI Helidon Quickstart
 * This is a sample for Helidon Quickstart project.
 *
 * The version of the OpenAPI document: 1.0.0
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

package org.openapitools.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openapitools.jackson.nullable.JsonNullableModule;

import io.helidon.config.Config;
import io.helidon.media.jackson.JacksonSupport;
import io.helidon.webclient.WebClient;

import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Configuration and utility class for API clients.
 * <p>
 * Use the {@link ApiClient.Builder} class to prepare and ultimately create the {@code ApiClient} instance.
 * </p>
 */
public class ApiClient {

  private final WebClient webClient;

  /**
   * @return a {@code Builder} for an {@code ApiClient}
   */
  public static ApiClient.Builder builder() {
    return new Builder();
  }

  /**
   * URL encode a string in the UTF-8 encoding.
   *
   * @param s String to encode.
   * @return URL-encoded representation of the input string.
   */
  public static String urlEncode(String s) {
    return URLEncoder.encode(s, UTF_8);
  }

  /**
   * Convert a URL query name/value parameter to a list of encoded {@link Pair}
   * objects.
   *
   * <p>The value can be null, in which case an empty list is returned.</p>
   *
   * @param name The query name parameter.
   * @param value The query value, which may not be a collection but may be
   *              null.
   * @return A singleton list of the {@link Pair} objects representing the input
   * parameters, which is encoded for use in a URL. If the value is null, an
   * empty list is returned.
   */
  public static List<Pair> parameterToPairs(String name, Object value) {
    if (name == null || name.isEmpty() || value == null) {
      return Collections.emptyList();
    }
    return Collections.singletonList(new Pair(urlEncode(name), urlEncode(valueToString(value))));
  }

  /**
   * Convert a URL query name/collection parameter to a list of encoded
   * {@link Pair} objects.
   *
   * @param collectionFormat The swagger collectionFormat string (csv, tsv, etc).
   * @param name The query name parameter.
   * @param values A collection of values for the given query name, which may be
   *               null.
   * @return A list of {@link Pair} objects representing the input parameters,
   * which is encoded for use in a URL. If the values collection is null, an
   * empty list is returned.
   */
  public static List<Pair> parameterToPairs(
      String collectionFormat, String name, Collection<?> values) {
    if (name == null || name.isEmpty() || values == null || values.isEmpty()) {
      return Collections.emptyList();
    }

    // get the collection format (default: csv)
    String format = collectionFormat == null || collectionFormat.isEmpty() ? "csv" : collectionFormat;

    // create the params based on the collection format
    if ("multi".equals(format)) {
      return values.stream()
          .map(value -> new Pair(urlEncode(name), urlEncode(valueToString(value))))
          .collect(Collectors.toList());
    }

    String delimiter;
    switch(format) {
      case "csv":
        delimiter = urlEncode(",");
        break;
      case "ssv":
        delimiter = urlEncode(" ");
        break;
      case "tsv":
        delimiter = urlEncode("\t");
        break;
      case "pipes":
        delimiter = urlEncode("|");
        break;
      default:
        throw new IllegalArgumentException("Illegal collection format: " + collectionFormat);
    }

    StringJoiner joiner = new StringJoiner(delimiter);
    for (Object value : values) {
      joiner.add(urlEncode(valueToString(value)));
    }

    return Collections.singletonList(new Pair(urlEncode(name), joiner.toString()));
  }

  private ApiClient(Builder builder) {
    webClient = builder.webClientBuilder().build();
  }

  /**
   * Get the {@link WebClient} prepared by the builder of this {@code ApiClient}.
   *
   * @return the WebClient
   */
  public WebClient webClient() {
    return webClient;
  }

  private static String valueToString(Object value) {
    if (value == null) {
      return "";
    }
    if (value instanceof OffsetDateTime) {
      return ((OffsetDateTime) value).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
    return value.toString();
  }

  /**
    * Builder for creating a new {@code ApiClient} instance.
    *
    * <p>
    * The builder accepts a {@link WebClient.Builder} via the {@code webClientBuilder} method but will provide a default one
    * using available configuration (the {@code client} node) and the base URI set in the OpenAPI document.
    * </p>
    */
  public static class Builder {

    private WebClient.Builder webClientBuilder;
    private Config clientConfig;
    private ObjectMapper objectMapper;

    public ApiClient build() {
      return new ApiClient(this);
    }

    /**
     * Sets the {@code WebClient.Builder} which the {@code ApiClient.Builder} uses. Any previous setting is discarded.
     *
     * @param webClientBuilder the {@code WebClient.Builder} to be used going forward
     * @return the updated builder
     */
    public Builder webClientBuilder(WebClient.Builder webClientBuilder) {
      this.webClientBuilder = webClientBuilder;
      return this;
    }

    /**
     * Sets the client {@code Config} which the {@code ApiClient.Builder} uses in preparing a default {@code WebClient.Builder}.
     * The builder ignores this setting if you provide your own {@code WebClient.Builder} by invoking the
     * {@code webClientBuilder} method.
     *
     * @param clientConfig the {@code Config} node containing client settings
     * @return the updated builder
     */
    public Builder clientConfig(Config clientConfig) {
      this.clientConfig = clientConfig;
      return this;
    }

    /**
     * @return the previously-stored web client builder or, if none, a default one using the provided or defaulted
     * client configuration
     */
     public WebClient.Builder webClientBuilder() {
      if (webClientBuilder == null) {
        webClientBuilder = defaultWebClientBuilder();
      }
      return webClientBuilder;
    }

    /**
     * Stores the Jackson {@code ObjectMapper} the builder uses in preparing the {@code WebClient}.
     *
     * @param objectMapper the Jackson object mapper to use in all API invocations via the built {@code ApiClient}
     * @return the updated builder
     */
    public Builder objectMapper(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
      return this;
    }

    private WebClient.Builder defaultWebClientBuilder() {
      WebClient.Builder defaultWebClientBuilder = WebClient.builder()
                  .baseUri("http://localhost:8080")
                  .config(clientConfig());
      defaultWebClientBuilder.addMediaSupport(objectMapper == null
                ? JacksonSupport.create()
                : JacksonSupport.create(objectMapper));
      return defaultWebClientBuilder;
    }

    private Config clientConfig() {
      if (clientConfig == null) {
         clientConfig = Config.create().get("client");
      }
      return clientConfig;
    }
  }
}