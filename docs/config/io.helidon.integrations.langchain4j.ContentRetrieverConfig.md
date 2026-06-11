# io.helidon.integrations.langchain4j.ContentRetrieverConfig

## Description

Configuration for LangChain4j <code>Content<wbr>Retriever</code> components

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
<code>embedding-<wbr>model</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Explicit embedding model to use in the content retriever</td>
</tr>
<tr>
<td>
<code>display-<wbr>name</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Display name for this content retriever configuration</td>
</tr>
<tr>
<td>
<code>embedding-<wbr>store</code>
</td>
<td>
<code>String</code>
</td>
<td>
</td>
<td>Embedding store to use in the content retriever</td>
</tr>
<tr>
<td>
<code>max-<wbr>results</code>
</td>
<td>
<code>Integer</code>
</td>
<td>
</td>
<td>Maximum number of results to return from the retriever</td>
</tr>
<tr>
<td>
<a id="type"></a>
<a href="io.helidon.integrations.langchain4j.ContentRetrieverType.md">
<code>type</code>
</a>
</td>
<td>
<code>Content<wbr>Retriever<wbr>Type</code>
</td>
<td>
<code>EMBEDDING_<wbr>STORE_<wbr>CONTENT_<wbr>RETRIEVER</code>
</td>
<td>Type of content retriever to create</td>
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
<td>If set to <code>false</code>, component will be disabled even if configured</td>
</tr>
<tr>
<td>
<code>min-<wbr>score</code>
</td>
<td>
<code>Double</code>
</td>
<td>
</td>
<td>Minimum score threshold for retrieved results</td>
</tr>
</tbody>
</table>



---

See the [manifest](manifest.md) for all available types.
