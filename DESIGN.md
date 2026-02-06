# Candi Design Document

## Context

Modern Java web dev has no framework that combines HTML-first output, real backend power (DI, security, persistence), compile-time safety, and fast developer feedback. Candi fills this gap.

**Current status:** M1 (Compiler MVP) complete — 49 tests passing. M2 (Runtime Core) in progress — interfaces created, handler mapping and demo app pending.

This doc captures every architectural decision, the reasoning behind each, and serves as the single source of truth for the project.

---

## 1. What Candi Is

A Spring Boot–powered, HTML-first web framework where pages are dependency-injected components and rendering is the primary concern.

**One file = one routable page.** Header section declares backend wiring, body section declares HTML output. Compiled to Java at build time. No runtime template evaluation.

```
@page "/post/{id}"
@inject PostService posts
@inject RequestContext ctx

@init {
  post = posts.getById(ctx.path("id"));
}

<h1>{{ post.title }}</h1>
{{ if post.published }}
  <article>{{ post.content }}</article>
{{ end }}
```

**What Candi is NOT** (by design):
- Not a SPA framework (use HTMX for interactivity)
- Not a general-purpose template engine
- Not MVC — no controllers, no model maps
- Not API-first — use Spring `@RestController` for JSON APIs
- No arbitrary Java in templates — use `@init` for logic
- No nested layouts in v1
- No i18n built-in — inject a `Messages` service
- No client-side rendering

**Target use cases:** Blogs, CMS, documentation sites, marketing pages, content-heavy SaaS frontends, admin panels.

---

## 2. Framework Choice: Spring Boot

### Decision

Spring Boot for **all modes** — dev and production.

### Why not Quarkus + GraalVM?

The initial temptation: use Quarkus + GraalVM native image for production (low memory, fast startup) while developing on Spring Boot (rich DI, familiar).

**This doesn't work.** Candi is a *framework*. Users write their backend services using the framework's DI, persistence, and security stack:

```java
@Service                          // ← Spring annotation
public class PostService {
    @Autowired                    // ← Spring DI
    private PostRepository repo;  // ← Spring Data JPA

    public List<Post> findAll() {
        return repo.findAll();
    }
}
```

If dev runs on Spring but prod runs on Quarkus, the user's code must work on **both** frameworks. That means rewriting `@Service` → `@ApplicationScoped`, `@Autowired` → `@Inject`, Spring Data → Panache, Spring Security → Quarkus Security. This is not a "dev vs build" tool difference like `npm run dev` vs `npm run build` — a web framework **IS** the runtime. The user's code is structurally coupled to it.

### Why Spring Boot specifically?

| Factor | Reasoning |
|--------|-----------|
| **Ecosystem** | Largest Java web ecosystem. Spring Data, Spring Security, Spring Cache — users bring what they know. |
| **Audience** | Most Java developers already know Spring. Lower adoption barrier = faster adoption for Candi. |
| **Hot reload** | ClassLoader swapping works naturally on JVM. This is core to Candi's DX promise. |
| **Maturity** | Battle-tested for content-heavy web apps. Well-understood failure modes. |

### Addressing memory footprint

The main concern with Spring Boot is memory. Spring Boot 3.x provides three complementary strategies:

**1. Virtual Threads (Java 21)**
```properties
spring.threads.virtual.enabled=true
```
Platform threads use ~1MB stack each. Virtual threads use ~few KB. For content-heavy apps with many concurrent requests, this alone cuts memory significantly. Zero code changes.

**2. CDS (Class Data Sharing)**
Shares class metadata across JVM instances. Reduces memory 20-30% and improves startup. Spring Boot 3.x has first-class support via `spring-boot:build-image` with CDS training runs.

**3. GraalVM Native Image (optional production build)**
Spring Boot 3 officially supports AOT compilation to native image. Same Spring annotations, same user code, same generated page classes. Only the final compilation step differs.
- Memory: ~50-80MB (vs ~200-300MB standard JVM)
- Startup: <1 second (vs ~2-3 seconds)
- Single binary deployment

