<p align="center">
  <img src="logo.png" alt="Candi" width="160">
</p>

<h1 align="center">Candi</h1>

<p align="center"><strong>Fullstack web framework on Spring Boot. One file per page. Compiled to bytecode.</strong></p>

<p align="center"><em>Named after the ancient stone temples of Java — built to last, built by people who shipped.</em></p>

---

## The Problem

**PHP got it right — then hit a wall.** One file, one page. Route is the file. Logic next to markup. You open it, you see the whole thing. You ship fast. But no compile-time safety, no real DI, testing is awkward, architecture crumbles at scale.

**Java/Go shipped the backend — and lost the frontend.** The industry moved to proper languages with type systems and DI containers. The cost? Frontend got separated. Two repos, two build systems, two deploy pipelines, a REST/GraphQL API in between, and a JavaScript framework that needs its own ecosystem just to render a page.

**Most projects don't need that.** Internal tools, admin panels, content sites, early startups — they need one codebase, one deploy, pages that load fast, forms that work without JavaScript.

## The Approach

Candi brings back PHP's fullstack-in-one-file workflow, built on Spring Boot. Each page is a Java class with a `@Template` annotation. The annotation processor generates a subclass with `render()` at compile time — no plugins, no build tool config.

Works with Maven, Gradle, Bazel — anything that runs `javac`.

```java
@Page("/posts")
@Template("""
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
""")
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
```

One file. Route, injection, data loading, form handling, HTML — all in one place. The Java part is real Java with full IDE support. Compiled to bytecode at build time.

## How It Works

```
                  javac (annotation processor)
                  ┌──────────────────────────┐
 PostsPage.java──>│ @Page + @Template found   │──> PostsPage_Candi.java
                  │ Lexer → Parser → Codegen  │    extends PostsPage
                  └──────────────────────────┘    implements CandiPage
                                                   + @Component
                                                   + @CandiRoute
                                                   + render()
```

Your class stays untouched. The processor generates a `_Candi` subclass with Spring annotations and a compiled `render()` method. Spring registers the generated class — your class is just the parent.

## File Types

| Annotation | Type | What it does |
|------------|------|--------------|
| `@Page("/path")` | Page | Request-scoped bean, handles HTTP requests at the given route |
| `@Layout` | Layout | Singleton wrapper template with a `{{ content }}` slot |
| `@Widget` | Widget | Prototype-scoped reusable UI component with parameters |

### Pages

```java
@Page(value = "/post/{id}", layout = "base")
@Template("""
<article>
  <h1>{{ post.title }}</h1>
  <div>{{ raw post.body }}</div>
</article>
<form method="POST" action="?_method=DELETE">
  <button>Delete</button>
</form>
""")
public class PostViewPage {

    @Autowired private PostService posts;
    @Autowired private RequestContext ctx;

    private Post post;

    public void init() {
        post = posts.findById(ctx.path("id"));
    }

    @Delete
    public ActionResult destroy() {
        posts.delete(ctx.path("id"));
        return ActionResult.redirect("/posts");
    }
}
```

### Layouts

```java
@Layout
@Template("""
<!DOCTYPE html>
<html>
<head><title>My App</title></head>
<body>
  <nav>My App</nav>
  {{ content }}
  <footer>Powered by Candi</footer>
</body>
</html>
""")
public class BaseLayout {
}
```

### Widgets

```java
@Widget
@Template("""
<div class="alert alert-{{ type }}">{{ message }}</div>
""")
public class AlertWidget {
    private String type = "info";
    private String message;
}
```

Used in any page or layout: `{{ widget "alert" type="error" message="Oops" }}`

## Template Expressions

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
| `{{ fragment "name" }}...{{ end }}` | Named fragment (AJAX partial rendering) |
| `{{ widget "card" title="Hello" }}` | Render widget |
| `{{ content }}` | Layout content placeholder |

## AJAX Fragments

Render only a named section of a page — no JSON, no JS framework. Use it for search results, load-more, form submissions without full page reload.

```java
@Page("/posts")
@Template("""
<h1>Posts</h1>
{{ fragment "post-list" }}
<ul>
  {{ for post in posts }}
    <li>{{ post.title }}</li>
  {{ end }}
</ul>
{{ end }}
""")
public class PostsPage {
    @Autowired private PostService svc;
    private List<Post> posts;

    public void onGet() {
        posts = svc.findAll();
    }
}
```

**Normal request** — renders the full page (fragment content included inline).

**Fragment request** — returns only the fragment HTML, layout is skipped:

```
GET /posts
Candi-Fragment: post-list
```

Or via query parameter: `GET /posts?_fragment=post-list`

Pair with htmx or a simple `fetch()` call:

```html
<button hx-get="/posts?_fragment=post-list" hx-target="#results">
  Refresh
</button>
```

The server runs the full page lifecycle (`init()` → `onGet()`), then renders only the named fragment. Same data loading, smaller response.

## Quick Start

**Requirements:** Java 21+, Maven 3.9+

