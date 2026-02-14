<p align="center">
  <img src="logo.png" alt="Candi" width="160">
</p>

<h1 align="center">Candi</h1>

<p align="center"><strong>Build production-grade Java web apps as fast as PHP — without sacrificing architecture.</strong></p>

<p align="center">One file per page. Spring Boot underneath. Designed to be AI-predictable.</p>

<p align="center">
  <a href="https://candi.kakrizky.dev">Documentation</a> ·
  <a href="https://candi.kakrizky.dev/docs/getting-started">Quick Start</a> ·
  <a href="./llm_generate_recommendation.md">AI Generation Guide</a>
</p>

---

## Why Candi

- **One file per page.** Route, data loading, form handling, template — all in one Java class. You open the file, you see the whole thing.
- **No API layer for CRUD SaaS.** No controllers, no DTOs, no REST endpoints, no fetch calls. Data flows from database to template. If your frontend only talks to your own backend, why have an API at all?
- **Compile-time template safety.** Templates are compiled at build time. Errors fail compilation, not production. AI mistakes are caught before deploy.
- **Spring Boot underneath.** Real DI, JPA, battle-tested in enterprise. Production-ready from day one, not just a prototype tool.
- **AI-predictable architecture.** One canonical way to build pages. Strict patterns, no routing confusion, no controller/view separation. AI generates working code consistently.

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

    public void init() { posts = svc.search(q); }

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
    <version>0.2.1</version>
</dependency>
<dependency>
    <groupId>dev.kakrizky.candi</groupId>
    <artifactId>candi-processor</artifactId>
    <version>0.2.1</version>
    <scope>provided</scope>
</dependency>
```

```groovy
// Gradle
implementation 'dev.kakrizky.candi:candi-spring-boot-starter:0.2.1'
annotationProcessor 'dev.kakrizky.candi:candi-processor:0.2.1'
```

No plugin config. The annotation processor runs automatically during `javac`.

```bash
mvn spring-boot:run
# http://localhost:8080/posts
```

## How It Works

```
PostsPage.java ──▶ Annotation Processor ──▶ PostsPage_Candi.java
                    (compile time)               extends PostsPage
                                                  implements CandiPage
                                                  + @Component
                                                  + render()
```

Your class stays untouched. The processor generates a `_Candi` subclass with Spring annotations and a compiled `render()` method. Spring registers the generated class — your class is just the parent. Errors are caught at build, not in production.

## What You Get

| | |
|---|---|
| **One file per page** | Route, logic, template — one Java class. No controller/view split. |
| **Zero API layer** | No REST endpoints, no DTOs, no fetch. Data goes straight to template. |
| **Compile-time safety** | Template errors fail the build. Not runtime 500s in production. |
| **Auto parameter binding** | `@RequestParam`, `@PathVariable`, `Pageable` — fields populated before `init()`. |
| **Rich templates** | Ternary `? :`, null coalescing `??`, filters `\|`, arithmetic, switch/case, index `[]`. |
| **Layouts & widgets** | Named slots, asset stacking, reusable components. |
| **AJAX fragments** | Render named sections without JSON. Pair with htmx for interactivity. |
| **Hot reload** | SSE-based live refresh. Edit, save, see changes instantly. |
| **Spring Boot** | Real DI, JPA, security, testing. Everything Spring offers, out of the box. |

## AI-Predictable Architecture

Candi is designed to be predictable for AI code generation. One way to build pages, strict patterns, no ambiguity.

AI generates a full SaaS page in one prompt:

```
Prompt: "Build an item list page with search and pagination"
Output:  One Java file, working CRUD, compiles on first try
```

This works because Candi reduces coordination complexity. No separate API to generate. No frontend state to manage. No routing config to wire up. One file, one page, done.

See the [AI Generation Guide](./llm_generate_recommendation.md) for the full output contract and patterns.

## Candi doesn't block you from evolving

Business logic lives in Spring services. JPA entities work everywhere. You can add REST endpoints alongside Candi pages whenever you need them. Nothing is locked in.

## Documentation

| Resource | What's inside |
|----------|---------------|
| **[candi.kakrizky.dev](https://candi.kakrizky.dev)** | Full docs — setup, pages, templates, layouts, widgets, filters, dev tools |
| **[AI Generation Guide](./llm_generate_recommendation.md)** | Output contract, patterns, do's/don'ts for LLMs generating Candi code |
| **[CHANGELOG](./CHANGELOG.md)** | Version history |

## License

Apache License 2.0