Candi's role: provide GraalVM reflection hints for generated page classes in the Starter (M7). User code needs no changes.

### Dev vs Production modes

| Mode | Runtime | Memory | Use case |
|------|---------|--------|----------|
| `candi:dev` | Spring Boot on JVM | Don't care | Hot reload, ClassLoader swap, in-process javac |
| `candi:build` (default) | Spring Boot on JVM | ~200-300MB | Zero config production |
| `candi:build` + vthreads | Spring Boot + Virtual Threads | ~100-150MB | One property change |
| `candi:build` + native | Spring Boot + GraalVM native | ~50-80MB | Needs GraalVM SDK |

**Key constraint:** Dev mode MUST run on JVM — ClassLoader tricks, in-process javac, reflection-based type checking all require it. Production can optionally go native.

---

## 3. Project Structure

```
candi/
├── pom.xml                          (parent POM, Java 21, Spring Boot 3.4.2)
├── candi-compiler/                  (lexer, parser, type resolver, codegen — zero Spring deps)
├── candi-runtime/                   (CandiPage interface, handler mapping, request lifecycle)
├── candi-maven-plugin/              (candi:compile, candi:dev goals)
├── candi-dev-tools/                 (file watcher, hot reload, live reload server)
└── candi-spring-boot-starter/       (auto-configuration, properties, GraalVM hints)
```

| Module | Depends On | Purpose |
|--------|-----------|---------|
| `candi-compiler` | nothing | Pure Java compiler. Lex → Parse → (Type-check) → Codegen. No Spring deps. |
| `candi-runtime` | Spring MVC | Base interfaces, handler mapping, page registry, request lifecycle |
| `candi-maven-plugin` | compiler | Maven goals. Invokes compiler at build time |
| `candi-dev-tools` | compiler + runtime | File watcher, incremental compile, classloader swap, WebSocket live reload |
| `candi-spring-boot-starter` | runtime + dev-tools (optional) | Auto-configuration, drop-in dependency, GraalVM reflection hints |

---

## 4. Page File Anatomy

```
@page "/post/{id}/edit"

@inject PostService posts
@inject RequestContext ctx
@inject Auth auth

@init {
  post = posts.getById(ctx.path("id"));
  canEdit = auth.isAdmin();
}

@action POST {
  posts.update(ctx.path("id"), ctx.form("title"));
  redirect("/posts");
}

@action DELETE {
  posts.delete(ctx.path("id"));
  redirect("/posts");
}

@fragment "post-content" {
  <article>{{ post.content }}</article>
}

<!DOCTYPE html>
<html>
  <body>
    <h1>{{ post.title }}</h1>
    {{ if post.published }}
      {{ fragment "post-content" }}
    {{ end }}
    {{ if canEdit }}
      <form method="POST">
        <input name="title" value="{{ post.title }}">
        <button>Save</button>
      </form>
    {{ end }}
  </body>
</html>
```

**Rule**: Header = backend (directives, init, actions). Body = rendering only. One-way data flow.

### Directives

| Directive | Purpose | Example |
|-----------|---------|---------|
| `@page` | Route path | `@page "/post/{id}"` |
| `@inject` | DI dependency | `@inject PostService posts` |
| `@init` | Request-time initialization | `@init { post = posts.getById(id); }` |
| `@action` | Non-GET mutation handler | `@action POST { ... redirect("/done"); }` |
| `@fragment` | Named partial for HTMX | `@fragment "content" { <p>...</p> }` |
| `@layout` | Layout inheritance (M4) | `@layout "base"` |
| `@slot` | Fill a layout slot (M4) | `@slot title { <title>Hi</title> }` |

### Template Expressions

