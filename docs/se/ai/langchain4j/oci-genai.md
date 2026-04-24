# Oracle OCI GenAI

## Overview

This module adds support for selected Oracle Cloud Infrastructure GenAI models.

## Maven Coordinates

In addition to the [Helidon integration with LangChain4j core dependencies](langchain4j.md#maven-coordinates), you must add the following:

```xml
<dependency>
    <groupId>io.helidon.integrations.langchain4j.providers</groupId>
    <artifactId>helidon-integrations-langchain4j-providers-oci-genai</artifactId>
</dependency>
```

## Authentication

Integration uses OCI SDK authentication provider bean from the service registry. The simplest way to configure it is by adding [Helidon OCI integration](https://github.com/helidon-io/helidon/tree/main/integrations/oci/oci):

```xml
<dependency>
    <groupId>io.helidon.integrations.oci</groupId>
    <artifactId>helidon-integrations-oci</artifactId>
</dependency>
<!-- Jakartified OCI SDK HTTP client -->
<dependency>
    <groupId>com.oracle.oci.sdk</groupId>
    <artifactId>oci-java-sdk-common-httpclient-jersey3</artifactId>
    <scope>runtime</scope>
</dependency>
```

Helidon OCI integration makes OCI Authentication provider available as a Helidon service registry bean so LangChain4j OCI GenAI can automatically discover it.

Example of OCI specific configuration file `oci-config.yaml`:

```yaml
helidon.oci:
  # "config-file" value can instruct integration to
  # load values from `~/.oci/config` file
  authentication-method: "config"
  authentication:
    config:
      region: eu-frankfurt-1
      fingerprint: "b7:a7:9c:7f:57:a7:74:ad:c2:fa:d4:31:06:b5:02:f5"
      tenant-id: "ocid1.tenancy.oc1...."
      user-id: "ocid1.user.oc1....."
      private-key:
        path: "/secrets/oci_ai_api_key.pem"
```

More authentication methods are available like `oke-workload-identity` or `resource-principal`, for example, authentication method `config-file` can instruct integration to use `~/.oci/config` file:

```yaml
helidon.oci:
  authentication-method: "config-file"
```

All possible OCI configuration properties are documented at [OCI Configuration](../../../config/io_helidon_integrations_oci_OciConfig.md).

More general information about Helidon OCI authentication integration can be found in [Helidon OCI integration](https://github.com/helidon-io/helidon/tree/main/integrations/oci/oci)

## Components

### OciGenAiChatModel

To automatically create and add `OciGenAiChatModel` to the service registry add the following lines to `application.yaml`:

```yaml
langchain4j:
  providers:
    oci-gen-ai:
      compartment-id: "ocid1.tenancy.oc1...."
      region: EU_FRANKFURT_1

  models:
    oci-genai-chat-model:
      provider: oci-gen-ai
      model-name: meta.llama-3.3-70b-instruct
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

<https://docs.oracle.com/en-us/iaas/api/#/EN/generative-ai-inference/20231130/datatypes/GenericChatRequest>

| Key | Type | Description |
|----|----|----|
| `enabled` | boolean | If set to false, the component will not be available even if configured. |
| `model-name` | string | The model name or model’s OCID to use. |
| `compartment-id` | OCID | OCI Compartment OCID |
| `region` | enum | Explicit region. If not configured, the current one is resolved. |
| `auth-provider` | injected bean | Injected is default bean if exists, named bean can be configured with `auth-provider.service-registry.named: beanName` |
| `gen-ai-client` | injected bean | Manually configured OCI SDK GenAi client. When set, values provided with `region` and `auth-provider` are ignored. Injected is default bean if exists, named bean can be configured with `gen-ai-client.service-registry.named: beanName` |
| `serving-type` | enum | The model’s serving mode, which is either on-demand serving or dedicated serving. |
| `top-k` | int | The maximum number of top-probability tokens to consider when generating text. |
| `top-p` | double between 0 and 1 | If set to a probability 0.0 \< p \< 1.0, it ensures that only the most likely tokens, with total probability mass of p, are considered for generation at each step. |
| `seed` | int | The seed for the random number generator used by the model. |
| `temperature` | double \> 0 | A number that sets the randomness of the generated output. A lower temperature means a less random generations. Use lower numbers for tasks with a correct answer such as question answering or summarizing. High temperatures can generate hallucinations or factually incorrect information. Start with temperatures lower than 1.0 and increase the temperature for more creative outputs, as you regenerate the prompts to refine the outputs. Default is 1. |
| `presence-penalty` | double between -2 and 2 | To reduce repetitiveness of generated tokens, this number penalizes new tokens based on whether they’ve appeared in the generated text so far. Values \> 0 encourage the model to use new tokens and values \< 0 encourage the model to repeat tokens. Similar to frequency penalty, a penalty is applied to previously present tokens, except that this penalty is applied equally to all tokens that have already appeared, regardless of how many times they’ve appeared. Set to 0 to disable. Default is 0. |
| `stop` | list of strings | List of strings that stop the generation if they are generated for the response text. The returned output will not contain the stop strings. |
| `max-tokens` | integer \> 1 | The maximum number of tokens that can be generated per output sequence. The token count of your prompt plus maxTokens must not exceed the model’s context length. Not setting a value for maxTokens results in the possible use of model’s full context length. |
| `frequency-penalty` | double between -2 and 2 | To reduce repetitiveness of generated tokens, this number penalizes new tokens based on their frequency in the generated text so far. Values \> 0 encourage the model to use new tokens and values \< 0 encourage the model to repeat tokens. Set to 0 to disable. Default is 0. |
| `num-generations` | int between 1 and 5 | The number of generated texts that will be returned. To eliminate tokens with low likelihood, assign p a minimum percentage for the next token’s likelihood. For example, when p is set to 0.75, the model eliminates the bottom 25 percent for the next token. Set to 1 to consider all tokens and set to 0 to disable. If both k and p are enabled, p acts after k. |
| `log-probs` | int \> 0 | Includes the logarithmic probabilities for the most likely output tokens and the chosen tokens. For example, if the log probability is 5, the API returns a list of the 5 most likely tokens. The API returns the log probability of the sampled token, so there might be up to logprobs+1 elements in the response. |
| `logit-bias` | json | Modifies the likelihood of specified tokens that appear in the completion. Example: `{"6395": 2, "8134": 1, "21943": 0.5, "5923": -100}` |

### OciGenAiStreamingChatModel

To automatically create and add `OciGenAiStreamingChatModel` to the service registry add the following lines to `application.yaml`:

```yaml
langchain4j:
  providers:
    oci-gen-ai:
      compartment-id: "ocid1.tenancy.oc1...."
      region: EU_FRANKFURT_1

  models:
    oci-genai-streaming-chat-model:
      provider: oci-gen-ai
      model-name: meta.llama-3.3-70b-instruct
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

<table>
<colgroup>
<col style="width: 27%" />
<col style="width: 27%" />
<col style="width: 45%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Key</th>
<th style="text-align: left;">Type</th>
<th style="text-align: left;">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>enabled</code></p></td>
<td style="text-align: left;"><p>boolean</p></td>
<td style="text-align: left;"><p>If set to false, the component will not be available even if configured.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>model-name</code></p></td>
<td style="text-align: left;"><p>string</p></td>
<td style="text-align: left;"><p>The model name or model’s OCID to use.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>compartment-id</code></p></td>
<td style="text-align: left;"><p>OCID</p></td>
<td style="text-align: left;"><p>OCI Compartment OCID</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>region</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>Explicit region. If not configured, the current one is resolved.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>auth-provider</code></p></td>
<td style="text-align: left;"><p>injected bean</p></td>
<td style="text-align: left;"><p>Injected is default bean if exists, named bean can be configured with <code>auth-provider.service-registry.named: beanName</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>gen-ai-client</code></p></td>
<td style="text-align: left;"><p>injected bean</p></td>
<td style="text-align: left;"><p>Manually configured OCI SDK GenAi client. When set, values provided with <code>region</code> and <code>auth-provider</code> are ignored. Injected is default bean if exists, named bean can be configured with <code>gen-ai-client.service-registry.named: beanName</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>serving-type</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>The model’s serving mode, which is either on-demand serving or dedicated serving.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>top-k</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The maximum number of top-probability tokens to consider when generating text.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>top-p</code></p></td>
<td style="text-align: left;"><p>double between 0 and 1</p></td>
<td style="text-align: left;"><p>If set to a probability 0.0 &lt; p &lt; 1.0, it ensures that only the most likely tokens, with total probability mass of p, are considered for generation at each step.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>seed</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The seed for the random number generator used by the model.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>temperature</code></p></td>
<td style="text-align: left;"><p>double &gt; 0</p></td>
<td style="text-align: left;"><p>A number that sets the randomness of the generated output. A lower temperature means a less random generations.</p>
<p>Use lower numbers for tasks with a correct answer such as question answering or summarizing. High temperatures can generate hallucinations or factually incorrect information. Start with temperatures lower than 1.0 and increase the temperature for more creative outputs, as you regenerate the prompts to refine the outputs. Default is 1.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>presence-penalty</code></p></td>
<td style="text-align: left;"><p>double between -2 and 2</p></td>
<td style="text-align: left;"><p>To reduce repetitiveness of generated tokens, this number penalizes new tokens based on whether they’ve appeared in the generated text so far. Values &gt; 0 encourage the model to use new tokens and values &lt; 0 encourage the model to repeat tokens.</p>
<p>Similar to frequency penalty, a penalty is applied to previously present tokens, except that this penalty is applied equally to all tokens that have already appeared, regardless of how many times they’ve appeared. Set to 0 to disable. Default is 0.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>stop</code></p></td>
<td style="text-align: left;"><p>list of strings</p></td>
<td style="text-align: left;"><p>List of strings that stop the generation if they are generated for the response text. The returned output will not contain the stop strings.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>max-tokens</code></p></td>
<td style="text-align: left;"><p>integer &gt; 1</p></td>
<td style="text-align: left;"><p>The maximum number of tokens that can be generated per output sequence. The token count of your prompt plus maxTokens must not exceed the model’s context length. Not setting a value for maxTokens results in the possible use of model’s full context length.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>frequency-penalty</code></p></td>
<td style="text-align: left;"><p>double between -2 and 2</p></td>
<td style="text-align: left;"><p>To reduce repetitiveness of generated tokens, this number penalizes new tokens based on their frequency in the generated text so far. Values &gt; 0 encourage the model to use new tokens and values &lt; 0 encourage the model to repeat tokens. Set to 0 to disable. Default is 0.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>num-generations</code></p></td>
<td style="text-align: left;"><p>int between 1 and 5</p></td>
<td style="text-align: left;"><p>The number of generated texts that will be returned.</p>
<p>To eliminate tokens with low likelihood, assign p a minimum percentage for the next token’s likelihood. For example, when p is set to 0.75, the model eliminates the bottom 25 percent for the next token. Set to 1 to consider all tokens and set to 0 to disable. If both k and p are enabled, p acts after k.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>log-probs</code></p></td>
<td style="text-align: left;"><p>int &gt; 0</p></td>
<td style="text-align: left;"><p>Includes the logarithmic probabilities for the most likely output tokens and the chosen tokens.</p>
<p>For example, if the log probability is 5, the API returns a list of the 5 most likely tokens. The API returns the log probability of the sampled token, so there might be up to logprobs+1 elements in the response.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>logit-bias</code></p></td>
<td style="text-align: left;"></td>
<td style="text-align: left;"><p>Modifies the likelihood of specified tokens that appear in the completion. Example: <code>{"6395": 2, "8134": 1, "21943": 0.5, "5923": -100}</code></p></td>
</tr>
</tbody>
</table>

### OciGenAiCohereChatModel

To automatically create and add `OciGenAiChatModel` to the service registry add the following lines to `application.yaml`:

```yaml
langchain4j:
  providers:
    oci-gen-ai-cohere:
      compartment-id: "ocid1.tenancy.oc1...."
      region: EU_FRANKFURT_1

  models:
    oci-genai-cohere-chat-model:
      provider: oci-gen-ai-cohere
      model-name: meta.llama-3.3-70b-instruct
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

<table>
<colgroup>
<col style="width: 27%" />
<col style="width: 27%" />
<col style="width: 45%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Key</th>
<th style="text-align: left;">Type</th>
<th style="text-align: left;">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>enabled</code></p></td>
<td style="text-align: left;"><p>boolean</p></td>
<td style="text-align: left;"><p>If set to false, the component will not be available even if configured.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>model-name</code></p></td>
<td style="text-align: left;"><p>string</p></td>
<td style="text-align: left;"><p>The model name or model’s OCID to use.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>compartment-id</code></p></td>
<td style="text-align: left;"><p>OCID</p></td>
<td style="text-align: left;"><p>OCI Compartment OCID</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>region</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>Explicit region. If not configured, the current one is resolved.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>auth-provider</code></p></td>
<td style="text-align: left;"><p>injected bean</p></td>
<td style="text-align: left;"><p>Injected is default bean if exists, named bean can be configured with <code>auth-provider.service-registry.named: beanName</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>gen-ai-client</code></p></td>
<td style="text-align: left;"><p>injected bean</p></td>
<td style="text-align: left;"><p>Manually configured OCI SDK GenAi client. When set, values provided with <code>region</code> and <code>auth-provider</code> are ignored. Injected is default bean if exists, named bean can be configured with <code>gen-ai-client.service-registry.named: beanName</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>serving-type</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>The model’s serving mode, which is either on-demand serving or dedicated serving.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>top-k</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The maximum number of top-probability tokens to consider when generating text.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>top-p</code></p></td>
<td style="text-align: left;"><p>double between 0 and 1</p></td>
<td style="text-align: left;"><p>If set to a probability 0.0 &lt; p &lt; 1.0, it ensures that only the most likely tokens, with total probability mass of p, are considered for generation at each step.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>seed</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The seed for the random number generator used by the model.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>temperature</code></p></td>
<td style="text-align: left;"><p>double &gt; 0</p></td>
<td style="text-align: left;"><p>A number that sets the randomness of the generated output. A lower temperature means a less random generations.</p>
<p>Use lower numbers for tasks with a correct answer such as question answering or summarizing. High temperatures can generate hallucinations or factually incorrect information. Start with temperatures lower than 1.0 and increase the temperature for more creative outputs, as you regenerate the prompts to refine the outputs. Default is 1.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>presence-penalty</code></p></td>
<td style="text-align: left;"><p>double between -2 and 2</p></td>
<td style="text-align: left;"><p>To reduce repetitiveness of generated tokens, this number penalizes new tokens based on whether they’ve appeared in the generated text so far. Values &gt; 0 encourage the model to use new tokens and values &lt; 0 encourage the model to repeat tokens.</p>
<p>Similar to frequency penalty, a penalty is applied to previously present tokens, except that this penalty is applied equally to all tokens that have already appeared, regardless of how many times they’ve appeared. Set to 0 to disable. Default is 0.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>stop</code></p></td>
<td style="text-align: left;"><p>list of strings</p></td>
<td style="text-align: left;"><p>List of strings that stop the generation if they are generated for the response text. The returned output will not contain the stop strings.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>max-tokens</code></p></td>
<td style="text-align: left;"><p>integer &gt; 1</p></td>
<td style="text-align: left;"><p>The maximum number of tokens that can be generated per output sequence. The token count of your prompt plus maxTokens must not exceed the model’s context length. Not setting a value for maxTokens results in the possible use of model’s full context length.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>frequency-penalty</code></p></td>
<td style="text-align: left;"><p>double between -2 and 2</p></td>
<td style="text-align: left;"><p>To reduce repetitiveness of generated tokens, this number penalizes new tokens based on their frequency in the generated text so far. Values &gt; 0 encourage the model to use new tokens and values &lt; 0 encourage the model to repeat tokens. Set to 0 to disable. Default is 0.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>is-raw-prompting</code></p></td>
<td style="text-align: left;"><p>boolean</p></td>
<td style="text-align: left;"><p>When enabled, the user’s message will be sent to the model without any preprocessing. Default is false.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>citation-quality</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>When FAST is selected, citations are generated at the same time as the text output and the request will be completed sooner. May result in less accurate citations. Default is ACCURATE.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>preamble-override</code></p></td>
<td style="text-align: left;"><p>string</p></td>
<td style="text-align: left;"><p>If specified, the default Cohere preamble is replaced with the provided preamble. A preamble is an initial guideline message that can change the model’s overall chat behavior and conversation style. Default preambles vary for different models.</p>
<p>Example: You are a travel advisor. Answer with a pirate tone.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>max-input-tokens</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The maximum number of input tokens to send to the model. If not specified, max_input_tokens is the model’s context length limit minus a small buffer.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>prompt-truncation</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>Defaults to OFF. Dictates how the prompt will be constructed. With promptTruncation set to AUTO_PRESERVE_ORDER, some elements from chatHistory and documents will be dropped to construct a prompt that fits within the model’s context length limit. During this process the order of the documents and chat history will be preserved. With prompt_truncation set to OFF, no elements will be dropped.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>is-search-queries-only</code></p></td>
<td style="text-align: left;"><p>boolean</p></td>
<td style="text-align: left;"><p>When set to true, the response contains only a list of generated search queries without the search results and the model will not respond to the user’s message.</p></td>
</tr>
</tbody>
</table>

### OciGenAiCohereStreamingChatModel

To automatically create and add `OciGenAiStreamingChatModel` to the service registry add the following lines to `application.yaml`:

```yaml
langchain4j:
  providers:
    oci-gen-ai-cohere:
      compartment-id: "ocid1.tenancy.oc1...."
      region: EU_FRANKFURT_1

  models:
    oci-genai-cohere-streaming-chat-model:
      provider: oci-gen-ai-cohere
      model-name: meta.llama-3.3-70b-instruct
```

If `enabled` is set to `false`, the configuration is ignored, and the component is not created.

Full list of configuration properties:

<table>
<colgroup>
<col style="width: 27%" />
<col style="width: 27%" />
<col style="width: 45%" />
</colgroup>
<thead>
<tr>
<th style="text-align: left;">Key</th>
<th style="text-align: left;">Type</th>
<th style="text-align: left;">Description</th>
</tr>
</thead>
<tbody>
<tr>
<td style="text-align: left;"><p><code>enabled</code></p></td>
<td style="text-align: left;"><p>boolean</p></td>
<td style="text-align: left;"><p>If set to false, the component will not be available even if configured.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>model-name</code></p></td>
<td style="text-align: left;"><p>string</p></td>
<td style="text-align: left;"><p>The model name or model’s OCID to use.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>compartment-id</code></p></td>
<td style="text-align: left;"><p>OCID</p></td>
<td style="text-align: left;"><p>OCI Compartment OCID</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>region</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>Explicit region. If not configured, the current one is resolved.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>auth-provider</code></p></td>
<td style="text-align: left;"><p>injected bean</p></td>
<td style="text-align: left;"><p>Injected is default bean if exist, named bean can be configured with <code>auth-provider.service-registry.named: beanName</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>gen-ai-client</code></p></td>
<td style="text-align: left;"><p>injected bean</p></td>
<td style="text-align: left;"><p>Manually configured OCI SDK GenAi client. When set, values provided with <code>region</code> and <code>auth-provider</code> are ignored. Injected is default bean if exists, named bean can be configured with <code>gen-ai-client.service-registry.named: beanName</code></p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>serving-type</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>The model’s serving mode, which is either on-demand serving or dedicated serving.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>top-k</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The maximum number of top-probability tokens to consider when generating text.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>top-p</code></p></td>
<td style="text-align: left;"><p>double between 0 and 1</p></td>
<td style="text-align: left;"><p>If set to a probability 0.0 &lt; p &lt; 1.0, it ensures that only the most likely tokens, with total probability mass of p, are considered for generation at each step.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>seed</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The seed for the random number generator used by the model.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>temperature</code></p></td>
<td style="text-align: left;"><p>double &gt; 0</p></td>
<td style="text-align: left;"><p>A number that sets the randomness of the generated output. A lower temperature means a less random generations.</p>
<p>Use lower numbers for tasks with a correct answer such as question answering or summarizing. High temperatures can generate hallucinations or factually incorrect information. Start with temperatures lower than 1.0 and increase the temperature for more creative outputs, as you regenerate the prompts to refine the outputs. Default is 1.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>presence-penalty</code></p></td>
<td style="text-align: left;"><p>double between -2 and 2</p></td>
<td style="text-align: left;"><p>To reduce repetitiveness of generated tokens, this number penalizes new tokens based on whether they’ve appeared in the generated text so far. Values &gt; 0 encourage the model to use new tokens and values &lt; 0 encourage the model to repeat tokens.</p>
<p>Similar to frequency penalty, a penalty is applied to previously present tokens, except that this penalty is applied equally to all tokens that have already appeared, regardless of how many times they’ve appeared. Set to 0 to disable. Default is 0.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>stop</code></p></td>
<td style="text-align: left;"><p>list of strings</p></td>
<td style="text-align: left;"><p>List of strings that stop the generation if they are generated for the response text. The returned output will not contain the stop strings.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>max-tokens</code></p></td>
<td style="text-align: left;"><p>integer &gt; 1</p></td>
<td style="text-align: left;"><p>The maximum number of tokens that can be generated per output sequence. The token count of your prompt plus maxTokens must not exceed the model’s context length. Not setting a value for maxTokens results in the possible use of model’s full context length.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>frequency-penalty</code></p></td>
<td style="text-align: left;"><p>double between -2 and 2</p></td>
<td style="text-align: left;"><p>To reduce repetitiveness of generated tokens, this number penalizes new tokens based on their frequency in the generated text so far. Values &gt; 0 encourage the model to use new tokens, and values &lt; 0 encourage the model to repeat tokens. Set to 0 to disable. Default is 0.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>is-raw-prompting</code></p></td>
<td style="text-align: left;"><p>boolean</p></td>
<td style="text-align: left;"><p>When enabled, the user’s message will be sent to the model without any preprocessing. Default is false.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>citation-quality</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>When FAST is selected, citations are generated at the same time as the text output and the request will be completed sooner. May result in less accurate citations. Default is ACCURATE.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>preamble-override</code></p></td>
<td style="text-align: left;"><p>string</p></td>
<td style="text-align: left;"><p>If specified, the default Cohere preamble is replaced with the provided preamble. A preamble is an initial guideline message that can change the model’s overall chat behavior and conversation style. Default preambles vary for different models.</p>
<p>Example: You are a travel advisor. Answer with a pirate tone.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>max-input-tokens</code></p></td>
<td style="text-align: left;"><p>int</p></td>
<td style="text-align: left;"><p>The maximum number of input tokens to send to the model. If not specified, max_input_tokens is the model’s context length limit minus a small buffer.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>prompt-truncation</code></p></td>
<td style="text-align: left;"><p>enum</p></td>
<td style="text-align: left;"><p>Defaults to OFF. Dictates how the prompt will be constructed. With promptTruncation set to AUTO_PRESERVE_ORDER, some elements from chatHistory and documents will be dropped to construct a prompt that fits within the model’s context length limit. During this process the order of the documents and chat history will be preserved. With prompt_truncation set to OFF, no elements will be dropped.</p></td>
</tr>
<tr>
<td style="text-align: left;"><p><code>is-search-queries-only</code></p></td>
<td style="text-align: left;"><p>boolean</p></td>
<td style="text-align: left;"><p>When set to true, the response contains only a list of generated search queries without the search results and the model will not respond to the user’s message.</p></td>
</tr>
</tbody>
</table>

## Additional Information

- [LangChain4j Integration](langchain4j.md)
- [LangChain4j OciGenAi Documentation](https://docs.langchain4j.dev/integrations/language-models/oci-genai)
- [Oracle Cloud Infrastructure GenAI Services](https://www.oracle.com/artificial-intelligence/generative-ai/generative-ai-service/)
