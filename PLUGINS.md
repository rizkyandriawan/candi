# Candi Plugin Ecosystem

## Roundtable Discussion

**Date:** 2026-02-09
**Models:** GPT-5-mini, Gemini-3-Flash-Preview, Qwen3-32B
**Cycles:** 2
**Topic:** Top 20 highest-priority plugins for the Candi web framework ecosystem

### Cycle 1

#### GPT-5-mini

> I prioritized what a solo dev building a SaaS/MVP needs on day-1: auth, persistent data, forms/validation, CRUD/admin, uploads, email, payments, background jobs, realtime, and minimal ops. I biased toward things that (a) eliminate repetitive Java+template boilerplate, (b) fit naturally on Spring Boot, and (c) preserve Candi's one-file-per-page philosophy.

Initial ranking placed **AuthKit** at #1, **Forms & Validation** at #2, **Data (Spring Data + Auto-Binding)** at #3 — focusing on traditional developer needs.

#### Gemini-3-Flash-Preview

> gpt-5-mini is thinking like a traditional developer, not a Candi developer. If Candi's value proposition is "minimal tokens for LLM-assisted development," the priority shouldn't be "Auth" or "Forms" in the abstract. It should be **infrastructure that prevents context switching.**

Key critiques:
1. **Auth is ranked too high.** Spring Security already exists. A plugin that just wraps it is low innovation.
2. **Missing UI/CSS.** Writing 100 lines of Tailwind in `@Template` loses the token-saving battle.
3. **Missing the "S" in SaaS.** Solo devs need deployment and schema management within the Java file.

Gemini's top 5: **CandiUI** (#1), **AutoCRUD** (#2), **MagicForms** (#3), **TurboDrive/HTMX** (#4), **DirectSchema** (#5).

> Priority #1 (CandiUI): The LLM spends most of its tokens on HTML tags and CSS classes. If I can say `<c:table data="users" />`, I save 80% of the template tokens.

#### Qwen3-32B

> The Gemini list is a strong, pragmatic foundation. It centers on token efficiency, single-file philosophy, and production readiness for SaaS MVPs.