| Syntax | Meaning | Generated Java |
|--------|---------|----------------|
| `{{ post.title }}` | Property access, auto-escaped | `out.appendEscaped(String.valueOf(this.post.getTitle()))` |
| `{{ raw post.html }}` | Unescaped output | `out.append(String.valueOf(this.post.getHtml()))` |
| `{{ post?.title }}` | Null-safe access | `(this.post == null ? null : this.post.getTitle())` |
| `{{ post.getTitle() }}` | Method call | `this.post.getTitle()` |
| `{{ if cond }}...{{ end }}` | Conditional | `if (cond) { ... }` |
| `{{ if a }}...{{ else if b }}...{{ else }}...{{ end }}` | Chained conditionals | Nested `if/else` |
| `{{ for item in list }}...{{ end }}` | Iteration | `for (var item : list) { ... }` |
| `{{ if a == "x" }}` | Equality | `Objects.equals(a, "x")` |
| `{{ if a && b }}` | Boolean logic | `(a && b)` |
| `{{ fragment "name" }}` | Render fragment inline | `renderFragment_name(out)` |

**Intentionally NOT supported** (keep logic in `@init`): arithmetic, ternary, instanceof, casting, constructors.

---

## 5. Compiler Pipeline

**4 stages (M1), 5 stages (M3+):**

```
.page.html → [Lexer] → Tokens → [Parser] → AST → [CodeGenerator] → Java Source → [javac] → .class
                                                 ↗
                                    [TypeResolver] (M3)
```

### Stage 1: Lexer (`candi.compiler.lexer.Lexer`)

State machine with two modes:
- **HEADER**: scans `@` directives and `{ }` code blocks. Ends when it sees `<` (HTML) or `{{` (body-only page).
- **BODY**: scans HTML text and `{{ }}` template expressions.

Key behaviors:
- `@init`/`@action` bodies: brace-matched opaque code blocks (Java code stored as raw strings)
- `@fragment` bodies: special brace-matching that treats `{{ }}` as template expressions (not brace depth changes)
- Body-only pages (no header): lexer transitions to BODY mode when `{{` is first non-whitespace

### Stage 2: Parser (`candi.compiler.parser.Parser`)

Recursive descent. Produces sealed AST:

```
PageNode (root)
├── InjectNode          @inject Type name
├── InitNode            @init { code }
├── ActionNode          @action METHOD { code }
├── FragmentDefNode     @fragment "name" { body }
├── LayoutDirectiveNode @layout "name"
├── SlotFillNode        @slot name { content }
└── BodyNode
    ├── HtmlNode
    ├── ExpressionOutputNode / RawExpressionOutputNode
    ├── IfNode (with optional elseBody)
    ├── ForNode
    ├── FragmentCallNode
    ├── ComponentCallNode
    └── SlotRenderNode
```

Key behavior: `else if` is desugared to nested `IfNode` in `else` body. The inner IfNode consumes the shared `{{ end }}` — outer IfNode must not also consume it.

### Stage 3: Expression Parser (`candi.compiler.expr.ExpressionParser`)

Recursive descent with precedence:
```
expression    = or_expr
or_expr       = and_expr ( "||" and_expr )*
and_expr      = equality ( "&&" equality )*
equality      = comparison ( ("==" | "!=") comparison )*
comparison    = unary ( ("<" | ">" | "<=" | ">=") unary )*
unary         = "!" unary | primary_chain
primary_chain = primary ( ("." | "?.") member )*
member        = IDENTIFIER ( "(" args? ")" )?
primary       = IDENTIFIER | STRING | NUMBER | BOOLEAN | "(" expression ")"
```

### Stage 4: Code Generator (`candi.compiler.codegen.CodeGenerator`)

Each `.page.html` → one Java class implementing `CandiPage`:

- `@Component` + `@Scope(SCOPE_REQUEST)` — request-scoped Spring bean
- `@CandiRoute(path, methods)` — route metadata for discovery
- `@Autowired` fields from `@inject` directives
- `private Object` fields from `@init` variable assignments
- `init()` — `@init` code with `this.` prefix on assignments
- `handleAction(String method)` — dispatches to `@action` blocks, handles `redirect()`
- `render(HtmlOutput out)` — compiled body HTML
- `renderFragment(String name, HtmlOutput out)` — fragment dispatch
- Private `renderFragment_xxx()` methods for each `@fragment`

