# OpenAPI clarification assessment

This assessment records the document-model impact of OpenAPI 3.0.4, 3.1.2,
and 3.2.1 for [issue #12607](https://github.com/helidon-io/helidon/issues/12607).
It covers the model, parser, renderer, and structural validators in this
directory. These APIs remain preview. The checks described here are partial;
this is not a claim of full OpenAPI or JSON Schema conformance.

## Sources and version policy

The authoritative sources are the published HTML specifications:
[3.0.4](https://spec.openapis.org/oas/v3.0.4.html),
[3.1.2](https://spec.openapis.org/oas/v3.1.2.html), and
[3.2.1](https://spec.openapis.org/oas/v3.2.1.html).
Each identifies that HTML edition as its source of truth. Clarifications apply
to their major/minor line, rather than introducing patch-specific feature
switches.

The version providers accept the new patch identifiers. Their generated-output
defaults deliberately remain **3.0.3**, **3.1.1**, and **3.2.0**, preserving
existing generated documents. Applications can select the clarification
edition explicitly using the provider's `version` setting. See the
[user documentation](../docs/modules/openapi/openapi.md#openapi-document-versions).

The 3.2.1 release summary describes a prohibition of `style: form` with
`explode: true` in Encoding Objects, but the authoritative HTML still describes
that combination. This implementation follows the HTML and introduces no such
prohibition. Likewise, a serialization-table cell marked `n/a` denotes undefined
behavior, including in 3.2; it is not by itself a document-rejection rule.
Explicit field restrictions are assessed separately below.

## Document handling

“Satisfied” means the existing representation or handling already accommodates
the clarification; “fixed” identifies document-validation changes in this issue.
Neither status asserts that an HTTP serialization engine implements the
corresponding wire behavior.

| Area | Status | Model, parser, renderer, and validator assessment |
| --- | --- | --- |
| Patch versions | Satisfied | The [3.0](openapi/src/main/java/io/helidon/openapi/v30/OpenApi30Version.java), [3.1](openapi-31/src/main/java/io/helidon/openapi/v31/OpenApi31Version.java), and [3.2](openapi-32/src/main/java/io/helidon/openapi/v32/OpenApi32Version.java) providers accept patch identifiers within their line. Mapper feature selection uses the major/minor dialect. |
| Parameter styles and Header Objects | Fixed | [OpenApiStructuralValidator](openapi/src/main/java/io/helidon/openapi/v30/OpenApiStructuralValidator.java) checks styles against `in` when parsing and rendering generated documents; `cookie` style is available only in 3.2. Parsing rejects prohibited Header Object fields: `name`, `in`, `allowEmptyValue`, and, in 3.0/3.1, `allowReserved`. Renderer projection already drops nonapplicable fields, including Header Object `allowReserved` in 3.2. For 3.2, explicit schema types disjoint from the types supported by `deepObject`, `spaceDelimited`, or `pipeDelimited` are rejected, as are explicit array/object types for cookie parameters with `form` or `cookie` style and `explode: false`. These checks do not infer an effective type through arbitrary JSON Schema evaluation. |
| Querystring parameters | Satisfied | [OpenApiQueryStringValidator](openapi/src/main/java/io/helidon/openapi/v30/OpenApiQueryStringValidator.java) checks the 3.2 querystring restrictions, including conflicts with individual query parameters. Earlier dialects do not expose this location. |
| Paths | Satisfied | [OpenApiDocument](openapi/src/main/java/io/helidon/openapi/OpenApiDocument.java) already rejects duplicate templated path hierarchies such as `/pets/{id}` and `/pets/{name}` through `mergePathItems`, `equivalentPathTemplate`, and `normalizedPathTemplate`. This covers model building and parsing. A slash in a template **name**, such as `{a/b}`, is legal; a substituted parameter **value** cannot span path segments using an unescaped slash. Document checks must not confuse these two cases. |
| Encoding metadata | Satisfied | [OpenApiDocument](openapi/src/main/java/io/helidon/openapi/OpenApiDocument.java) and [OpenApiDocumentMapperSupport](openapi/src/main/java/io/helidon/openapi/v30/OpenApiDocumentMapperSupport.java) retain explicit `contentType`, `style`, `explode`, and `allowReserved` fields and preserve their absence. They do not materialize serialization defaults or normalize precedence into a different document. A `contentType` comma-list, including a quoted parameter containing a comma, remains one unchanged string. |
| Positional multipart encoding | Fixed | [OpenApiMediaTypeValidator](openapi/src/main/java/io/helidon/openapi/v30/OpenApiMediaTypeValidator.java) retains mutual exclusion of `encoding` with `prefixEncoding`/`itemEncoding`. Positional encoding accepts absent, boolean, and empty schemas, and rejects an explicit inline type excluding arrays unless `itemSchema` is supplied. It preserves OpenAPI Media Type Object reference resolution but defers JSON Schema `$ref` type inference, which requires `$id` scope and sibling semantics rather than the OpenAPI Reference Object resolver. |
| Reference siblings | Satisfied | The shared mapper preserves Schema Object `$ref` siblings in 3.1/3.2; 3.0 Reference Object siblings remain ignored. JSON Schema references and OpenAPI Reference Objects therefore retain distinct handling. |
| Examples and escaping | Satisfied | Header and cookie examples remain document data. Parsing and rendering preserve their percent-escaped text rather than applying URI or form encoding to the examples. |

## Runtime boundaries

These modules compose and serve API descriptions. They contain no OpenAPI
request matcher or parameter/body serialization engine. The following work is
outside this assessment:

1. HTTP parameter serialization, including reserved characters, header/cookie
   escaping, undefined style combinations, and the runtime precedence of
   Encoding Object `contentType` versus `style`, `explode`, and `allowReserved`.
2. Multipart array-to-part mapping, per-part media type selection, and handling
   media type lists. The document retains this metadata for consumers.
3. Actual route matching and substitution of path parameter values. The path
   checks above validate description structure without promising routing behavior.
4. Instance-level JSON Schema validation, effective-type inference, and external
   schema loading. Structural validation cannot establish that a request or
   response instance satisfies its schema.

## Regression coverage

`OpenApiClarificationsTest` in `openapi-32` covers shared behavior across
3.0.3/3.0.4, 3.1.1/3.1.2, and 3.2.0/3.2.1. The
[3.2 mapper tests](openapi-32/src/test/java/io/helidon/openapi/v32/OpenApi32DocumentMapperTest.java)
cover encoding representation and positional-encoding checks. These references
describe test scope; execution results belong in the change's validation report.
