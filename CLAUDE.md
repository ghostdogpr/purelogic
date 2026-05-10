# Claude Development Notes

## Build and Test Commands

**If the metals MCP server is available, prefer its tools** — check the available `mcp__metals__*` list before reaching for `sbt`, `grep`, Context7, or web search. Specifically:
- Scala compile / test / format → metals (not `sbt` shell).
- Maven/coursier lookups, including latest versions → `find-dep` (not Context7/web).
- Scala symbol search, usages, docs, source → `glob-search` / `get-usages` / `get-docs` / `get-source` (not `grep`). Use `grep` only for non-Scala text.
- Codebase-wide pattern refactors → `generate-scalafix-rule` instead of editing files one by one.

If metals is not available, fall back to `sbt --client` (existing server, never a fresh one) and standard tools.

After modifying a Scala file, format it via `mcp__metals__format-file` if metals is available; otherwise use `sbt --client scalafmt`.