Expression codegen rules:
- `==` / `!=` → `Objects.equals()` (safe for null)
- `post.title` → `post.getTitle()` (getter convention)
- `post?.title` → `(post == null ? null : post.getTitle())`
- Variables that are fields → `this.variable`

### Stage 5: Type Resolver (M3 — not yet implemented)

- Resolves `@inject` types via classpath reflection
- Infers `@init` variable types from RHS expression types
- Walks body AST, type-checks every expression
- Handles generics: `List<Post>` → `for item in posts` → `item` is `Post`
- Source-mapped errors: `file:line:col + message + snippet`

---

## 6. Runtime Architecture

### Core Interfaces (`candi-runtime`)

| Class | Purpose |
|-------|---------|
| `CandiPage` | Interface all generated pages implement: `init()`, `handleAction()`, `render()`, `renderFragment()` |
| `HtmlOutput` | StringBuilder wrapper with `append()` (raw) and `appendEscaped()` (XSS-safe) |
| `ActionResult` | Sealed interface: `Redirect(url)`, `Render()`, `MethodNotAllowed()` |
| `CandiRoute` | Annotation: `path` + `methods[]` — used by PageRegistry for route discovery |
| `RequestContext` | Request-scoped bean: `path()`, `query()`, `form()`, `header()` convenience methods |
| `FragmentNotFoundException` | Thrown when a fragment name isn't found on a page |

### Request Lifecycle

```
HTTP Request
  → CandiHandlerMapping: resolve route via PageRegistry (PathPattern matching)
  → CandiHandlerAdapter:
      1. Get request-scoped CandiPage bean from Spring DI
      2. page.init()  — run @init code
      3. Check HTTP method:
         GET/HEAD → skip to render
         POST/PUT/DELETE → page.handleAction(method)
           → Redirect(url) → HttpServletResponse.sendRedirect(), DONE
           → Render()      → fall through to render
           → MethodNotAllowed() → 405 response, DONE
      4. Check fragment request (HX-Fragment header or ?_fragment= param):
         Fragment → page.renderFragment(name, out) → write partial HTML
         Full page → page.render(out) → write full HTML
      5. Response: Content-Type text/html, write HtmlOutput
```

### PageRegistry

- On startup: scan ApplicationContext for all beans annotated with `@CandiRoute`
- Build path pattern → bean name index
- Use Spring's `PathPatternParser` for matching (supports `{id}`, `{*path}`, etc.)
- Thread-safe — supports hot reload (unregister old, register new)

### Layout System (M4)

Layout file (`base.layout.html`) → compiles to `CandiLayout`:
```java
public class Base__Layout implements CandiLayout {
    public void render(HtmlOutput out, SlotProvider slots) {
        out.append("<html><head><title>");
        slots.renderSlot("title", out);
        out.append("</title></head><body>");
        slots.renderSlot("content", out);
        out.append("</body></html>");
    }
}
```

Slots are **lambda callbacks** — no intermediate buffering, streaming-compatible.

### Component System (M4)

Component file (`card.component.html`) → prototype-scoped bean with `@param` inputs.
Called via `{{ component "card" title=post.title body=post.excerpt }}`.

---

## 7. Hot Reload (Dev Mode)

**Target: save → browser refresh < 500ms.**

```
File change detected (.page.html modified)
  → FileWatcher (Java WatchService, debounced 50ms)
  → IncrementalCompiler:
      1. Lex + Parse changed file only
      2. Type-check against existing classpath
      3. Generate Java source
      4. Compile Java → bytecode (javax.tools.JavaCompiler, in-process)
  → HotReloadManager:
      1. Create new PageClassLoader (child of app classloader)
      2. Load new page class bytecode
      3. Update PageRegistry (unregister old, register new)
      4. Register new Spring bean definition (request-scoped)
  → LiveReloadServer:
      1. Send WebSocket message → browser refreshes
```

**PageClassLoader** is parent-last for page classes only. Page classes implement `CandiPage` (from parent classloader), consume services (from parent classloader) — the boundary is clean. Old classloader gets GC'd.