```xml
<dependency>
  <groupId>candi</groupId>
  <artifactId>candi-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>candi</groupId>
  <artifactId>candi-processor</artifactId>
  <version>0.1.0</version>
  <scope>provided</scope>
</dependency>
```

That's it. No plugin config. The annotation processor runs automatically during `javac`.

**Gradle:**

```groovy
implementation 'candi:candi-spring-boot-starter:0.1.0'
annotationProcessor 'candi:candi-processor:0.1.0'
```

Create `src/main/java/pages/HelloPage.java`:

```java
@Page("/hello")
@Template("""
<h1>Hello from Candi</h1>
""")
public class HelloPage {
}
```

```bash
mvn spring-boot:run
# Open http://localhost:8080/hello
```

## Benchmark: Candi vs React + NestJS

Same app. Same features. A blog with list, view, create, edit, delete.

| Metric | Candi | React + NestJS |
|--------|-------|----------------|
| **Source files** | 10 | 20 |
| **Lines of code** | 374 | 706 |
| **Tokens (cl100k)** | 3,573 | 5,097 |
| **Config files** | 1 (pom.xml) | 5 (2x package.json, 2x tsconfig, vite.config) |
| **Config tokens** | 549 | 1,131 |
| **Repos** | 1 | 2 (or monorepo) |
| **Build systems** | 1 (Maven) | 2 (Maven/Gradle + Vite) |
| **Runtime processes** | 1 (Spring Boot) | 2 (NestJS + Vite/Nginx) |
| **Languages** | 1 (Java) | 2 (TypeScript x2) |
| **API layer needed** | No | Yes (REST) |

<details>
<summary>Token breakdown</summary>

**Candi (aksra)**

| File | Tokens |
|------|--------|
| BaseLayout.java | 784 |
| BlogService.java | 512 |
| PostPage.java | 476 |
| EditPostPage.java | 419 |
| NewPostPage.java | 396 |
| IndexPage.java | 362 |
| BlogPost.java | 351 |
| AlertWidget.java | 189 |
| AksraApplication.java | 63 |
| application.properties | 21 |
| **Total** | **3,573** |

**React + NestJS**

| File | Tokens |
|------|--------|
| App.css | 636 |
| PostPage.tsx | 573 |
| IndexPage.tsx | 504 |
| blog.service.ts | 502 |
| EditPostPage.tsx | 478 |
| NewPostPage.tsx | 391 |
| api.ts | 355 |
| blog.controller.ts | 329 |
| package.json (fe) | 185 |
| package.json (be) | 174 |
| tsconfig.json (be) | 151 |
| tsconfig.json (fe) | 155 |
| App.tsx | 145 |
| Layout.tsx | 139 |
| index.html | 93 |
| main.tsx | 71 |
| vite.config.ts | 63 |
| main.ts | 65 |
| app.module.ts | 52 |
| blog-post.interface.ts | 36 |
| **Total** | **5,097** |

</details>

The Candi version has **30% fewer tokens**, half the files, one language, one process, and zero API boilerplate. The gap widens as the app grows — every new page in React+API needs a route, a component, an API endpoint, and a fetch call. In Candi, it's one file.

## Project Structure

```
candi/
├── candi-compiler/             Lexer, parser, code generator
├── candi-runtime/              CandiPage interface, handler mapping, request lifecycle
├── candi-processor/            JSR 269 annotation processor (generates _Candi subclasses)
├── candi-dev-tools/            FileWatcher, IncrementalCompiler, HotReloadManager
├── candi-maven-plugin/         Maven goals: candi:compile, candi:dev (backward compat)
├── candi-spring-boot-starter/  Auto-configuration, GraalVM hints
└── candi-demo/                 Integration test app
```

## Interceptors

Candi uses its own `CandiHandlerMapping`, which means standard `WebMvcConfigurer.addInterceptors()` does **not** apply to Candi page requests. To add interceptors that run on Candi pages, use the `CandiHandlerMapping` API:

```java
@Autowired
private CandiHandlerMapping candiHandlerMapping;

candiHandlerMapping.addCandiInterceptor(myInterceptor);
```

The Candi auth plugins register their interceptors this way automatically via auto-configuration.

## Dev Mode

Hot reload with live browser refresh. Edit, save, see changes instantly.

```bash
mvn candi:dev
```

File watcher detects changes, recompiles, hot-swaps class, pushes reload to browser. No server restart.

## Features

| Feature | Detail |
|---------|--------|
| Compile-time safety | Template errors caught at build, not runtime |
| Zero config | Annotation processor runs during javac — no plugin needed |
| Real Java | Fields, methods, annotations — full IDE support |
| Spring Boot native | Use any Spring service, repository, component via `@Autowired` |
| Layouts & widgets | `@Layout` + `{{ widget }}` for reuse |
| AJAX fragments | `{{ fragment "name" }}` for partial rendering — no JSON, no JS framework |
| Null-safe expressions | `?.` compiles to Java null checks |
| Hot reload | SSE-based live reload, no server restart |
| GraalVM compatible | Runtime hints included for native image |
| Build tool agnostic | Works with Maven, Gradle, Bazel — anything that runs javac |

## License

MIT
