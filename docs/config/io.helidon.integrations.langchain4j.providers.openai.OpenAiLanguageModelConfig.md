# io.helidon.integrations.langchain4j.providers.openai.OpenAiLanguageModelConfig

## Description

Configuration for LangChain4j model OpenAiLanguageModel

## Configuration options


<table>
<thead>
<tr>
<th>Key</th>
<th>Type</th>
<th>Default</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td>
<code>base-<wbr>url</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>base<wbr>Url(<wbr>java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>custom-<wbr>query-<wbr>params</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>custom<wbr>Query<wbr>Params(<wbr>java.<wbr>util.<wbr>Map)</code></td>
</tr>
<tr>
<td>
<code>http-<wbr>client-<wbr>builder</code>
</td>
<td>
<code>Http<wbr>Client<wbr>Builder</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>http<wbr>Client<wbr>Builder(<wbr>dev.<wbr>langchain4j.<wbr>http.<wbr>client.<wbr>Http<wbr>Client<wbr>Builder)</code></td>
</tr>
<tr>
<td>
<code>custom-<wbr>headers</code>
</td>
<td>
<code>Map&lt;<wbr>String,<wbr> String&gt;</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>custom<wbr>Headers(<wbr>java.<wbr>util.<wbr>Map)</code></td>
</tr>
<tr>
<td>
<code>logger</code>
</td>
<td>
<code>Logger</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>logger(<wbr>org.<wbr>slf4j.<wbr>Logger)</code></td>
</tr>
<tr>
<td>
<code>max-<wbr>retries</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>maxRetries(<wbr>java.<wbr>lang.<wbr>Integer)</code></td>
</tr>
<tr>
<td>
<code>log-<wbr>responses</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>logResponses(<wbr>java.<wbr>lang.<wbr>Boolean)</code></td>
</tr>
<tr>
<td>
<code>enabled</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>If set to <code>false</code>, OpenAiLanguageModel will not be available even if configured</td>
</tr>
<tr>
<td>
<code>timeout</code>
</td>
<td>
<code>Duration</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>timeout(<wbr>java.<wbr>time.<wbr>Duration)</code></td>
</tr>
<tr>
<td>
<code>log-<wbr>requests</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>logRequests(<wbr>java.<wbr>lang.<wbr>Boolean)</code></td>
</tr>
<tr>
<td>
<code>api-<wbr>key</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>apiKey(<wbr>java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>http-<wbr>client-<wbr>builder-<wbr>discover-<wbr>services</code>
</td>
<td>
<code>Boolean</code>
</td>
<td>
<code>true</code>
</td>
<td>Whether to enable automatic service discovery for <code>http-<wbr>client-<wbr>builder</code></td>
</tr>
<tr>
<td>
<code>temperature</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>temperature(<wbr>java.<wbr>lang.<wbr>Double)</code></td>
</tr>
<tr>
<td>
<code>model-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>model<wbr>Name(<wbr>java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>organization-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>organization<wbr>Id(java.<wbr>lang.<wbr>String)</code></td>
</tr>
<tr>
<td>
<code>project-<wbr>id</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Generated from <code>dev.<wbr>langchain4j.<wbr>model.<wbr>openai.<wbr>Open<wbr>AiLanguage<wbr>Model.<wbr>Open<wbr>AiLanguage<wbr>Model<wbr>Builder#<wbr>project<wbr>Id(java.<wbr>lang.<wbr>String)</code></td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
