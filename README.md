<p align="center">
  <img src="logo.png" alt="Candi" width="160">
</p>

<h1 align="center">Candi</h1>

<p align="center"><strong>Fullstack web framework on Spring Boot. One file per page. Compiled to bytecode.</strong></p>

<p align="center"><em>Named after the ancient stone temples of Java — built to last, built by people who shipped.</em></p>

<p align="center">
  <a href="https://candi.kakrizky.dev">Documentation</a> ·
  <a href="https://candi.kakrizky.dev/docs/getting-started">Getting Started</a> ·
  <a href="./llm_generate_recommendation.md">LLM Guide</a>
</p>

---

## Why Candi

**PHP got it right — then hit a wall.** One file, one page. Route is the file. Logic next to markup. You open it, you see the whole thing. You ship fast. But no compile-time safety, no real DI, testing is awkward, architecture crumbles at scale.

**Java shipped the backend — and lost the frontend.** The industry moved to proper languages, but frontend got separated. Two repos, two build systems, a REST API in between, and a JavaScript framework that needs its own ecosystem just to render a page.

**Most projects don't need that.** Internal tools, admin panels, content sites, early startups — they need one codebase, one deploy, pages that load fast, forms that work without JavaScript.

Candi brings back PHP's fullstack-in-one-file workflow, built on Spring Boot:

```java
@Getter @Setter
@Page("/posts")
@Template("""
<h1>All Posts</h1>
<input name="q" value="{{ q }}">
{{ for post in posts }}
  <article>
    <h2>{{ post.title }}</h2>
    <p>{{ post?.summary ?? "No summary" }}</p>
    <span class="{{ post.published ? "text-green" : "text-gray" }}">
      {{ post.status | capitalize }}
    </span>
  </article>
{{ end }}
""")
public class PostsPage {

    @Autowired private PostService svc;
    @RequestParam(defaultValue = "") String q;
    private List<Post> posts;

    public void init() { posts = svc.search(q); }  // q auto-populated

    @Post
    public ActionResult create() {
        svc.create(ctx.form("title"), ctx.form("body"));
        return ActionResult.redirect("/posts");
    }
}
```

One file. Route, injection, data loading, form handling, HTML — all in one place.

## Quick Start

**Requirements:** Java 21+, Spring Boot 3.4+

```xml
<dependency>
    <groupId>dev.kakrizky.candi</groupId>
    <artifactId>candi-spring-boot-starter</artifactId>
    <version>0.2.0</version>
</dependency>
<dependency>
    <groupId>dev.kakrizky.candi</groupId>
    <artifactId>candi-processor</artifactId>
    <version>0.2.0</version>
    <scope>provided</scope>
</dependency>
```

```groovy
// Gradle
implementation 'dev.kakrizky.candi:candi-spring-boot-starter:0.2.0'
annotationProcessor 'dev.kakrizky.candi:candi-processor:0.2.0'
```

No plugin config. The annotation processor runs automatically during `javac`.

```bash
mvn spring-boot:run
# http://localhost:8080/posts
```

## How It Works

```
PostsPage.java ──▶ Annotation Processor ──▶ PostsPage_Candi.java
                    (Lexer → Parser → Codegen)    extends PostsPage
                                                    implements CandiPage
                                                    + @Component
                                                    + render()
```

Your class stays untouched. The processor generates a `_Candi` subclass with Spring annotations and a compiled `render()` method. Spring registers the generated class — your class is just the parent.

## Features

| Category | Features |
|----------|----------|
| **Core** | Compile-time safety, zero config, real Java with full IDE support |
| **Template** | Ternary `? :`, null coalescing `??`, arithmetic, filters `\|`, index access `[]`, switch/case |
| **Layout** | Named slots, asset stacking, reusable widgets |
| **Data Binding** | Auto `@RequestParam`, `@PathVariable`, `Pageable` — fields populated before `init()` |
| **Server** | AJAX fragments, hot reload with SSE live refresh, GraalVM support |
| **DX** | One language, one process, one deploy. Spring DI just works. |

## Vibe Coding Ready

Candi's single-file architecture makes it **ideal for AI-assisted development**. A blog app in Candi has **30% fewer tokens** than React + NestJS (3,573 vs 5,097 tokens), half the files, one language, and zero API boilerplate. Every new page is one file — no route, no component, no endpoint, no fetch call to wire up.

This means AI can generate **production-grade fullstack apps**, not just prototypes. The compile-time safety catches template errors at build, and Spring DI gives you real architecture — not just code that looks right.

See the [LLM Code Generation Guide](./llm_generate_recommendation.md) for patterns and best practices.

## Documentation

| Resource | Audience | What's inside |
|----------|----------|---------------|
| **[candi.kakrizky.dev](https://candi.kakrizky.dev)** | Developers | Full docs — setup, pages, templates, layouts, widgets, filters, dev tools, fragments |
| **[llm_generate_recommendation.md](./llm_generate_recommendation.md)** | AI agents | Code generation patterns, do's/don'ts, common gotchas, full template syntax reference |
| **This README** | Everyone | High-level overview and quick start |

## Project Structure

```
candi/
├── candi-compiler/             Lexer, parser, code generator
├── candi-runtime/              CandiPage, RequestContext, HtmlOutput, handler mapping
├── candi-processor/            JSR 269 annotation processor (generates _Candi subclasses)
├── candi-dev-tools/            FileWatcher, IncrementalCompiler, SSE live reload
├── candi-maven-plugin/         Maven goals for compilation
├── candi-spring-boot-starter/  Auto-configuration, GraalVM hints
└── candi-demo/                 Integration test app
```

## License

Apache License 2.0
