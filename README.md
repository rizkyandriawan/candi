# Candi

**Fullstack web framework on Spring Boot. One file per page. Compiled to bytecode.**

> *Named after the ancient stone temples of Java — built to last, built by people who shipped.*

```
┌──────────────────────────────────────────────────────────┐
│                     .page.html                           │
│                                                          │
│   ┌─────────────────────────┐  ┌──────────────────────┐  │
│   │  Java Class             │  │  <template>          │  │
│   │  @Page("/posts")        │  │  <h1>{{ title }}</h1> │  │
│   │  @Autowired Service s   │  │  {{ for p in posts }}│  │
│   │  init() { ... }         │  │    <p>{{ p.title }}</p>│ │
│   │  @Post create() { }    │  │  {{ end }}           │  │
│   └─────────────────────────┘  └──────────────────────┘  │
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

Candi brings back PHP's fullstack-in-one-file workflow, built on Spring Boot. The top half is a real Java class with fields, methods, and annotations. The bottom half is an HTML template. The compiler reads both, then generates a single Spring bean with a `render()` method.

```html
@Page("/posts")
public class PostsPage {

    @Autowired
    private PostService posts;

    private List<Post> allPosts;

    public void init() {
        allPosts = posts.findAll();
    }

    @Post
    public ActionResult create() {
        posts.create(ctx.form("title"), ctx.form("body"));
        return ActionResult.redirect("/posts");
    }
}

<template>
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
</template>
```

One file. Route, injection, data loading, form handling, HTML — all in one place. The Java part is real Java with full IDE support. Compiled to bytecode at build time.

## Java Class

The top section is a standard Java class. The compiler preserves it and adds Spring annotations + a `render()` method.

| Annotation | Purpose |
|------------|---------|
| `@Page("/path/{id}")` | Route binding with path params |
| `@Layout("name")` | Wrap page in a layout template |
| `@Autowired` | Spring dependency injection |
| `@Post` | Handle POST requests |
| `@Put` | Handle PUT requests |
| `@Delete` | Handle DELETE requests |
| `@Patch` | Handle PATCH requests |

The `init()` method runs once per request before rendering. Action methods (`@Post`, `@Delete`, etc.) return `ActionResult` for redirects or re-renders.

## Template Expressions

The `<template>` section uses Go-style `{{ }}` syntax:

| Syntax | Output |
|--------|--------|
| `{{ title }}` | HTML-escaped output |
| `{{ raw content }}` | Unescaped output |
| `{{ post.title }}` | Property access (calls `getTitle()`) |
| `{{ post?.title }}` | Null-safe access |
| `{{ if cond }}...{{ end }}` | Conditional |
| `{{ if a }}...{{ else }}...{{ end }}` | If/else |
| `{{ if a }}...{{ else if b }}...{{ end }}` | Else-if chain |
| `{{ for item in list }}...{{ end }}` | Loop |
| `{{ include "header" title="Home" }}` | Include HTML partial |
| `{{ component "card" title="Hello" }}` | Render component |
| `{{ content }}` | Layout content placeholder |

## Layouts

A layout is a `.layout.html` file with a Java class and a `{{ content }}` placeholder:

```html
public class BaseLayout {
}

<template>
<!DOCTYPE html>
<html>
<head><title>My App</title></head>
<body>
  <nav>My App</nav>
  {{ content }}
  <footer>Powered by Candi</footer>
</body>
</html>
</template>
```

Pages opt into a layout with `@Layout("base")`. The page's template is injected at `{{ content }}`.

## Components

Reusable UI elements with parameters:

```html
public class AlertComponent {
    private String type;
    private String message;
}

<template>
<div class="alert alert-{{ type }}">{{ message }}</div>
</template>
```

Used in any page: `{{ component "alert" type="error" message="Oops" }}`

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
@Page("/hello")
public class HelloPage {
}

<template>
<h1>Hello from Candi</h1>
</template>
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
| Real Java | Fields, methods, annotations — full IDE support in the class section |
| Spring Boot native | Use any Spring service, repository, component via `@Autowired` |
| Layouts & components | `@Layout` + `{{ component }}` + `{{ include }}` for reuse |
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