Agreements:
- **CandiUI (#1) is a game-changer** — without pre-built widgets, LLMs waste tokens generating HTML/CSS boilerplate.
- **AutoCRUD (#2) is critical** — CRUD apps require list/edit/view pages, and writing them manually loses Candi's "minimal code" edge.
- **DirectSchema (#5) is non-negotiable** — forcing developers to maintain SQL files is a severe context switch.

Cautions:
- **AuthGuard might be oversimplified** — `@Protected(role="ADMIN")` fails for dynamic permissions like "user owns this post."
- **HTMX risks complexity** — a simpler `@Ajax("updateCart")` might be more universally applicable.

### Cycle 2

#### GPT-5-mini (revised)

> Prioritize plugins that eliminate context-switching and repetitive HTML/asset/migration boilerplate while preserving Candi's one-file-per-page workflow. UI widgets, in-file assets, schema/migrations, auto CRUD/forms, and dev tooling come first — not a thin wrapper around Spring Security.

Major shift: **AuthKit dropped from #1 to #12.** Adopted **CandiUI** at #1, **DirectSchema** at #2, **In-File Assets** at #3.

#### Gemini-3-Flash-Preview (revised)

> gpt-5-mini is back-pedaling into a "Goldilocks" compromise that satisfies no one. Both of you are missing a massive technical debt trap: **Type Safety.**

Key addition: **TypeSafeRoutes** at #3 — generates `Routes.java` constants from `@Page` paths. `Routes.USER_PROFILE.url(id)` instead of `"/users/" + id`.

> If Candi is a Java framework, its greatest advantage over Rails or Node is that the compiler (and the LLM) knows exactly what the data is. Any plugin list that doesn't prioritize Type-Safe Routing and Refactor-safe Templates is building a house of cards.

Other notable positions:
- **SSE over WebSocket (#18)** — "WebSockets are overkill for 90% of SaaS apps. SSE is simpler to scale."
- **MailPreview (#12)** — local dev inbox for testing emails without external tools.
- **ExportKit (#19)** — one-click "Download as CSV/PDF" for any Table widget.
- **ActuatorUI (#20)** — minimal debug footer like Django Debug Toolbar.

#### Qwen3-32B (final synthesis)

> The debate between token efficiency, Java's type system, and MVP urgency is critical. Here's my synthesis.

Final consensus: **CandiUI** (#1), **SchemaSync** (#2), **TypeSafeRoutes** (#3), **AutoCRUD** (#4), **MagicForms** (#5).

---

## Top 20 Plugin Priorities

| # | Plugin | Description | Replaces | Token Savings |
|---|--------|-------------|----------|---------------|
| **1** | **CandiUI** | Pre-built `@Widget` components — Table, Modal, Form, Nav, Stats, Pagination. Themeable (Tailwind/DaisyUI). `<c:table data="users" />` instead of raw HTML/CSS. | 100+ lines of HTML/CSS per page | 2,000+ |
| **2** | **SchemaSync** | In-file DB migrations via annotations. Add `private String bio` to entity, plugin auto-generates Flyway/Liquibase migration. No more separate `.sql` files. | SQL migration files, context switching | 1,500 |
| **3** | **TypeSafeRoutes** | Auto-generates `Routes.java` constants from `@Page` paths. `Routes.POST_EDIT.url(id)` instead of `"/posts/edit/" + id`. Compile-time URL checks. | String-based URLs, LLM URL hallucination bugs | 1,200 |
| **4** | **AutoCRUD** | `@CRUD(entity=User.class)` auto-generates list/view/edit/delete pages with full wiring. | 4-5 repetitive page files per entity | 5,000+ |
| **5** | **MagicForms** | Auto-generates HTML forms from Bean Validation (`@NotNull`, `@Email`, `@Size`). CSRF, error messages, client-side validation included. | Manual `<form>` HTML, BindingResult plumbing, validation boilerplate | 1,500 |
| **6** | **AuthGuard** | `@Protected(role="ADMIN")`, `@OwnerOnly(field="author")`, `{{ user.name }}` template global. Simple session/JWT toggle. | Spring Security XML/Java config, login/logout controllers, guard checks | 1,200 |
| **7** | **TurboPartial** | Partial page updates aware of Candi lifecycle. Update just the `@Widget` without full reload. HTMX-compatible but Candi-native. | Manual JS fetch, DOM manipulation, full SPA frameworks | 1,000 |
| **8** | **CandiShip** | Stripe checkout widget + webhook annotations. `@Webhook("checkout.session.completed")` on a method. | Stripe SDK boilerplate, redirect logic, webhook routing | 3,000 |
| **9** | **InFileAssets** | Scoped CSS/JS blocks inside the Java file. Auto-bundled, deduped, cache-busted. | `src/main/resources/static/` management, separate asset pipeline | 500 |
| **10** | **CloudStore** | `@Upload` annotation on file fields. Auto multipart handling to S3/Minio. Signed URLs, image variants. | MultipartFile handling, S3 client boilerplate | 1,500 |
| **11** | **DataGlue** | Opinionated Spring Data JPA helpers. Auto-bind repos to page lifecycle, simple query generation, DTO-less data binding. | Repository/service/DTO boilerplate for common patterns | 1,000 |
| **12** | **MailPreview** | Templated emails using `{{ }}` syntax + local dev inbox for testing. No external mail tools needed during dev. | JavaMailSender config, Thymeleaf email templates, Mailtrap setup | 1,200 |
| **13** | **QueryBind** | Auto-map URL params (`?search=foo&sort=desc&page=2`) to JPA Specifications + Pageable. Works with CandiUI Table. | Manual Specification building, query param parsing, pagination logic | 1,000 |
| **14** | **ValidationSync** | Export Java JSR-303 annotations to client-side JS validation automatically. No double-coding validation rules. | Duplicate validation in JS and Java | 800 |
| **15** | **JobRunner** | `@Background` on any method. Virtual thread-backed by default, Redis queue optional. `@Scheduled` for cron. | TaskExecutor config, job scheduling infrastructure | 1,000 |
| **16** | **AutoAPI** | Turn any `@Page` data model into a JSON REST endpoint with `@Api`. Same file serves both HTML and JSON. | Separate `@RestController` classes, DTO duplication | 1,200 |
| **17** | **SEOGen** | Auto-generates Meta/OpenGraph/Sitemap from `@Page` title and data. Smart defaults, override when needed. | Manual `<head>` tag management per page | 600 |
| **18** | **SocialLink** | `@OAuth(provider="GOOGLE")` for social login. Built on Spring OAuth2 Client but zero-config. | OAuth2 client registration boilerplate, redirect handlers | 2,000 |
| **19** | **RealtimeLite** | SSE (not WebSocket) for live updates. `@Broadcast` to push data changes. Good for "job finished" notifications, live dashboards. | STOMP/SockJS boilerplate, WebSocket complexity | 1,000 |
| **20** | **DevToolbar** | Debug footer (like Django Debug Toolbar) — shows DB queries, render time, active widgets for current page. Dev-only. | External monitoring, manual logging, Actuator JSON parsing | 500 |

## Key Consensus Points

1. **CandiUI is unanimous #1** — all 3 models agree: LLMs waste most tokens on HTML/CSS. Pre-built widgets are the single biggest token saver.

2. **SchemaSync is the "Rails killer"** — the single-file philosophy breaks down the moment you open a `.sql` migration file. This plugin makes Candi compete with ActiveRecord.

3. **TypeSafeRoutes is Java-native innovation** — unique to compiled frameworks. LLMs hallucinate URLs; compile-time checks prevent entire categories of bugs.

4. **Auth should be simple, not first** — Spring Security already exists. The plugin should be a thin opinionated wrapper (`@Protected`), not a reinvention.

5. **SSE over WebSocket** — 90% of SaaS apps don't need bidirectional. SSE is simpler for LLMs to generate and easier to scale.

6. **AutoCRUD has the highest single-plugin token savings** (~5,000 tokens) since it eliminates entire pages.

## Build Order

### Phase 1 — Foundation
CandiUI, MagicForms, TypeSafeRoutes, AuthGuard

### Phase 2 — Productivity
AutoCRUD, SchemaSync, DataGlue, QueryBind, InFileAssets, TurboPartial

### Phase 3 — SaaS Features
CandiShip, CloudStore, MailPreview, JobRunner, AutoAPI, SEOGen, SocialLink, RealtimeLite, ValidationSync, DevToolbar
