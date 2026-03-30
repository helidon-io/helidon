# io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiCohereStreamingChatModelConfig

## Description

Configuration for LangChain4j model OciGenAiCohereStreamingChatModel.

## Usages

- [`langchain4j.providers.oci-gen-ai-cohere`](../config/config_reference.md#af05e9-langchain4j-providers-oci-gen-ai-cohere)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="af56d0-auth-provider"></span> `auth-provider` | `VALUE` | `c.o.b.a.BasicAuthenticationDetailsProvider` |   | Authentication provider is by default used from default Service bean found in Service Registry |
| <span id="a2785a-auth-provider-discover-services"></span> `auth-provider-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `auth-provider` |
| <span id="af32ca-citation-quality"></span> [`citation-quality`](../config/com_oracle_bmc_generativeaiinference_model_CohereChatRequest_CitationQuality.md) | `VALUE` | `c.o.b.g.m.C.CitationQuality` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#citationQuality(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.CitationQuality)` |
| <span id="a8ba6d-compartment-id"></span> `compartment-id` | `VALUE` | `String` |   | OCI Compartment OCID |
| <span id="a4035b-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="a663fd-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="aaf4e0-documents"></span> `documents` | `LIST` | `Object` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#documents(java.util.List)` |
| <span id="a028b4-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OciGenAiCohereStreamingChatModel will not be available even if configured |
| <span id="a4b7e2-frequency-penalty"></span> `frequency-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#frequencyPenalty(java.lang.Double)` |
| <span id="a73265-gen-ai-client"></span> `gen-ai-client` | `VALUE` | `c.o.b.g.GenerativeAiInferenceClient` |   | Custom http client builder |
| <span id="a76d3d-gen-ai-client-discover-services"></span> `gen-ai-client-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `gen-ai-client` |
| <span id="abb58a-is-raw-prompting"></span> `is-raw-prompting` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isRawPrompting(java.lang.Boolean)` |
| <span id="a10538-is-search-queries-only"></span> `is-search-queries-only` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isSearchQueriesOnly(java.lang.Boolean)` |
| <span id="af14b3-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#listeners(java.util.List)` |
| <span id="a5d831-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="a6872e-max-input-tokens"></span> `max-input-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#maxInputTokens(java.lang.Integer)` |
| <span id="ad3a6f-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#maxTokens(java.lang.Integer)` |
| <span id="a13edb-model-name"></span> `model-name` | `VALUE` | `String` |   | OCI LLM Model name or OCID |
| <span id="a1b404-preamble-override"></span> `preamble-override` | `VALUE` | `String` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#preambleOverride(java.lang.String)` |
| <span id="a53d0f-presence-penalty"></span> `presence-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#presencePenalty(java.lang.Double)` |
| <span id="a39b15-prompt-truncation"></span> [`prompt-truncation`](../config/com_oracle_bmc_generativeaiinference_model_CohereChatRequest_PromptTruncation.md) | `VALUE` | `c.o.b.g.m.C.PromptTruncation` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#promptTruncation(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.PromptTruncation)` |
| <span id="abdaf9-region"></span> `region` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Region is by default set to the current OCI region detected by OCI SDK |
| <span id="af567f-region-discover-services"></span> `region-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `region` |
| <span id="a7b545-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#seed(java.lang.Integer)` |
| <span id="a14b40-serving-type"></span> `serving-type` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#servingType(com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType)` |
| <span id="ab5aa9-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#stop(java.util.List)` |
| <span id="a6458b-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#temperature(java.lang.Double)` |
| <span id="ac10cd-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topK(java.lang.Integer)` |
| <span id="af6a99-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
