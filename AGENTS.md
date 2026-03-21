# AGENTS.md

## Scope
- `@DEV-GUIDELINES.md` is the source of truth for API, style, and design rules.
- This file defines agent workflow and validation only.

## Environment
- Use JDK 21 and Maven 3.8+.

## Workflow
- Read the nearest module `pom.xml` and nearby tests before editing.
- Keep diffs minimal and scoped to the touched module.
- Do not reformat unrelated files.
- Do not change public APIs, dependency versions, or module structure unless the task requires it.
- If work may span repositories, stop at the boundary unless explicitly asked.

## Validation
- Docs only: `cd docs && mvn package`
- Docs + javadocs: `cd docs && mvn package -Pjavadoc`
- Single-module code changes: `mvn -pl <module> -am test`
- Shared or root build changes: run targeted Maven validation and `@etc/scripts/checkstyle.sh` plus `@etc/scripts/copyright.sh`
- If validation is partial, say so explicitly.

## Repo Map
- Docs: `docs/src/main/asciidoc`
- Doc snippets: `docs/src/main/java`
- Test-heavy areas: `tests`, `*/tests`, `*/testing`
- Reactor modules: see root `pom.xml`

## Output
- Call out any unverified risk.

## Formatting
- Use @etc/codestyle/idea-code-style.xml for IDE formatting.

## Related Repositories
- Adjacent repositories: `helidon-io/helidon-examples`, `helidon-io/helidon-extensions` (HashiCorp Vault, Neo4j), `helidon-io/helidon-build-tools` (build tools, Maven plugins, CLI), `helidon-io/helidon-microprofile` (post-4.x MicroProfile work), and `helidon-io/helidon-mcp`.
