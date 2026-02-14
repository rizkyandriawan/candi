# Changelog

All notable changes to Candi are documented in this file.

## [0.2.1] — 2026-02-14

### Fixed

- **super.init() guard** — Generated `_Candi` subclass no longer calls `super.init()` when the parent page class has no `init()` method, preventing potential errors
- **candi-demo module** — Excluded from Maven Central publishing to avoid missing sources/javadoc/signature errors during deploy

### Added

- **Field-level `@RequestParam` and `@PathVariable` annotations** — Added `candi.runtime.RequestParam` and `candi.runtime.PathVariable` annotations to candi-runtime, allowing pages to declare parameter bindings without depending on Spring Web annotations

### Changed

- Rewrote README with outcome-focused branding and AI Output Contract
- Added AI Output Contract (12 rules) to LLM generation guide

## [0.2.0] — 2026-02-14

### Added

- **Auto Parameter Binding** — `@RequestParam`, `@PathVariable`, and `Pageable` fields on `@Page` classes are automatically populated before `init()` runs. Zero boilerplate for query params, path variables, and pagination.
  - Supported types: `String`, `int`/`Integer`, `long`/`Long`, `double`/`Double`, `boolean`/`Boolean`
  - `@RequestParam(defaultValue = "...")` for default values
  - `@RequestParam(required = true)` throws `IllegalArgumentException` if missing
  - `@PathVariable` binds from URL path segments (e.g., `/items/{id}`)
  - `Pageable` auto-binds from `?page=`, `?size=`, `?sort=` query params
  - Compile-time code generation — no reflection at runtime
  - Validates setter availability at compile time (works with Lombok `@Setter`/`@Data`)
  - Full backward compatibility — no overhead when annotations are absent

### Changed

- Updated LLM guide patterns (Pattern A, C) to use `@RequestParam`/`@PathVariable` instead of manual `ctx.query()`/`ctx.path()`

## [0.1.0] — 2025-12-01

### Added

- Initial release
- **Core**: Compile-time template engine, annotation processor generating `_Candi` subclasses
- **Template syntax**: `{{ variable }}`, `{{ raw }}`, `{{ if }}`/`{{ else }}`, `{{ for }}`, `{{ include }}`, `{{ widget }}`, `{{ component }}`
- **Expression engine**: Ternary `? :`, null coalescing `??`, arithmetic `+ - * / %`, string concat `~`, filters `|`, index access `[]`, comparisons, boolean logic
- **Control flow**: `{{ switch }}`/`{{ case }}`/`{{ default }}`, `{{ set variable = expr }}`
- **Layout system**: `{{ content }}`, named slots `{{ slot }}`/`{{ block }}`, asset stacking `{{ push }}`/`{{ stack }}`
- **Widgets**: Reusable components with `{{ widget "name" param="value" }}`
- **AJAX fragments**: `{{ fragment "name" }}` for partial page updates via `Candi-Fragment` header
- **Comments**: `{{-- hidden --}}`, verbatim `{{ verbatim }}...{{ end }}`, whitespace control `{{- -}}`
- **Loop metadata**: `item_index`, `item_first`, `item_last` in `{{ for }}` loops
- **Filters**: `upper`, `lower`, `capitalize`, `trim`, `length`, `truncate`, `replace`, `date`, `number`, `join`, `default`
- **Runtime**: `RequestContext`, `ActionResult`, `HtmlOutput`, `SelectOption`, `CheckboxOption`
- **Spring Boot**: Auto-configuration, request-scoped pages, DI, `@Post`/`@Put`/`@Delete`/`@Patch`
- **Dev tools**: `FileWatcher`, `IncrementalCompiler`, SSE live reload
- **Maven plugin**: Compilation goals
- **GraalVM**: Native image support via Spring Boot starter
- **Maven Central**: Published under `dev.kakrizky.candi`
