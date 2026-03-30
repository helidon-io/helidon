# io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiChatModelConfig

## Description

Configuration for LangChain4j model OciGenAiChatModel.

## Usages

- [`langchain4j.providers.oci-gen-ai`](../config/config_reference.md#a4c41d-langchain4j-providers-oci-gen-ai)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="ac2a75-auth-provider"></span> `auth-provider` | `VALUE` | `c.o.b.a.BasicAuthenticationDetailsProvider` |   | Authentication provider is by default used from default Service bean found in Service Registry |
| <span id="a7ecab-auth-provider-discover-services"></span> `auth-provider-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `auth-provider` |
| <span id="ad8e53-compartment-id"></span> `compartment-id` | `VALUE` | `String` |   | OCI Compartment OCID |
| <span id="af16ec-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="af47ca-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="acdf39-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OciGenAiChatModel will not be available even if configured |
| <span id="af6fe7-frequency-penalty"></span> `frequency-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#frequencyPenalty(java.lang.Double)` |
| <span id="aedd48-gen-ai-client"></span> `gen-ai-client` | `VALUE` | `c.o.b.g.GenerativeAiInferenceClient` |   | Custom http client builder |
| <span id="a2593f-gen-ai-client-discover-services"></span> `gen-ai-client-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `gen-ai-client` |
| <span id="a425a5-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#listeners(java.util.List)` |
| <span id="a0959a-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="ae0faf-log-probs"></span> `log-probs` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseGenericChatModel.Builder#logProbs(java.lang.Integer)` |
| <span id="a8571c-logit-bias"></span> `logit-bias` | `MAP` | `Integer` |   | Modifies the likelihood of specified tokens that appear in the completion |
| <span id="a91d11-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#maxTokens(java.lang.Integer)` |
| <span id="afb198-model-name"></span> `model-name` | `VALUE` | `String` |   | OCI LLM Model name or OCID |
| <span id="a33d38-num-generations"></span> `num-generations` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseGenericChatModel.Builder#numGenerations(java.lang.Integer)` |
| <span id="a81cf4-presence-penalty"></span> `presence-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#presencePenalty(java.lang.Double)` |
| <span id="a7cce7-region"></span> `region` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Region is by default set to the current OCI region detected by OCI SDK |
| <span id="aa2ed0-region-discover-services"></span> `region-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `region` |
| <span id="a82b50-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#seed(java.lang.Integer)` |
| <span id="a1e804-serving-type"></span> `serving-type` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#servingType(com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType)` |
| <span id="adac88-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#stop(java.util.List)` |
| <span id="a794ad-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#temperature(java.lang.Double)` |
| <span id="a7b8db-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topK(java.lang.Integer)` |
| <span id="ab6e17-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
