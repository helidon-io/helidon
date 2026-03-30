# io.helidon.integrations.langchain4j.providers.oci.genai.OciGenAiStreamingChatModelConfig

## Description

Configuration for LangChain4j model OciGenAiStreamingChatModel.

## Usages

- [`langchain4j.providers.oci-gen-ai`](../config/config_reference.md#a8c210-langchain4j-providers-oci-gen-ai)

## Configuration options

| Key | Kind | Type | Default Value | Description |
|----|----|----|----|----|
| <span id="a6ec5b-auth-provider"></span> `auth-provider` | `VALUE` | `c.o.b.a.BasicAuthenticationDetailsProvider` |   | Authentication provider is by default used from default Service bean found in Service Registry |
| <span id="ade139-auth-provider-discover-services"></span> `auth-provider-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `auth-provider` |
| <span id="af0eb4-compartment-id"></span> `compartment-id` | `VALUE` | `String` |   | OCI Compartment OCID |
| <span id="a658d7-default-request-parameters"></span> `default-request-parameters` | `VALUE` | `d.l.m.c.r.ChatRequestParameters` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#defaultRequestParameters(dev.langchain4j.model.chat.request.ChatRequestParameters)` |
| <span id="aba4d4-default-request-parameters-discover-services"></span> `default-request-parameters-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `default-request-parameters` |
| <span id="aa8801-enabled"></span> `enabled` | `VALUE` | `Boolean` | `true` | If set to `false`, OciGenAiStreamingChatModel will not be available even if configured |
| <span id="a18928-frequency-penalty"></span> `frequency-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#frequencyPenalty(java.lang.Double)` |
| <span id="aae3db-gen-ai-client"></span> `gen-ai-client` | `VALUE` | `c.o.b.g.GenerativeAiInferenceClient` |   | Custom http client builder |
| <span id="a090bc-gen-ai-client-discover-services"></span> `gen-ai-client-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `gen-ai-client` |
| <span id="a9469b-listeners"></span> `listeners` | `LIST` | `d.l.m.c.l.ChatModelListener` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#listeners(java.util.List)` |
| <span id="abcb6d-listeners-discover-services"></span> `listeners-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `listeners` |
| <span id="a04e8d-log-probs"></span> `log-probs` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseGenericChatModel.Builder#logProbs(java.lang.Integer)` |
| <span id="ad1019-logit-bias"></span> `logit-bias` | `MAP` | `Integer` |   | Modifies the likelihood of specified tokens that appear in the completion |
| <span id="a0d02a-max-tokens"></span> `max-tokens` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#maxTokens(java.lang.Integer)` |
| <span id="a314ff-model-name"></span> `model-name` | `VALUE` | `String` |   | OCI LLM Model name or OCID |
| <span id="a779b3-num-generations"></span> `num-generations` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseGenericChatModel.Builder#numGenerations(java.lang.Integer)` |
| <span id="aadde7-presence-penalty"></span> `presence-penalty` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#presencePenalty(java.lang.Double)` |
| <span id="a6fe0b-region"></span> `region` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Region is by default set to the current OCI region detected by OCI SDK |
| <span id="a81855-region-discover-services"></span> `region-discover-services` | `VALUE` | `Boolean` | `true` | Whether to enable automatic service discovery for `region` |
| <span id="a31fac-seed"></span> `seed` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#seed(java.lang.Integer)` |
| <span id="acd5a5-serving-type"></span> `serving-type` | `VALUE` | `i.h.i.l.p.o.g.OciFactoryMethods` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#servingType(com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType)` |
| <span id="ab6f50-stop"></span> `stop` | `LIST` | `String` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#stop(java.util.List)` |
| <span id="a9433b-temperature"></span> `temperature` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#temperature(java.lang.Double)` |
| <span id="abe192-top-k"></span> `top-k` | `VALUE` | `Integer` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topK(java.lang.Integer)` |
| <span id="a899ab-top-p"></span> `top-p` | `VALUE` | `Double` |   | Generated from `dev.langchain4j.community.model.oracle.oci.genai.BaseChatModel.Builder#topP(java.lang.Double)` |

See the [manifest](../config/manifest.md) for all available types.