**Error overlay**: Compilation errors sent via WebSocket → full-screen overlay in browser (file, line, snippet).

---

## 8. Build Milestones

| # | Milestone | Scope | Status |
|---|-----------|-------|--------|
| **M1** | **Compiler MVP** | Lexer, parser, expression parser, codegen. Generates compilable Java from pages. 49 tests. | **DONE** |
| **M2** | **Runtime Core** | CandiPage interface, PageRegistry, HandlerMapping/Adapter, RequestContext, Maven plugin `candi:compile`. Working demo app with MockMvc tests. | **In progress** |
| M3 | Type System | Type resolver via classpath reflection. `@init` type inference. Expression type checking. Source-mapped errors. | Not started |
| M4 | Layouts + Components | `@layout`/`@slot` inheritance. `@param` components. Discovery by convention. | Not started |
| M5 | Fragments / HTMX | `@fragment` blocks. Fragment request detection. Partial rendering via HX-Fragment header. | Not started |
| M6 | Hot Reload | FileWatcher, IncrementalCompiler, PageClassLoader, HotReloadManager, LiveReloadServer, error overlay. `candi:dev` goal. | Not started |
| M7 | Spring Boot Starter | Auto-configuration, properties, error pages, GraalVM reflection hints, starter archetype. | Not started |

---

## 9. M2 Implementation Plan

### Files to create/modify

**candi-runtime** (handler layer):
- `PageRegistry.java` — Discovers @CandiRoute beans, indexes routes, resolves requests to page beans
- `CandiHandlerMapping.java` — Spring HandlerMapping impl using PageRegistry
- `CandiHandlerAdapter.java` — Spring HandlerAdapter impl orchestrating init → action → render lifecycle
- `CandiAutoConfiguration.java` — Registers handler mapping + adapter + page registry as Spring beans

**candi-maven-plugin** (build tool):
- `CandiCompileMojo.java` — Scans `src/main/candi/` for `.page.html`, invokes compiler, writes to `target/generated-sources/candi`
- Activate module in parent POM

**candi-demo** (integration test app):
- New module: `candi-demo/`
- Spring Boot app with 3-4 hand-compiled pages (simulating what the Maven plugin would generate)
- MockMvc integration tests: GET renders HTML, POST redirects, 404 on unknown, fragment rendering

### Verification

```bash
# Unit: runtime compiles
mvn compile -pl candi-runtime

# Unit: Maven plugin compiles
mvn compile -pl candi-maven-plugin

# Integration: demo app boots and serves pages
mvn test -pl candi-demo
```

---

## 10. Risks & Mitigations

### High Risk

| Risk | Detail | Mitigation |
|------|--------|------------|
| **@init type inference** (M3) | Needs classpath reflection. Circular deps possible. | Two-pass: generate without type checking first, compile, then validate. Maven plugin runs at `generate-sources` after app code compiles. |
| **ClassLoader isolation** (M6) | New page classes must work with Spring DI. Bean proxies may cache stale class refs. | Use `CandiPageFactory` pattern — factory creates instances via current classloader. |

### Medium Risk

| Risk | Detail | Mitigation |
|------|--------|------------|
| **Expression lang expectations** | Users will expect Java expressions. No arithmetic, ternary, instanceof. | Clear error messages: "Compute values in @init block" — reinforces design principle. |
| **Generic type resolution** (M3) | `List<Post>` in `for item in posts` → `item` must be `Post`. | Use `ResolvableType` or `getGenericReturnType()`. |

### Low Risk

| Risk | Detail | Mitigation |
|------|--------|------------|
| Hot reload needs JDK | `javax.tools.JavaCompiler` requires JDK. | Standard for dev tools. Document requirement. |
| No streaming | Current design buffers full page. | v2 feature. StringBuilder is fast enough for content pages. |
| GraalVM native compat | Generated code uses reflection (type checking). | Reflection hints provided by Starter. AOT-friendly code generation. |
