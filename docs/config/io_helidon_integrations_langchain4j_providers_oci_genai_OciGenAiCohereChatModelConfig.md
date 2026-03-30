# io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiCohereChatModelConfig

## Description

Configuration for LangChain4j model OciGenAiCohereChatModel.

## Usages

- [`langchain4j.providers.oci-gen-ai-cohere`](../config/config_reference.md#a456bb-langchain4j-providers-oci-gen-ai-cohere)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a69622-auth-provider"></span> `auth-provider` | `VALUE` | `c.o.b.a.BasicAuthenticationDetailsProvider` |   | Authentication provider is by default used from default Service bean found in Service Registry |
| <span id="ac65b8-auth-provider-discover-services"></span> `auth-provider-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `auth-provider` |
| <span id="ae9150-citation-quality"></span> [`citation-quality`](../config/com_oracle_bmc_generativeaiinference_model_CohereChatRequest_CitationQuality.md) | `VALUE` | `c.o.b.g.m.C.CitationQuality` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#citationQuality(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.CitationQuality)` |
| <span id="a0d9d9-compartment-id"></span> `compartment-id` | `VALUE` | `String` |   | OCI Compartment OCID |
| <span id="af97a5-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="a6edbc-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="a4bde2-documents"></span> `documents` | `LIST` | `Object` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#documents(java.util.List)` |
| <span id="a5d8e1-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OciGenAiCohereChatModel will not be available even if configured |
| <span id="acf8ff-frequency-penalty"></span> `frequency-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#frequencyPenalty(java.lang.Double)` |
| <span id="abdfe6-gen-ai-client"></span> `gen-ai-client` | `VALUE` | `c.o.b.g.GenerativeAiInferenceClient` |   | Custom http client builder |
| <span id="afb7de-gen-ai-client-discover-services"></span> `gen-ai-client-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `gen-ai-client` |
| <span id="aaedcc-is-raw-prompting"></span> `is-raw-prompting` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isRawPrompting(java.lang.Boolean)` |
| <span id="ad6a26-is-search-queries-only"></span> `is-search-queries-only` | `VALUE` | `Boolean` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#isSearchQueriesOnly(java.lang.Boolean)` |
| <span id="acc6d9-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#listeners(java.util.List)` |
| <span id="abdb1b-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="a485d1-max-input-tokens"></span> `max-input-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#maxInputTokens(java.lang.Integer)` |
| <span id="aa150c-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#maxTokens(java.lang.Integer)` |
| <span id="a60114-model-name"></span> `model-name` | `VALUE` | `String` |   | OCI LLM Model name or OCID |
| <span id="a70820-preamble-override"></span> `preamble-override` | `VALUE` | `String` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#preambleOverride(java.lang.String)` |
| <span id="a953b6-presence-penalty"></span> `presence-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#presencePenalty(java.lang.Double)` |
| <span id="a2e3fd-prompt-truncation"></span> [`prompt-truncation`](../config/com_oracle_bmc_generativeaiinference_model_CohereChatRequest_PromptTruncation.md) | `VALUE` | `c.o.b.g.m.C.PromptTruncation` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseCohereChatModel.Builder#promptTruncation(com.oracle.bmc.generativeaiinference.model.CohereChatRequest.PromptTruncation)` |
| <span id="a2ae20-region"></span> `region` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Region is by default set to the current OCI region detected by OCI SDK |
| <span id="a4fc3d-region-discover-services"></span> `region-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `region` |
| <span id="a842ff-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#seed(java.lang.Integer)` |
| <span id="add2c0-serving-type"></span> `serving-type` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#servingType(com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType)` |
| <span id="a3d0d0-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#stop(java.util.List)` |
| <span id="ad8fe8-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#temperature(java.lang.Double)` |
| <span id="a8561c-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topK(java.lang.Integer)` |
| <span id="a7cc26-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
