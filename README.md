# Candi

**Fullstack web framework on Spring Boot. One file per page. Compiled to bytecode.**

> *Named after the ancient stone temples of Java — built to last, built by people who shipped.*

```
┌──────────────────────────────────────────────────────────┐
│                     .page.html                           │
│                                                          │
│   ┌─────────────────────┐  ┌──────────────────────────┐  │
│   │  Header             │  │  Body                    │  │
│   │  @page "/posts"     │  │  <h1>{{ title }}</h1>    │  │
│   │  @inject Service s  │  │  {{ for p in posts }}    │  │
│   │  @init { ... }      │  │    <p>{{ p.title }}</p>  │  │
│   │  @action POST { } │  │  {{ end }}               │  │
│   └─────────────────────┘  └──────────────────────────┘  │
│              │                          │                 │
│              ▼                          ▼                 │
│        Java bytecode            HTML renderer             │
│              │                          │                 │
│              └──────────┬───────────────┘                 │
│                         ▼                                 │
│                   Spring Boot                             │
│              (request-scoped bean)                        │
└──────────────────────────────────────────────────────────┘
```

## The Problem

**PHP got it right — then hit a wall.** One file, one page. Route is the file. Logic next to markup. You open it, you see the whole thing. You ship fast. But no compile-time safety, no real DI, testing is awkward, architecture crumbles at scale.

**Java/Go shipped the backend — and lost the frontend.** The industry moved to proper languages with type systems and DI containers. The cost? Frontend got separated. Two repos, two build systems, two deploy pipelines, a REST/GraphQL API in between, and a JavaScript framework that needs its own ecosystem just to render a page.

**Most projects don't need that.** Internal tools, admin panels, content sites, early startups — they need one codebase, one deploy, pages that load fast, forms that work without JavaScript.

## The Approach

Candi brings back PHP's fullstack-in-one-file workflow, built on Spring Boot.

```html
@page "/posts"
@inject PostService posts

@init {
  allPosts = posts.findAll();
}

@action POST {
  posts.create(ctx.form("title"), ctx.form("body"));
  redirect("/posts");
}

<h1>All Posts</h1>
{{ for post in allPosts }}
  <article>
    <h2>{{ post.title }}</h2>
    <p>{{ post?.summary }}</p>
  </article>
{{ end }}

{{ if allPosts.isEmpty() }}
  <p>Nothing here yet.</p>
{{ end }}

<form method="POST">
  <input name="title" placeholder="Title">
  <textarea name="body"></textarea>
  <button>Publish</button>
</form>
```

One file. Route, injection, data loading, form handling, HTML — all in one place. Compiled to bytecode at build time.

## Header Directives

| Directive | Syntax | Purpose |
|-----------|--------|---------|
| `@page` | `@page "/path/{id}"` | Route binding with path params |
| `@inject` | `@inject Type name` | Spring dependency injection |
| `@init` | `@init { code }` | Load data, run setup logic |
| `@action` | `@action POST { code }` | Handle POST, PUT, DELETE |
| `@fragment` | `@fragment "name" { html }` | HTMX-friendly partial |
| `@layout` | `@layout "name"` | Wrap in layout template |
| `@slot` | `@slot name { html }` | Fill named layout slot |

## Template Expressions

| Syntax | Output |
|--------|--------|
| `{{ title }}` | HTML-escaped output |
| `{{ raw content }}` | Unescaped output |
| `{{ post.title }}` | Property access (calls `getTitle()`) |
| `{{ post?.title }}` | Null-safe access |
| `{{ if cond }}...{{ end }}` | Conditional |
| `{{ if a }}...{{ else }}...{{ end }}` | If/else |
| `{{ for item in list }}...{{ end }}` | Loop |
| `{{ fragment "name" }}` | Include fragment |
| `{{ component "card" title="Hello" }}` | Render component |
| `{{ slot content }}` | Render layout slot |

## Quick Start

**Requirements:** Java 21+, Maven 3.9+

```xml
<!-- pom.xml -->
<dependency>
  <groupId>candi</groupId>
  <artifactId>candi-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>

<plugin>
  <groupId>candi</groupId>
  <artifactId>candi-maven-plugin</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <executions>
    <execution>
      <goals><goal>compile</goal></goals>
    </execution>
  </executions>
</plugin>
```

Create `src/main/candi/hello.page.html`:

```html
@page "/hello"

<h1>Hello from Candi</h1>
<p>The time is {{ java.time.LocalTime.now() }}</p>
```

```bash
mvn spring-boot:run
# Open http://localhost:8080/hello
```

## Dev Mode

Hot reload with live browser refresh. Edit, save, see changes instantly.

```bash
mvn candi:dev
```

Or via property:

```properties
candi.dev=true
```

File watcher detects changes → recompiles → hot-swaps class → SSE pushes reload to browser. No server restart.

## Project Structure

```
candi/
├── candi-compiler/             Lexer, parser, code generator (zero Spring deps)
├── candi-runtime/              CandiPage interface, handler mapping, request lifecycle
├── candi-dev-tools/            FileWatcher, IncrementalCompiler, HotReloadManager
├── candi-maven-plugin/         Maven goals: candi:compile, candi:dev
├── candi-spring-boot-starter/  Auto-configuration, GraalVM hints
└── candi-demo/                 Integration test app with example pages
```

## Features

| Feature | Detail |
|---------|--------|
| Compile-time safety | Template errors caught at build, not runtime |
| Spring Boot native | Use any Spring service, repository, component via `@inject` |
| HTMX-ready | `@fragment` serves partials via `HX-Fragment` header |
| Layouts & components | `@layout` + `{{ component }}` for reuse |
| Null-safe expressions | `?.` compiles to Java null checks |
| Hot reload | SSE-based live reload, no server restart |
| GraalVM compatible | Runtime hints included for native image |

## IDE Support

IntelliJ plugin with syntax highlighting, code completion, Ctrl+click navigation, and run configuration.

```bash
cd candi-intellij
./gradlew buildPlugin
# Install from build/distributions/candi-intellij-0.1.0.zip
```

## Intentional Trade-offs

| Choice | Reason |
|--------|--------|
| Maven only (no Gradle) | Spring Boot convention, simpler plugin |
| No JavaScript runtime | Pages render server-side, HTMX for interactivity |
| Single-file pages | Simplicity over component granularity |
| Go-style `{{ }}` syntax | Familiar, unambiguous, easy to parse |
| No JSP/Thymeleaf interop | Clean break, no legacy baggage |

## Use Cases

**Good fit:**
- Internal tools, admin panels
- Content sites, blogs, documentation
- CRUD apps, dashboards
- Startups shipping MVPs
- Any project where "just make a web page" is the goal

**Not a fit:**
- Heavy client-side interactivity (use React/Vue)
- Real-time apps (use WebSocket frameworks)
- Microservice API-only backends (no HTML needed)

## License

MIT
