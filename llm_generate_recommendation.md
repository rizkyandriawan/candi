# LLM Code Generation Guide for Candi Framework

Best practices and reference patterns for LLMs generating code in the Candi fullstack Java web framework. Candi runs on Spring Boot. Each page is one Java file containing both logic and its HTML template.

> **Cross-references:**
> - [Human-friendly documentation](https://candi.kakrizky.dev) — detailed setup, tutorials, and component guides
> - [README](./README.md) — high-level overview and quick start
> - [GitHub](https://github.com/rizkyandriawan/candi)

## Why Candi for AI Code Generation

Candi's single-file architecture makes it **ideal for LLM-assisted development** ("vibe coding"). Compared to typical React + NestJS setups:

- **30% fewer tokens** per app (3,573 vs 5,097 tokens for a blog app)
- **Half the files** — one file per page, no separate component/route/API/fetch
- **One language** — Java for everything (logic + template), no context-switching
- **Zero API boilerplate** — no REST endpoints, no fetch calls, no JSON serialization
- **Compile-time safety** — template errors caught at build, not at runtime

This means AI can generate **production-grade fullstack apps**, not just prototypes. Every new page is one file. Spring DI gives real architecture. The compile-time template engine catches errors before deployment.

---

## 1. Project Setup

### build.gradle

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.4.2'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.example'
version = '0.1.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    // Lombok (REQUIRED for @Getter on page/layout classes)
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    // Candi framework (REQUIRED)
    implementation 'dev.kakrizky.candi:candi-spring-boot-starter:0.1.0'
    annotationProcessor 'dev.kakrizky.candi:candi-processor:0.1.0'

    // Candi UI plugins (OPTIONAL - built-in widget libraries)
    implementation 'dev.kakrizky.candi:candi-ui-core:0.1.0'
    implementation 'dev.kakrizky.candi:candi-ui-forms:0.1.0'

    // Spring Boot
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Database (choose one)
    implementation 'org.xerial:sqlite-jdbc:3.45.1.0'
    implementation 'org.hibernate.orm:hibernate-community-dialects'
}
```

**Key points:**
- `candi-spring-boot-starter` provides the runtime (RequestContext, ActionResult, template engine, etc.). Published on Maven Central under `dev.kakrizky.candi`.
- `candi-processor` is an annotation processor -- it MUST be declared as `annotationProcessor`, not `implementation`. It generates the routing and template compilation at compile time.
- `candi-ui-core` and `candi-ui-forms` are optional widget plugins. Only add them if you need the built-in UI widgets. Also under `dev.kakrizky.candi`.

### application.properties

```properties
server.port=3001
spring.application.name=my-app

# Database (SQLite example)
spring.datasource.url=jdbc:sqlite:myapp.db
spring.datasource.driver-class-name=org.sqlite.JDBC
spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect
spring.jpa.hibernate.ddl-auto=update

# PostgreSQL example
# spring.datasource.url=jdbc:postgresql://localhost:5432/mydb
# spring.datasource.driver-class-name=org.postgresql.Driver
# spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Optional: seed data on startup
spring.jpa.defer-datasource-initialization=true
spring.sql.init.mode=always
spring.sql.init.data-locations=classpath:data.sql
```

### Application entry point

```java
package com.example.myapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}
```

---

## 2. Page Pattern

A page is one Java class annotated with `@Page` and `@Template`. The class fields are accessible in the template. The `init()` method runs on GET to load data.

### Basic page (GET only)

```java
package com.example.myapp.pages;

import candi.runtime.Page;
import candi.runtime.Template;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;

@Getter
@Page(value = "/dashboard", layout = "main")
@Template("""
<h1>Dashboard</h1>
<p>Total items: {{ itemCount }}</p>
<p>Total suppliers: {{ supplierCount }}</p>
""")
public class DashboardPage {

    @Autowired
    private ItemService itemService;

    @Autowired
    private SupplierService supplierService;

    private int itemCount;
    private int supplierCount;

    public void init() {
        itemCount = itemService.findAll().size();
        supplierCount = supplierService.findAll().size();
    }
}
```

**Rules:**
- `@Page(value = "/path")` defines the route.
- `@Page(value = "/path", layout = "main")` wraps this page's template inside the "main" layout.
- `@Template("""...""")` contains the HTML template as a Java text block.
- `init()` is called automatically on GET requests. Use it to populate fields.
- **All annotations are in `candi.runtime` package** (NOT `candi.annotation`).

### Field access — CRITICAL

The Candi annotation processor generates a `_Candi` subclass that accesses fields via **getter methods** (e.g., `this.getItemCount()`). Every field referenced in a template MUST have a corresponding getter.

**Recommended: Use Lombok `@Getter`** on the class to auto-generate getters for all fields:

```java
@Getter   // Generates getters for all fields
@Page(value = "/path", layout = "main")
@Template("""...""")
public class MyPage {
    private int count;     // → getCount() generated by Lombok
    private String name;   // → getName() generated by Lombok
}
```

**Alternative: Manual getters** (without Lombok):

```java
public class MyPage {
    private int count;
    public int getCount() { return count; }
}
```

**Boolean fields**: Use `Boolean` (wrapper), NOT `boolean` (primitive). Lombok generates `isXxx()` for primitive `boolean` but the processor expects `getXxx()`. `Boolean` wrapper generates the correct `getXxx()` getter.

```java
// CORRECT — Lombok generates getHasItems()
private Boolean hasItems;

// WRONG — Lombok generates isHasItems(), processor expects getHasItems()
private boolean hasItems;
```

**`@Autowired` fields**: Service/context fields annotated with `@Autowired` do NOT need getters (they are excluded by the processor).

- Getter methods defined manually are also accessible: a method `getTitle()` is accessible as `{{ title }}`.

### Page with POST (form submission)

```java
package com.example.myapp.pages;

import candi.runtime.Page;
import candi.runtime.Post;
import candi.runtime.Template;
import candi.runtime.ActionResult;
import candi.runtime.RequestContext;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.ArrayList;
import java.util.List;

@Page(value = "/items/create", layout = "main")
@Template("""
<h1>Create Item</h1>

{{ if errors.size > 0 }}
<div class="alert alert-danger">
    <ul>
    {{ for error in errors }}
        <li>{{ error }}</li>
    {{ end }}
    </ul>
</div>
{{ end }}

<form method="POST" action="/items/create">
    <div class="mb-3">
        <label for="name">Name</label>
        <input type="text" id="name" name="name" value="{{ name }}" required>
    </div>
    <div class="mb-3">
        <label for="status">Status</label>
        <select id="status" name="status">
            <option value="Active"{{ if status == "Active" }} selected{{ end }}>Active</option>
            <option value="Inactive"{{ if status == "Inactive" }} selected{{ end }}>Inactive</option>
        </select>
    </div>
    <button type="submit">Save</button>
    <a href="/items">Cancel</a>
</form>
""")
public class ItemCreatePage {

    @Autowired
    private RequestContext ctx;

    @Autowired
    private ItemService itemService;

    String name = "";
    String status = "Active";
    List<String> errors = new ArrayList<>();

    public void init() {
        // Called on GET. Nothing to load for a create form.
    }

    @Post
    public ActionResult handlePost() {
        // Read form data
        name = ctx.form("name") != null ? ctx.form("name").trim() : "";
        status = ctx.form("status") != null ? ctx.form("status").trim() : "Active";

        errors.clear();

        // Validate
        if (name.isEmpty()) {
            errors.add("Name is required.");
        }
        if (name.length() > 200) {
            errors.add("Name must be at most 200 characters.");
        }

        // On validation failure: re-render the form with errors
        if (!errors.isEmpty()) {
            return ActionResult.render();
        }

        // Create the entity
        Item item = new Item();
        item.setName(name);
        item.setStatus(status);
        itemService.create(item);

        // On success: redirect
        return ActionResult.redirect("/items");
    }
}
```

**Rules for @Post methods:**
- Annotate the method with `@Post`. The method name does not matter.
- The method MUST return `ActionResult`.
- `ActionResult.redirect("/path")` sends an HTTP redirect (POST-Redirect-GET pattern).
- `ActionResult.render()` re-renders the same page template (use this for validation errors so the user sees the form with their input preserved).
- Read form data with `ctx.form("fieldName")`. Always null-check.
- After reading form data into fields, those field values are automatically available in the re-rendered template.

### Page with DELETE (or other POST actions)

```java
@Page(value = "/items/{id}/delete", layout = "main")
@Template("""
<p>This action requires a POST request.</p>
""")
public class ItemDeletePage {

    @Autowired
    private RequestContext ctx;

    @Autowired
    private ItemService itemService;

    @Post
    public ActionResult handlePost() {
        String id = ctx.path("id");
        itemService.deleteById(id);
        return ActionResult.redirect("/items");
    }
}
```

**Note:** HTML forms only support GET and POST. For delete operations, use a POST form that submits to a `/delete` endpoint. There is no `@Delete` annotation; use `@Post` on a dedicated delete page/route.

---

## 3. Layout Pattern

A layout wraps page content. It uses `{{ content }}` as the insertion point for the page's rendered template.

### CRITICAL: Layouts are SINGLETONS

The generated `_Candi` layout class is registered as a Spring singleton (`@Component` without `@Scope`). This means:

- **Layouts are created once at application startup**, NOT per-request.
- **Layouts CANNOT use `@Autowired RequestContext`** — `RequestContext` is request-scoped and cannot be injected into a singleton bean. The application will fail to start with: `Scope 'request' is not active for the current thread`.
- **Layout fields are shared across all requests** — do NOT store request-specific data in layout fields.
- Layouts should contain only static HTML structure (header, sidebar, footer, etc.).

This is different from **page beans**, which ARE request-scoped (`@Scope(SCOPE_REQUEST)`) and CAN safely use `RequestContext`.

### Example: Correct layout (static HTML only)

```java
package com.example.myapp.layouts;

import candi.runtime.Layout;
import candi.runtime.Template;

@Layout
@Template("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>My Application</title>
    <link rel="stylesheet" href="/webjars/bootstrap/css/bootstrap.min.css">
    <link rel="stylesheet" href="/candi/cnd-ui.css">
    <link rel="stylesheet" href="/candi/cnd-forms.css">
</head>
<body>
    <nav class="navbar navbar-expand bg-body">
        <div class="container-fluid">
            <a href="/" class="navbar-brand">My App</a>
            <ul class="navbar-nav">
                <li class="nav-item">
                    <a href="/items" class="nav-link">Items</a>
                </li>
                <li class="nav-item">
                    <a href="/suppliers" class="nav-link">Suppliers</a>
                </li>
            </ul>
        </div>
    </nav>

    <main class="container mt-4">
        {{ content }}
    </main>

    <script src="/webjars/bootstrap/js/bootstrap.bundle.min.js"></script>
    <script src="/js/app.js"></script>
</body>
</html>
""")
public class MainLayout {
    // No fields, no RequestContext — layout is a singleton
}
```

### Sidebar active link highlighting (use JavaScript)

Since layouts can't access `RequestContext`, sidebar active state must be handled client-side:

```javascript
// In app.js — highlight current sidebar link
document.addEventListener('DOMContentLoaded', function() {
    var path = window.location.pathname;
    document.querySelectorAll('.sidebar-menu .nav-link, .navbar-nav .nav-link').forEach(function(link) {
        var href = link.getAttribute('href');
        if (!href) return;
        var isActive = (href === '/' && path === '/') ||
                       (href !== '/' && path.startsWith(href));
        if (isActive) link.classList.add('active');
    });
});
```

This pattern works because `window.location.pathname` gives the current URL on every page load.

### WRONG: Layout with RequestContext (will crash on startup)

```java
// DO NOT DO THIS — application will fail to start!
@Layout
@Template("""...""")
public class MainLayout {
    @Autowired
    private RequestContext ctx;  // FAILS: request scope not available in singleton

    private Boolean itemsActive;

    public void init() {
        itemsActive = ctx.uri().startsWith("/items");  // Never reached
    }
}
```

**Layout naming convention:**
- The layout name is derived from the class name by stripping the `Layout` suffix and lowercasing.
- `MainLayout` --> name is `"main"`
- `BaseLayout` --> name is `"base"`
- `AdminLayout` --> name is `"admin"`
- Pages reference layouts by this name: `@Page(value = "/path", layout = "main")`

**Rules:**
- `{{ content }}` is where the page's template is inserted. This placeholder is required.
- Layout fields work like page fields — accessible in the template — but remember the layout is a singleton so fields are shared.
- **DO NOT** inject `RequestContext` or any request-scoped beans into layouts.
- For per-request behavior (active menu highlighting), use client-side JavaScript.

### Named slots in layouts

Layouts can define named slots with default content using `{{ slot "name" }}`. Pages fill slots using `{{ block "name" }}`:

```java
@Layout
@Template("""
<html>
<body>
  <aside>
    {{ slot "sidebar" }}
      <p>Default sidebar content</p>
    {{ end }}
  </aside>
  <main>{{ content }}</main>
</body>
{{ stack "scripts" }}
</html>
""")
public class MainLayout {}
```

Page that provides content for the sidebar slot and pushes a script:

```java
@Page(value = "/dashboard", layout = "main")
@Template("""
<h1>Dashboard</h1>
{{ block "sidebar" }}
  <nav>Dashboard-specific sidebar</nav>
{{ end }}
{{ push "scripts" }}
  <script src="/js/dashboard.js"></script>
{{ end }}
""")
public class DashboardPage {}
```

If a page does NOT provide a `{{ block "sidebar" }}`, the slot renders its default content.

---

## 4. Widget Pattern

Widgets are reusable template fragments. They receive parameters from the calling template.

```java
package com.example.myapp.widgets;

import candi.runtime.Widget;
import candi.runtime.Template;

@Widget
@Template("""
<div class="card {{ class }}">
    {{ if title }}
    <div class="card-header">
        <h5 class="card-title mb-0">{{ title }}</h5>
    </div>
    {{ end }}
    <div class="card-body">
        {{ raw body }}
    </div>
    {{ if footer }}
    <div class="card-footer">
        {{ raw footer }}
    </div>
    {{ end }}
</div>
""")
public class InfoCardWidget {
    String title;
    String body;
    String footer;
    String class_ = "";  // "class" is reserved in Java; use class_ which maps to "class" in template
}
```

**Using widgets in templates:**

```html
{{ widget "infoCard" title="Order Summary" body="<p>3 items</p>" }}
```

**Widget naming convention:**
- The widget name is derived from the class name by stripping the `Widget` suffix and lowercasing the first letter.
- `InfoCardWidget` --> name is `"infoCard"`
- `StatusBadgeWidget` --> name is `"statusBadge"`
- `CndTableWidget` --> name is `"cnd-table"` (built-in plugins use kebab-case)

**Rules:**
- Widget fields become the widget's parameters.
- Parameters are passed as `key=value` pairs in the `{{ widget }}` call.
- Use `{{ raw fieldName }}` when the parameter contains HTML that should not be escaped.
- String values with spaces must be quoted: `title="My Title"`.
- You can pass field references without quotes: `data=myList`.

---

## 5. Template Syntax

Candi templates use `{{ }}` delimiters. The template engine compiles templates at build time via the annotation processor.

### Variable output

```html
<!-- HTML-escaped output (SAFE - use by default) -->
<p>{{ user.name }}</p>
<p>{{ title }}</p>

<!-- Unescaped raw output (use for trusted HTML content only) -->
<div>{{ raw htmlContent }}</div>
```

### Property access

```html
<!-- Calls getTitle() on the post object -->
<h1>{{ post.title }}</h1>

<!-- Calls getAuthor() then getName() -->
<p>{{ post.author.name }}</p>

<!-- Null-safe access: renders empty string if post or title is null -->
<h1>{{ post?.title }}</h1>

<!-- Null-safe chaining -->
<p>{{ post?.author?.name }}</p>
```

### Conditionals

```html
<!-- Simple if -->
{{ if isActive }}
    <span class="badge bg-success">Active</span>
{{ end }}

<!-- If/else -->
{{ if items.isEmpty }}
    <p>No items found.</p>
{{ else }}
    <p>Found {{ items.size }} items.</p>
{{ end }}

<!-- If/else-if/else -->
{{ if status == "Active" }}
    <span class="badge bg-success">Active</span>
{{ else if status == "Draft" }}
    <span class="badge bg-secondary">Draft</span>
{{ else }}
    <span class="badge bg-warning">Unknown</span>
{{ end }}
```

### Ternary operator

```html
<!-- Inline conditional expression -->
<span class="{{ active ? "text-success" : "text-muted" }}">{{ name }}</span>

<!-- Works with comparisons -->
<td class="{{ item.stock > 0 ? "in-stock" : "out-of-stock" }}">{{ item.stock }}</td>
```

### Null coalescing

```html
<!-- Provide a fallback when value is null -->
<p>Hello, {{ name ?? "Guest" }}</p>

<!-- Chain with property access -->
<p>{{ user?.displayName ?? user?.email ?? "Anonymous" }}</p>
```

### Loops

```html
<!-- Basic for-each loop -->
{{ for item in items }}
    <tr>
        <td>{{ item.name }}</td>
        <td>{{ item.status }}</td>
    </tr>
{{ end }}

<!-- Nested loops -->
{{ for order in orders }}
    <h3>{{ order.id }}</h3>
    {{ for line in order.lineItems }}
        <p>{{ line.itemName }}: {{ line.quantity }}</p>
    {{ end }}
{{ end }}
```

### Loop metadata

Every for loop automatically provides index/first/last metadata variables named `{loopVar}_index`, `{loopVar}_first`, `{loopVar}_last`:

```html
{{ for item in items }}
    {{ if item_first }}<ul>{{ end }}
    <li class="{{ item_index % 2 == 0 ? "even" : "odd" }}">
        #{{ item_index }}: {{ item.name }}
    </li>
    {{ if item_last }}</ul>{{ end }}
{{ end }}
```

| Variable | Type | Description |
|---|---|---|
| `{var}_index` | `int` | Current index (0-based) |
| `{var}_first` | `boolean` | `true` on first iteration |
| `{var}_last` | `boolean` | `true` on last iteration |

### Switch/case

```html
{{ switch role }}
{{ case "admin" }}
    <span class="badge bg-danger">Administrator</span>
{{ case "editor" }}
    <span class="badge bg-warning">Editor</span>
{{ default }}
    <span class="badge bg-secondary">User</span>
{{ end }}
```

Compiles to an if/else-if chain using `Objects.equals()`.

### Set variable

Assign a local variable within the template scope:

```html
{{ set greeting = "Hello, " ~ name }}
<p>{{ greeting }}</p>

{{ set fullName = first ~ " " ~ last }}
<h1>{{ fullName }}</h1>
```

### Arithmetic operators

```html
<p>Total: {{ price * quantity }}</p>
<p>Tax: {{ subtotal * 0.11 }}</p>
<p>Discount: {{ total - discount }}</p>
<p>Average: {{ sum / count }}</p>
<p>Remainder: {{ index % 2 }}</p>
```

Supported: `+`, `-`, `*`, `/`, `%`. Standard Java numeric semantics.

### String concatenation

Use the `~` operator (NOT `+` which is arithmetic):

```html
<p>{{ first ~ " " ~ last }}</p>
<p>{{ "Page " ~ currentPage ~ " of " ~ totalPages }}</p>
```

Compiles to `String.valueOf(left) + String.valueOf(right)`.

### Unary minus

```html
<p>{{ -amount }}</p>
<p>{{ -1 * balance }}</p>
```

### Filters (pipes)

Transform values through built-in filter functions:

```html
{{ name | upper }}
{{ name | lower }}
{{ name | capitalize }}
{{ name | trim }}
{{ bio | truncate(200) }}
{{ name | trim | upper }}
{{ items | join(", ") }}
{{ price | number("$#,##0.00") }}
{{ createdAt | date("yyyy-MM-dd") }}
{{ role | default("guest") }}
{{ text | replace("foo", "bar") }}
{{ content | escape }}
{{ items | length }}
```

Built-in filters: `upper`, `lower`, `capitalize`, `trim`, `length`, `escape`, `truncate(len)`, `replace(from, to)`, `date(format)`, `number(format)`, `join(separator)`, `default(fallback)`.

Filters can be chained: `{{ name | trim | upper | truncate(50) }}`

### Index/map access

```html
<!-- List access by index -->
<p>First item: {{ items[0] }}</p>
<p>{{ items[0].name }}</p>

<!-- Map access by key -->
<p>{{ config["apiUrl"] }}</p>
<p>{{ translations["greeting"] }}</p>
```

Compiles to `CandiRuntime.index(obj, key)` — works with `List`, `Map`, and arrays at runtime.

### Widget invocation

```html
<!-- Basic widget call -->
{{ widget "cnd-button" label="Save" type="submit" variant="primary" }}

<!-- Widget with field reference (no quotes = variable reference) -->
{{ widget "cnd-table" data=items columns=columnDefs }}

<!-- Widget with mixed literal and variable parameters -->
{{ widget "cnd-pagination" currentPage=pageResult.currentPage totalPages=pageResult.totalPages baseUrl="/items" }}
```

### AJAX fragments

```html
{{ fragment "post-list" }}
<ul>
    {{ for post in posts }}
    <li>{{ post.title }}</li>
    {{ end }}
</ul>
{{ end }}
```

Request with `Candi-Fragment: post-list` header or `?_fragment=post-list` query parameter returns only the fragment HTML.

### Layout content slot

```html
<!-- Only valid inside a @Layout template -->
{{ content }}
```

### Named slots

Layouts can define named slots with optional default content:

```html
<!-- In layout: define a slot with default content -->
{{ slot "sidebar" }}
    <p>Default sidebar</p>
{{ end }}

<!-- In page: provide content for a named slot -->
{{ block "sidebar" }}
    <nav>Custom sidebar for this page</nav>
{{ end }}
```

When a page provides a `{{ block "sidebar" }}`, it replaces the slot's default content. If no block is provided, the slot renders its default.

### Asset stacking

Push content from pages, render it in layouts. Useful for per-page scripts/styles:

```html
<!-- In layout: render all pushed content for this stack -->
{{ stack "scripts" }}

<!-- In page: push content to a named stack -->
{{ push "scripts" }}
    <script src="/js/dashboard.js"></script>
{{ end }}
```

Note: `{{ stack }}` must appear after `{{ content }}` in the layout so pushes happen before rendering.

### Comments

```html
{{-- This comment is completely stripped from output --}}
{{-- TODO: add pagination later --}}
```

Comments produce no HTML output at all. They are removed at the lexer level.

### Whitespace control

Trim surrounding whitespace with `{{-` and `-}}`:

```html
<p>
    {{- name -}}
</p>
<!-- Renders as: <p>John</p> (no extra whitespace) -->
```

`{{-` trims trailing whitespace from the preceding HTML. `-}}` trims leading whitespace from the following HTML. Can be used together or separately.

### Verbatim blocks

Output raw template syntax without parsing:

```html
{{ verbatim }}
<p>Use {{ variable }} to output values.</p>
<p>Use {{ if cond }}...{{ end }} for conditionals.</p>
{{ end }}
```

Everything inside `{{ verbatim }}...{{ end }}` is emitted as-is, including `{{ }}` delimiters. Useful for documentation or JavaScript templates.

### Summary of all syntax forms

| Syntax | Purpose |
|---|---|
| `{{ variable }}` | HTML-escaped output |
| `{{ raw variable }}` | Unescaped output |
| `{{ obj.property }}` | Property access (calls getter) |
| `{{ obj?.property }}` | Null-safe property access |
| `{{ a ? b : c }}` | Ternary operator |
| `{{ a ?? b }}` | Null coalescing (fallback if null) |
| `{{ a + b }}`, `{{ a * b }}` | Arithmetic (`+`, `-`, `*`, `/`, `%`) |
| `{{ a ~ b }}` | String concatenation |
| `{{ -expr }}` | Unary minus |
| `{{ expr \| filter }}` | Filter/pipe |
| `{{ expr \| filter(arg) }}` | Filter with arguments |
| `{{ list[0] }}`, `{{ map["k"] }}` | Index/map access |
| `{{ if cond }}...{{ end }}` | Conditional block |
| `{{ if a }}...{{ else }}...{{ end }}` | If/else |
| `{{ if a }}...{{ else if b }}...{{ end }}` | Else-if chain |
| `{{ for x in list }}...{{ end }}` | Loop (with `x_index`, `x_first`, `x_last`) |
| `{{ switch expr }}{{ case val }}...{{ end }}` | Switch/case |
| `{{ set x = expr }}` | Local variable assignment |
| `{{ widget "name" k=v }}` | Render a widget |
| `{{ fragment "name" }}...{{ end }}` | AJAX fragment |
| `{{ content }}` | Layout content insertion point |
| `{{ slot "name" }}default{{ end }}` | Named slot in layout |
| `{{ block "name" }}...{{ end }}` | Provide content for a slot (in page) |
| `{{ stack "name" }}` | Render stacked content (in layout) |
| `{{ push "name" }}...{{ end }}` | Push content to a stack (in page) |
| `{{-- comment --}}` | Template comment (stripped) |
| `{{- expr -}}` | Whitespace-trimmed output |
| `{{ verbatim }}...{{ end }}` | Raw block (mustache preserved) |

---

## 6. Built-in UI Widgets (candi-ui-core)

Requires `dev.kakrizky.candi:candi-ui-core` dependency. Add the CSS to your layout:

```html
<link rel="stylesheet" href="/candi/cnd-ui.css">
```

### cnd-table

Renders an HTML table from a list of data.

```html
{{ widget "cnd-table" data=items columns=columns striped=true hover=true class="mb-0" }}
```

Parameters:
- `data` (List) -- the list of objects to render as rows
- `columns` (List<Map> or column definition) -- column definitions with key, label, and optional formatter
- `striped` (boolean) -- alternating row colors
- `hover` (boolean) -- highlight row on hover
- `class` (String) -- additional CSS classes

### cnd-button

Renders a styled button or link.

```html
{{ widget "cnd-button" label="Create Item" href="/items/create" variant="primary" }}
{{ widget "cnd-button" label="Save" type="submit" variant="success" }}
{{ widget "cnd-button" label="Delete" variant="danger" size="sm" class="ms-2" }}
```

Parameters:
- `label` (String) -- button text
- `href` (String) -- if provided, renders as `<a>` link; otherwise renders as `<button>`
- `type` (String) -- button type attribute: `"submit"`, `"button"`, `"reset"`
- `variant` (String) -- Bootstrap color: `"primary"`, `"secondary"`, `"success"`, `"danger"`, `"warning"`, `"info"`, `"light"`, `"dark"`, `"outline-primary"`, etc.
- `size` (String) -- `"sm"`, `"lg"`, or omit for default
- `class` (String) -- additional CSS classes

### cnd-card

Renders a Bootstrap card.

```html
{{ widget "cnd-card" title="Order Details" body=orderHtml footer=footerHtml class="mb-4" }}
```

Parameters:
- `title` (String) -- card header text
- `body` (String) -- card body HTML (rendered raw)
- `footer` (String) -- card footer HTML (rendered raw)
- `class` (String) -- additional CSS classes

### cnd-nav

Renders a navigation bar.

```html
{{ widget "cnd-nav" brand="My App" brandHref="/" items=navItems class="mb-4" }}
```

Parameters:
- `brand` (String) -- brand/logo text
- `brandHref` (String) -- brand link URL
- `items` (List) -- navigation items
- `class` (String) -- additional CSS classes

### cnd-modal

Renders a Bootstrap modal dialog. Trigger it with `data-modal-toggle="modalId"` on a button.

```html
{{ widget "cnd-modal" id="confirmDelete" title="Confirm Delete" body="<p>Are you sure?</p>" size="sm" }}

<!-- Trigger button -->
<button data-modal-toggle="confirmDelete" class="btn btn-danger">Delete</button>
```

Parameters:
- `id` (String) -- unique modal ID (used by trigger)
- `title` (String) -- modal header title
- `body` (String) -- modal body HTML (rendered raw)
- `size` (String) -- `"sm"`, `"lg"`, `"xl"`, or omit for default

### cnd-alert

Renders a dismissible alert box.

```html
{{ widget "cnd-alert" message="Item saved successfully" type="success" dismissible=true }}
{{ widget "cnd-alert" message=errorMessage type="danger" class="mb-3" }}
```

Parameters:
- `message` (String) -- alert text
- `type` (String) -- `"success"`, `"danger"`, `"warning"`, `"info"`, `"primary"`, `"secondary"`
- `dismissible` (boolean) -- show close button
- `class` (String) -- additional CSS classes

### cnd-badge

Renders an inline badge/label.

```html
{{ widget "cnd-badge" label="Active" variant="success" }}
{{ widget "cnd-badge" label=item.status variant=statusVariant class="ms-1" }}
```

Parameters:
- `label` (String) -- badge text
- `variant` (String) -- Bootstrap color
- `class` (String) -- additional CSS classes

### cnd-pagination

Renders pagination controls.

```html
{{ widget "cnd-pagination" currentPage=pageResult.currentPage totalPages=pageResult.totalPages baseUrl="/items?q=search" paramName="page" }}
```

Parameters:
- `currentPage` (int) -- current active page (1-based)
- `totalPages` (int) -- total number of pages
- `baseUrl` (String) -- base URL for page links (param is appended)
- `paramName` (String) -- query parameter name for page number (default: `"page"`)

---

## 7. Forms (candi-ui-forms)

Requires `dev.kakrizky.candi:candi-ui-forms` dependency. Add the CSS to your layout:

```html
<link rel="stylesheet" href="/candi/cnd-forms.css">
```

### Model-driven form

Automatically generates form fields from a model object's fields and annotations.

```html
{{ widget "cnd-form" model=post action="/posts" method="POST" submitLabel="Save" }}
```

Parameters:
- `model` (Object) -- the model object whose fields generate form inputs
- `action` (String) -- form action URL
- `method` (String) -- HTTP method (`"POST"` or `"GET"`)
- `submitLabel` (String) -- submit button text

### Individual form widgets

For manual form construction:

#### cnd-input

```html
{{ widget "cnd-input" name="title" type="text" label="Title" value=post.title required=true error=titleError }}
{{ widget "cnd-input" name="email" type="email" label="Email" value="" required=true }}
{{ widget "cnd-input" name="price" type="number" label="Price" value=item.price }}
```

Parameters:
- `name` (String) -- field name attribute
- `type` (String) -- input type: `"text"`, `"email"`, `"number"`, `"password"`, `"date"`, `"datetime-local"`, `"hidden"`, etc.
- `label` (String) -- label text
- `value` (String) -- current value
- `required` (boolean) -- adds required attribute
- `error` (String) -- error message to display below the field

#### cnd-select

```html
{{ widget "cnd-select" name="uomId" label="UOM" options=uomIds optionLabels=uomLabels selected=selectedUomId required=true }}
```

Parameters:
- `name` (String) -- field name attribute
- `label` (String) -- label text
- `options` (List<String>) -- list of option values
- `optionLabels` (List<String>) -- list of display labels (must match options length)
- `selected` (String) -- currently selected value
- `required` (boolean) -- adds required attribute

### Form model annotations

Control how model fields are rendered in `cnd-form`:

```java
import candi.ui.forms.*;

@Entity
@Table(name = "posts")
public class Post {

    @Id
    @FormHidden          // Render as hidden input
    private String id;

    @FormLabel("Title")  // Custom label text
    @FormOrder(1)        // Field display order
    private String title;

    @FormTextarea        // Render as <textarea> instead of <input>
    @FormOrder(2)
    private String content;

    @FormSelect(options = {"Draft", "Published", "Archived"})  // Render as <select>
    @FormOrder(3)
    private String status;

    @FormGroup("Metadata")  // Group fields under a heading
    @FormOrder(10)
    private String author;

    @FormIgnore          // Exclude from form rendering
    private String createdAt;
}
```

| Annotation | Effect |
|---|---|
| `@FormHidden` | Renders as `<input type="hidden">` |
| `@FormTextarea` | Renders as `<textarea>` |
| `@FormSelect(options = {...})` | Renders as `<select>` with specified options |
| `@FormIgnore` | Field is excluded from the generated form |
| `@FormLabel("Label")` | Overrides the auto-generated label |
| `@FormOrder(n)` | Controls field display order (lower = first) |
| `@FormGroup("Name")` | Groups fields under a section heading |

---

## 8. Entity / Model (JPA)

Models use standard JPA annotations. Spring's naming strategy auto-converts camelCase field names to snake_case column names.

### Basic entity

```java
package com.example.myapp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "items")
public class Item {

    @Id
    private String id;
    private String sku;
    private String name;
    private String status;       // maps to column "status"
    private String uomId;        // maps to column "uom_id" (auto snake_case)
    private String createdAt;    // maps to column "created_at"
    private String createdBy;    // maps to column "created_by"
    private String updatedAt;
    private String updatedBy;

    public Item() {}

    // Getters and setters for all fields
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getUomId() { return uomId; }
    public void setUomId(String uomId) { this.uomId = uomId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
```

**Rules:**
- Always provide a no-arg constructor.
- Always provide getters and setters for all fields.
- `@Table(name = "items")` -- table name should be plural snake_case.
- Field names use camelCase. Spring auto-converts to snake_case for the database column.
- Use `String` for IDs (UUIDs). Use `String` for timestamps (ISO-8601 format). This avoids database-specific date type issues.

### Composite primary key

Use `@IdClass` for tables with composite primary keys.

```java
// The ID class (must implement Serializable, have equals/hashCode)
package com.example.myapp.model;

import java.io.Serializable;
import java.util.Objects;

public class UserRoleId implements Serializable {
    private String userId;
    private String roleId;

    public UserRoleId() {}

    public UserRoleId(String userId, String roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserRoleId that = (UserRoleId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}

// The entity
@Entity
@Table(name = "user_roles")
@IdClass(UserRoleId.class)
public class UserRole {

    @Id
    private String userId;
    @Id
    private String roleId;
    private String assignedAt;
    private String assignedBy;

    public UserRole() {}

    // Getters and setters...
}
```

### Collections with @ElementCollection

For storing a list of simple values in a separate table:

```java
@Entity
@Table(name = "products")
public class Product {

    @Id
    private String id;
    private String name;

    @ElementCollection
    @CollectionTable(name = "product_tags", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "tag")
    private List<String> tags = new ArrayList<>();

    // Getters and setters...
}
```

---

## 9. Repository + Service

### Repository

Repositories are interfaces that extend `JpaRepository`. Spring Data JPA auto-implements them.

```java
package com.example.myapp.repository;

import com.example.myapp.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ItemRepository extends JpaRepository<Item, String> {

    // Derived query methods (Spring generates SQL from the method name)
    List<Item> findByStatus(String status);
    List<Item> findByNameContainingIgnoreCase(String name);
    List<Item> findAllByOrderByUpdatedAtDesc();
    int countBySkuIgnoreCaseAndIdNot(String sku, String excludeId);

    // Custom JPQL query
    @Query("SELECT i FROM Item i WHERE i.status = 'Active' ORDER BY i.name")
    List<Item> findActiveItemsSorted();
}
```

**Common derived query method patterns:**

| Method name | Generated SQL (conceptual) |
|---|---|
| `findByStatus(String s)` | `WHERE status = ?` |
| `findByNameContainingIgnoreCase(String n)` | `WHERE LOWER(name) LIKE LOWER('%?%')` |
| `findByStatusAndNameContaining(String s, String n)` | `WHERE status = ? AND name LIKE '%?%'` |
| `findAllByOrderByCreatedAtDesc()` | `ORDER BY created_at DESC` |
| `countByStatusAndIdNot(String s, String id)` | `SELECT COUNT(*) WHERE status = ? AND id != ?` |
| `existsByCode(String c)` | `SELECT EXISTS(... WHERE code = ?)` |
| `deleteByStatus(String s)` | `DELETE WHERE status = ?` |

### Service

Services wrap repositories with business logic.

```java
package com.example.myapp.service;

import com.example.myapp.model.Item;
import com.example.myapp.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ItemService {

    @Autowired
    private ItemRepository repo;

    public List<Item> findAll() {
        return repo.findAll();
    }

    public Item findById(String id) {
        return repo.findById(id).orElse(null);
    }

    public List<Item> findActive() {
        return repo.findByStatus("Active");
    }

    public Item create(Item item) {
        item.setId(UUID.randomUUID().toString());
        String now = Instant.now().toString();
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return repo.save(item);
    }

    public Item update(Item item) {
        item.setUpdatedAt(Instant.now().toString());
        return repo.save(item);
    }

    public void deleteById(String id) {
        repo.deleteById(id);
    }

    public boolean isSkuUnique(String sku, String excludeId) {
        return repo.countBySkuIgnoreCaseAndIdNot(sku,
                excludeId != null ? excludeId : "") == 0;
    }
}
```

**Rules:**
- Annotate with `@Service`.
- Always generate a UUID for new entity IDs.
- Always set `createdAt`/`updatedAt` timestamps in ISO-8601 format.
- Return `null` from `findById` when the entity does not exist (use `.orElse(null)`).
- Services are injected into pages with `@Autowired`.

---

## 10. Routing & Request

### RequestContext

`RequestContext` provides access to all parts of the HTTP request. Inject it with `@Autowired`.

```java
@Autowired
private RequestContext ctx;
```

#### Path parameters

Defined in `@Page` with `{paramName}` syntax, accessed via `ctx.path("paramName")`.

```java
@Page(value = "/items/{id}", layout = "main")
@Template("""
<h1>Item: {{ item.name }}</h1>
""")
public class ItemViewPage {

    @Autowired
    private RequestContext ctx;

    @Autowired
    private ItemService itemService;

    Item item;

    public void init() {
        String id = ctx.path("id");    // e.g., "abc-123"
        item = itemService.findById(id);
    }
}
```

#### Query parameters

Accessed via `ctx.query("paramName")`. Returns `null` if not present.

```java
// URL: /items?q=widget&page=2&status=Active
public void init() {
    String searchTerm = ctx.query("q");       // "widget"
    String pageParam = ctx.query("page");     // "2"
    String status = ctx.query("status");      // "Active"
}
```

#### Form data

Accessed via `ctx.form("fieldName")` inside `@Post` methods. Returns `null` if not present.

```java
@Post
public ActionResult handlePost() {
    String name = ctx.form("name");
    String email = ctx.form("email");
    String status = ctx.form("status");
    // ...
}
```

#### Current URI

```java
String currentPath = ctx.uri();  // e.g., "/items/create"
```

### ActionResult

Returned from `@Post` methods to control what happens after form submission.

```java
// Redirect to another URL (HTTP 302)
return ActionResult.redirect("/items");

// Redirect with query params for toast messages
return ActionResult.redirect("/items?toast=success&msg=Item+created");

// Re-render the current page (preserves field values for showing validation errors)
return ActionResult.render();
```

---

## 11. Naming Conventions

### File and class naming

| Type | Class name | File name | Derived name/route |
|---|---|---|---|
| Page | `ItemListPage` | `ItemListPage.java` | Route from `@Page(value = "/items")` |
| Page | `ItemCreatePage` | `ItemCreatePage.java` | Route from `@Page(value = "/items/create")` |
| Page | `ItemViewPage` | `ItemViewPage.java` | Route from `@Page(value = "/items/{id}")` |
| Page | `ItemDeletePage` | `ItemDeletePage.java` | Route from `@Page(value = "/items/{id}/delete")` |
| Layout | `MainLayout` | `MainLayout.java` | Name: `"main"` |
| Layout | `AdminLayout` | `AdminLayout.java` | Name: `"admin"` |
| Widget | `StatusBadgeWidget` | `StatusBadgeWidget.java` | Name: `"statusBadge"` |
| Model | `Item` | `Item.java` | Table: `items` (plural snake_case) |
| Model | `PurchaseOrder` | `PurchaseOrder.java` | Table: `purchase_orders` |
| Model | `PoLineItem` | `PoLineItem.java` | Table: `po_line_items` |
| Repository | `ItemRepository` | `ItemRepository.java` | -- |
| Service | `ItemService` | `ItemService.java` | -- |

### Package structure

```
src/main/java/com/example/myapp/
    MyApplication.java
    layouts/
        MainLayout.java
    model/
        Item.java
        Supplier.java
        PurchaseOrder.java
    pages/
        dashboard/
            DashboardPage.java
        item/
            ItemListPage.java
            ItemCreatePage.java
            ItemViewPage.java
            ItemDeletePage.java
        supplier/
            SupplierListPage.java
            SupplierCreatePage.java
            SupplierViewPage.java
    repository/
        ItemRepository.java
        SupplierRepository.java
    service/
        ItemService.java
        SupplierService.java
    widgets/
        StatusBadgeWidget.java
```

### Naming rules summary

- **Models**: singular PascalCase (`Item`, `User`, `PurchaseOrder`)
- **Tables**: plural snake_case (`items`, `users`, `purchase_orders`)
- **Pages**: `<Entity><Action>Page` (`ItemListPage`, `ItemCreatePage`, `ItemViewPage`)
- **Layouts**: `<Name>Layout` (`MainLayout`, `AdminLayout`)
- **Widgets**: `<Name>Widget` (`StatusBadgeWidget`, `InfoCardWidget`)
- **Services**: `<Entity>Service` (`ItemService`, `UserService`)
- **Repositories**: `<Entity>Repository` (`ItemRepository`, `UserRepository`)
- **ID classes**: `<Entity>Id` (`UserRoleId`)
- **Fields/columns**: camelCase in Java (`uomId`), auto-mapped to snake_case in DB (`uom_id`)

---

## 12. Common Patterns

### Pattern A: List page with search + pagination

Use an inner POJO with `@Getter @AllArgsConstructor` for pre-computed row data, then iterate with `{{ for row in rows }}` in the template. Keep pagination as `{{ raw }}` since it has complex conditional logic.

For fields that contain trusted HTML (badge classes, pre-built HTML snippets), use `{{ raw row.fieldName }}` in the template.

```java
package com.example.myapp.pages.item;

import candi.runtime.Page;
import candi.runtime.Template;
import candi.runtime.RequestContext;
import lombok.Getter;
import lombok.AllArgsConstructor;
import com.example.myapp.model.Item;
import com.example.myapp.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;
import java.util.stream.Collectors;

@Getter
@Page(value = "/items", layout = "main")
@Template("""
<div class="d-flex justify-content-between align-items-center mb-4">
    <h1 class="h3 mb-0">Items</h1>
    <a href="/items/create" class="btn btn-primary">+ Create Item</a>
</div>

<div class="card">
    <div class="card-header">
        <form method="GET" action="/items" class="d-flex gap-2">
            <input type="text" name="q" class="form-control" style="max-width:400px"
                   placeholder="Search by name or SKU..." value="{{ searchTerm }}">
            <button type="submit" class="btn btn-secondary">Search</button>
        </form>
    </div>

    {{ if hasNoItems }}
    <div class="card-body text-center py-5">
        <p class="text-muted">No items found.</p>
    </div>
    {{ else }}
    <div class="card-body p-0">
        <div class="table-responsive">
            <table class="table table-striped table-hover mb-0">
                <thead>
                    <tr>
                        <th>SKU</th>
                        <th>Name</th>
                        <th>Status</th>
                        <th>Updated</th>
                        <th>Action</th>
                    </tr>
                </thead>
                <tbody>
                    {{ for row in rows }}
                    <tr>
                        <td class="font-monospace"><a href="/items/{{ row.id }}">{{ row.sku }}</a></td>
                        <td>{{ row.name }}</td>
                        <td><span class="badge {{ raw row.statusBadgeClass }}">{{ row.status }}</span></td>
                        <td>{{ row.updatedAt }}</td>
                        <td><a href="/items/{{ row.id }}" class="btn btn-secondary btn-sm">View</a></td>
                    </tr>
                    {{ end }}
                </tbody>
            </table>
        </div>
    </div>

    <div class="card-footer d-flex justify-content-between align-items-center">
        <span class="text-muted small">
            Showing {{ startItem }}-{{ endItem }} of {{ totalItems }} records
        </span>
        {{ raw paginationHtml }}
    </div>
    {{ end }}
</div>
""")
public class ItemListPage {

    @Autowired
    private RequestContext ctx;

    @Autowired
    private ItemService itemService;

    // Inner POJO for table rows — pre-computed display values
    @Getter
    @AllArgsConstructor
    public static class ItemRow {
        private final String id;
        private final String sku;
        private final String name;
        private final String status;
        private final String statusBadgeClass;  // "bg-success" or "bg-secondary"
        private final String updatedAt;
    }

    // Template fields
    private String searchTerm = "";
    private int totalItems = 0;
    private int startItem = 0;
    private int endItem = 0;
    private Boolean hasNoItems = true;
    private List<ItemRow> rows = new ArrayList<>();
    private String paginationHtml = "";

    private static final int PAGE_SIZE = 10;

    public void init() {
        searchTerm = ctx.query("q") != null ? ctx.query("q") : "";

        int currentPage = 1;
        String pageParam = ctx.query("page");
        if (pageParam != null && !pageParam.isEmpty()) {
            try { currentPage = Integer.parseInt(pageParam); }
            catch (NumberFormatException ignored) {}
        }
        if (currentPage < 1) currentPage = 1;

        // Fetch and filter
        List<Item> allItems = itemService.findAll();
        List<Item> filtered = allItems;
        if (!searchTerm.isEmpty()) {
            String lower = searchTerm.toLowerCase();
            filtered = allItems.stream()
                .filter(i -> (i.getName() != null && i.getName().toLowerCase().contains(lower))
                          || (i.getSku() != null && i.getSku().toLowerCase().contains(lower)))
                .collect(Collectors.toList());
        }

        // Pagination math
        totalItems = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        if (currentPage > totalPages) currentPage = totalPages;

        int fromIndex = (currentPage - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, totalItems);
        List<Item> pageItems = (fromIndex < totalItems)
            ? filtered.subList(fromIndex, toIndex) : List.of();

        startItem = totalItems > 0 ? fromIndex + 1 : 0;
        endItem = toIndex;
        hasNoItems = pageItems.isEmpty();

        // Build row POJOs with pre-computed display values
        rows = new ArrayList<>();
        for (Item item : pageItems) {
            rows.add(new ItemRow(
                item.getId(),
                item.getSku(),
                item.getName(),
                item.getStatus(),
                "Active".equals(item.getStatus()) ? "bg-success" : "bg-secondary",
                formatDateTime(item.getUpdatedAt())
            ));
        }

        // Pagination stays as pre-built HTML (complex conditional logic)
        String baseUrl = "/items?q=" + urlEncode(searchTerm);
        paginationHtml = buildPaginationHtml(currentPage, totalPages, baseUrl);
    }

    private String buildPaginationHtml(int current, int total, String baseUrl) {
        if (total <= 1) return "";
        StringBuilder sb = new StringBuilder();
        sb.append("<nav><ul class=\"pagination mb-0\">");
        if (current > 1) {
            sb.append("<li class=\"page-item\"><a class=\"page-link\" href=\"")
              .append(baseUrl).append("&page=").append(current - 1).append("\">Previous</a></li>");
        }
        for (int p = 1; p <= total; p++) {
            if (p == current) {
                sb.append("<li class=\"page-item active\"><span class=\"page-link\">").append(p).append("</span></li>");
            } else {
                sb.append("<li class=\"page-item\"><a class=\"page-link\" href=\"")
                  .append(baseUrl).append("&page=").append(p).append("\">").append(p).append("</a></li>");
            }
        }
        if (current < total) {
            sb.append("<li class=\"page-item\"><a class=\"page-link\" href=\"")
              .append(baseUrl).append("&page=").append(current + 1).append("\">Next</a></li>");
        }
        sb.append("</ul></nav>");
        return sb.toString();
    }

    private String formatDateTime(String iso) {
        if (iso == null) return "-";
        return iso.substring(0, Math.min(16, iso.length())).replace("T", " ");
    }

    private String urlEncode(String value) {
        if (value == null) return "";
        try { return java.net.URLEncoder.encode(value, "UTF-8"); }
        catch (Exception e) { return value; }
    }
}
```

**Key points for list pages:**
- Define a `static inner class` with `@Getter @AllArgsConstructor` for row data.
- Pre-compute all display values (badge classes, formatted dates, cross-model lookups) in the POJO constructor.
- `{{ variable }}` auto-escapes HTML, so no manual `escapeHtml()` needed for text fields.
- Use `{{ raw row.field }}` only for trusted values like CSS class names or pre-built HTML snippets.
- Keep pagination as `{{ raw paginationHtml }}` — complex conditional logic is better built in Java.

### Pattern B: Create page with form + validation

Use `SelectOption` for dropdowns, `CheckboxOption` for checkbox lists, and `List<String>` for error messages. These are shared POJOs in `candi.runtime` with proper getters.

```java
package com.example.myapp.pages.item;

import candi.runtime.Page;
import candi.runtime.Post;
import candi.runtime.Template;
import candi.runtime.ActionResult;
import candi.runtime.RequestContext;
import candi.runtime.SelectOption;
import lombok.Getter;
import com.example.myapp.model.Item;
import com.example.myapp.model.Uom;
import com.example.myapp.service.ItemService;
import com.example.myapp.service.UomService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.*;

@Getter
@Page(value = "/items/create", layout = "main")
@Template("""
<div class="d-flex justify-content-between align-items-center mb-4">
    <h1 class="h3 mb-0">Create Item</h1>
    <a href="/items" class="btn btn-outline-secondary">Back to List</a>
</div>

<div class="card" style="max-width:600px;">
    <div class="card-body">

        {{ if hasErrors }}
        <div class="alert alert-danger">
            <ul style="margin:0; padding-left:20px;">
                {{ for error in errors }}
                <li>{{ error }}</li>
                {{ end }}
            </ul>
        </div>
        {{ end }}

        <form method="POST" action="/items/create">
            <div class="mb-3">
                <label class="form-label required" for="name">Name</label>
                <input type="text" id="name" name="name" class="form-control"
                       value="{{ name }}" maxlength="200" required placeholder="Enter item name">
            </div>

            <div class="mb-3">
                <label class="form-label required" for="uomId">UOM</label>
                <select id="uomId" name="uomId" class="form-select" required>
                    <option value="">Select UOM...</option>
                    {{ for opt in uomOptions }}
                    {{ if opt.selected }}<option value="{{ opt.value }}" selected>{{ opt.label }}</option>
                    {{ else }}<option value="{{ opt.value }}">{{ opt.label }}</option>{{ end }}
                    {{ end }}
                </select>
            </div>

            <div class="mb-3">
                <label class="form-label" for="status">Status</label>
                <select id="status" name="status" class="form-select">
                    {{ if statusIsActive }}
                        <option value="Active" selected>Active</option>
                        <option value="Inactive">Inactive</option>
                    {{ else }}
                        <option value="Active">Active</option>
                        <option value="Inactive" selected>Inactive</option>
                    {{ end }}
                </select>
            </div>

            <div class="d-flex gap-2 mt-3 pt-3 border-top">
                <button type="submit" class="btn btn-primary">Save</button>
                <a href="/items" class="btn btn-secondary">Cancel</a>
            </div>
        </form>

    </div>
</div>
""")
public class ItemCreatePage {

    @Autowired private RequestContext ctx;
    @Autowired private ItemService itemService;
    @Autowired private UomService uomService;

    // Form fields
    private String name = "";
    private String uomId = "";
    private String status = "Active";
    private Boolean statusIsActive = true;
    private List<SelectOption> uomOptions = new ArrayList<>();
    private List<String> errors = new ArrayList<>();
    private Boolean hasErrors;

    public void init() {
        buildUomOptions();
    }

    @Post
    public ActionResult handlePost() {
        name = ctx.form("name") != null ? ctx.form("name").trim() : "";
        uomId = ctx.form("uomId") != null ? ctx.form("uomId").trim() : "";
        status = ctx.form("status") != null ? ctx.form("status").trim() : "Active";
        statusIsActive = "Active".equals(status);

        errors.clear();

        if (name.isEmpty()) {
            errors.add("Name is required.");
        } else if (name.length() > 200) {
            errors.add("Name must be at most 200 characters.");
        }
        if (uomId.isEmpty()) {
            errors.add("UOM is required.");
        }

        hasErrors = !errors.isEmpty();
        buildUomOptions();  // Re-build for re-render

        if (hasErrors) {
            return ActionResult.render();
        }

        Item item = new Item();
        item.setName(name);
        item.setUomId(uomId);
        item.setStatus(status);
        item.setCreatedBy("user-001");
        item.setUpdatedBy("user-001");
        itemService.create(item);

        return ActionResult.redirect("/items?toast=success&msg=Item+created+successfully");
    }

    private void buildUomOptions() {
        uomOptions = new ArrayList<>();
        for (Uom uom : uomService.findActive()) {
            uomOptions.add(new SelectOption(
                uom.getId(),
                uom.getCode() + " - " + uom.getName(),
                uom.getId().equals(uomId)
            ));
        }
    }
}
```

**Key points for create/edit pages:**
- Use `SelectOption` for `<select>` dropdowns — template iterates with `{{ for opt in options }}`.
- Use `CheckboxOption` for checkbox lists — template iterates with `{{ for cb in checkboxes }}`.
- Re-build option lists in the `@Post` method before returning `ActionResult.render()` so selected/checked state is preserved.
- Error lists use `List<String>` with `{{ for error in errors }}<li>{{ error }}</li>{{ end }}`.

### Pattern C: View/detail page

```java
package com.example.myapp.pages.item;

import candi.runtime.Page;
import candi.runtime.Template;
import candi.runtime.RequestContext;
import com.example.myapp.model.Item;
import com.example.myapp.model.Uom;
import com.example.myapp.service.ItemService;
import com.example.myapp.service.UomService;
import org.springframework.beans.factory.annotation.Autowired;

@Page(value = "/items/{id}", layout = "main")
@Template("""
<div class="d-flex justify-content-between align-items-center mb-4">
    <h1 class="h3 mb-0">Item Detail</h1>
    <a href="/items" class="btn btn-secondary">Back to List</a>
</div>

{{ if item == null }}
<div class="card">
    <div class="card-body text-center py-5">
        <p class="h5 text-muted">Item not found</p>
        <p class="text-muted small">The requested item does not exist.</p>
    </div>
</div>
{{ else }}
<div class="card" style="max-width:700px;">
    <div class="card-body">
        <div class="row g-3">
            <div class="col-md-6">
                <div class="text-muted small">SKU</div>
                <div class="fw-medium font-monospace">{{ item.sku }}</div>
            </div>
            <div class="col-md-6">
                <div class="text-muted small">Name</div>
                <div class="fw-medium">{{ item.name }}</div>
            </div>
            <div class="col-md-6">
                <div class="text-muted small">UOM</div>
                <div class="fw-medium">{{ uomDisplay }}</div>
            </div>
            <div class="col-md-6">
                <div class="text-muted small">Status</div>
                <div class="fw-medium">
                    <span class="badge {{ statusBadgeClass }}">{{ item.status }}</span>
                </div>
            </div>
            <div class="col-md-6">
                <div class="text-muted small">Created At</div>
                <div class="fw-medium">{{ formattedCreatedAt }}</div>
            </div>
            <div class="col-md-6">
                <div class="text-muted small">Updated At</div>
                <div class="fw-medium">{{ formattedUpdatedAt }}</div>
            </div>
        </div>
    </div>
</div>
{{ end }}
""")
public class ItemViewPage {

    @Autowired private RequestContext ctx;
    @Autowired private ItemService itemService;
    @Autowired private UomService uomService;

    // Template fields
    Item item;
    String uomDisplay = "-";
    String statusBadgeClass = "bg-secondary";
    String formattedCreatedAt = "-";
    String formattedUpdatedAt = "-";

    public void init() {
        String id = ctx.path("id");
        if (id != null) {
            item = itemService.findById(id);
        }

        if (item != null) {
            // Pre-compute display values
            if (item.getUomId() != null) {
                Uom uom = uomService.findById(item.getUomId());
                if (uom != null) {
                    uomDisplay = uom.getCode() + " - " + uom.getName();
                }
            }
            statusBadgeClass = "Active".equals(item.getStatus()) ? "bg-success" : "bg-secondary";
            formattedCreatedAt = formatDateTime(item.getCreatedAt());
            formattedUpdatedAt = formatDateTime(item.getUpdatedAt());
        }
    }

    private String formatDateTime(String iso) {
        if (iso == null) return "-";
        return iso.substring(0, Math.min(16, iso.length())).replace("T", " ");
    }
}
```

### Pattern D: Pre-computing display values

Templates have limited expression capabilities. Complex formatting, lookups, and derived values MUST be computed in Java and stored in fields.

**Preferred approach: `{{ for }}` with POJOs**

For list pages, define a static inner class with `@Getter @AllArgsConstructor` to hold pre-computed display values per row (see Pattern A). For dropdowns and checkboxes, use the built-in `SelectOption` and `CheckboxOption` POJOs (see Pattern E).

```java
// Inner POJO for table rows
@Getter @AllArgsConstructor
public static class ItemRow {
    private final String id;
    private final String name;
    private final String status;
    private final String statusBadgeClass;  // pre-computed: "bg-success" or "bg-secondary"
    private final String updatedAt;         // pre-formatted: "2024-01-15 10:30"
}
```

```html
<!-- Template iterates POJOs — {{ variable }} auto-escapes, {{ raw }} for trusted values -->
{{ for row in rows }}
<tr>
    <td>{{ row.name }}</td>
    <td><span class="badge {{ raw row.statusBadgeClass }}">{{ row.status }}</span></td>
    <td>{{ row.updatedAt }}</td>
</tr>
{{ end }}
```

**When to use `{{ raw }}` with StringBuilder instead:**

Use `{{ raw preBuiltHtml }}` only for content that cannot be expressed with `{{ for }}`:
- **Pagination HTML** — complex conditional logic with page numbers, disabled states
- **Recursive tree structures** — nested parent/child rendering
- **Embedded `data-*` attributes** — e.g., `<option data-stock="5" data-uom="kg">` for JavaScript access
- **Complex conditional button groups** — e.g., different button classes per status transition

**IMPORTANT: `{{ for }}` requires POJOs with getters, NOT Maps:**

```html
<!-- WORKS: POJO with getters (inner class, entity, SelectOption, etc.) -->
{{ for row in rows }}
    <td>{{ row.name }}</td>
{{ end }}

<!-- WORKS: String list -->
{{ for error in errors }}
    <li>{{ error }}</li>
{{ end }}

<!-- DOES NOT WORK: Map<String,Object> has no getName() method -->
{{ for row in mapList }}
    <td>{{ row.name }}</td>   <!-- ERROR: Map.getName() doesn't exist -->
{{ end }}
```

**For single-value computed fields**, use getter methods or pre-computed fields:

```java
@Page(value = "/items/{id}", layout = "main")
@Template("""
<span class="badge {{ statusBadgeClass }}">{{ item.status }}</span>
""")
public class ItemViewPage {
    Item item;

    // The template accesses this as {{ statusBadgeClass }}
    public String getStatusBadgeClass() {
        if (item == null) return "bg-secondary";
        return "Active".equals(item.getStatus()) ? "bg-success" : "bg-secondary";
    }
}
```

### Pattern E: Dropdowns and checkboxes with SelectOption / CheckboxOption

`candi.runtime.SelectOption` and `candi.runtime.CheckboxOption` are shared POJOs with proper getters, designed for use with `{{ for }}` loops in templates.

#### SelectOption — for `<select>` dropdowns

```java
import candi.runtime.SelectOption;

// Build options list
List<SelectOption> uomOptions = new ArrayList<>();
for (Uom uom : uomService.findActive()) {
    uomOptions.add(new SelectOption(
        uom.getId(),                              // value
        uom.getCode() + " - " + uom.getName(),   // label
        uom.getId().equals(selectedUomId)          // selected (Boolean)
    ));
}
```

```html
<!-- Template pattern for <select> -->
<select name="uomId" class="form-select" required>
    <option value="">Select UOM...</option>
    {{ for opt in uomOptions }}
    {{ if opt.selected }}<option value="{{ opt.value }}" selected>{{ opt.label }}</option>
    {{ else }}<option value="{{ opt.value }}">{{ opt.label }}</option>{{ end }}
    {{ end }}
</select>
```

#### CheckboxOption — for checkbox/radio lists

```java
import candi.runtime.CheckboxOption;

// Build checkbox options
List<CheckboxOption> roleCheckboxes = new ArrayList<>();
for (FunctionalRole role : allRoles) {
    roleCheckboxes.add(new CheckboxOption(
        role.getId(),       // value
        role.getName(),     // label
        selectedRoleIds.contains(role.getId())  // checked (Boolean)
    ));
}
```

```html
<!-- Template pattern for checkboxes -->
{{ for role in roleCheckboxes }}
{{ if role.checked }}<label class="checkbox-item"><input type="checkbox" name="roleIds" value="{{ role.value }}" checked> {{ role.label }}</label>
{{ else }}<label class="checkbox-item"><input type="checkbox" name="roleIds" value="{{ role.value }}"> {{ role.label }}</label>{{ end }}
{{ end }}
```

**Note:** `SelectOption` has fields `value`, `label`, `selected` (Boolean). `CheckboxOption` has fields `value`, `label`, `checked` (Boolean). Both are in `candi.runtime` and have proper `getXxx()` methods.

---

## Quick Reference: Do's and Don'ts

### DO

- One file per page. Keep the template and logic together.
- Use `init()` for GET data loading. Use `@Post` for form handling.
- **Add `@Getter` (Lombok) to all page/widget classes** so the generated `_Candi` subclass can access fields.
- **Use `Boolean` (wrapper)** for boolean template fields, NOT `boolean` (primitive).
- **Use `{{ for }}` with POJOs** for table rows, dropdowns (`SelectOption`), checkboxes (`CheckboxOption`), and error lists (`List<String>`). Only use `{{ raw }}` for pagination, recursive trees, or HTML with `data-*` attributes (see Pattern D).
- **Keep layouts static** — no `RequestContext`, no request-scoped beans. Use JS for active highlighting.
- Pre-compute display values (formatting, lookups, conditionals) in Java, not in templates.
- Use `ActionResult.redirect()` after successful POST (PRG pattern).
- Use `ActionResult.render()` for validation errors to preserve user input.
- Null-check all `ctx.form()` and `ctx.query()` return values.
- Re-load dropdown/reference data in the `@Post` method if you might re-render.
- Use `{{ variable }}` (escaped) by default. Only use `{{ raw }}` for trusted HTML.
- Use filters for common transforms: `{{ name | upper }}`, `{{ bio | truncate(200) }}`, `{{ price | number("$#,##0.00") }}`.
- Use ternary `{{ cond ? "a" : "b" }}` for simple inline conditionals instead of `{{ if }}/{{ end }}` blocks.
- Use null coalescing `{{ name ?? "Guest" }}` instead of `{{ if name }}{{ name }}{{ else }}Guest{{ end }}`.
- Use loop metadata (`item_index`, `item_first`, `item_last`) instead of computing row numbers in Java.
- Use `{{ switch }}` for multi-way conditionals instead of long if/else-if chains.
- Use `{{ set x = expr }}` for computed template-local values to avoid repeating expressions.
- Use named slots (`{{ slot }}`/`{{ block }}`) for per-page layout customization (sidebars, nav, etc.).
- Use asset stacking (`{{ push }}`/`{{ stack }}`) for per-page scripts and styles in layouts.
- Use `{{ ~` }}` for string concatenation in templates, NOT `+` (which is arithmetic).
- Use `String` for IDs and timestamps in entities.
- Provide no-arg constructors and getters/setters on all entities.
- **Import from `candi.runtime`** (NOT `candi.annotation`).

### DON'T

- **Don't inject `RequestContext` into layouts** — layouts are singletons, `RequestContext` is request-scoped. The app will fail to start.
- **Don't use `Map<String, Object>` in `{{ for }}` loops** — Maps don't have getter methods. Use a static inner POJO with `@Getter @AllArgsConstructor` instead. For dropdowns use `SelectOption`, for checkboxes use `CheckboxOption`.
- **Don't use primitive `boolean`** for template fields — use `Boolean` wrapper.
- **Don't forget `@Getter`** on page classes — the generated `_Candi` class needs getters.
- Don't store request-specific data in layout fields — layouts are singletons shared across all requests.
- Don't put complex logic in templates. Move it to Java.
- Don't forget to set `createdAt`/`updatedAt` when creating/updating entities.
- Don't use `@Delete` -- use `@Post` on a separate delete page.
- Don't forget the `annotationProcessor` declaration for `candi-processor`.
- Don't use `@Page` without `@Template` -- both are required.
- Don't render user input with `{{ raw }}` -- use `{{ variable }}` for HTML-escaped output.
- Don't forget to re-populate dropdown data sources in the `@Post` method when using `ActionResult.render()`.
- Don't import from `candi.annotation` — that package doesn't exist. Use `candi.runtime`.

---

## Known Issues & Gotchas (Candi 0.1.0)

A consolidated list of non-obvious pitfalls discovered during development. Each entry describes the symptom, root cause, and fix.

### 1. `package candi.annotation does not exist`

**Symptom:** Compilation fails with `package candi.annotation does not exist`.

**Cause:** All Candi annotations (`@Page`, `@Template`, `@Layout`, `@Widget`, `@Post`, etc.) and runtime classes (`RequestContext`, `ActionResult`, `HtmlOutput`) are in the `candi.runtime` package. There is no `candi.annotation` package.

**Fix:** Use `import candi.runtime.*` or import specific classes from `candi.runtime`:
```java
import candi.runtime.Page;
import candi.runtime.Template;
import candi.runtime.RequestContext;
import candi.runtime.ActionResult;
import candi.runtime.Post;
```

### 2. `cannot find symbol` for template variables in generated `_Candi` class

**Symptom:** Compilation fails with errors like `cannot find symbol: method getFieldName()` in the generated `*_Candi.java` file.

**Cause:** The Candi annotation processor generates a `_Candi` subclass that accesses page fields via **getter methods** (`this.getFieldName()`). If the field has no getter, the generated code won't compile.

**Fix:** Add Lombok `@Getter` to the page class:
```java
import lombok.Getter;

@Getter
@Page(value = "/path", layout = "main")
@Template("""...""")
public class MyPage {
    private String myField;  // Lombok generates getMyField()
}
```

Ensure `build.gradle` includes Lombok:
```groovy
compileOnly 'org.projectlombok:lombok'
annotationProcessor 'org.projectlombok:lombok'
```

### 3. Boolean getter mismatch: `isXxx()` vs `getXxx()`

**Symptom:** Compilation fails with `cannot find symbol: method getHasItems()` even though `@Getter` is present.

**Cause:** For primitive `boolean` fields, Lombok generates `isXxx()` (e.g., `isHasItems()`). But the Candi processor generates code calling `getXxx()` (e.g., `getHasItems()`). This mismatch causes compilation failure.

**Fix:** Use `Boolean` (wrapper type) instead of `boolean` (primitive):
```java
// CORRECT — Lombok generates getHasItems()
private Boolean hasItems;

// WRONG — Lombok generates isHasItems(), Candi expects getHasItems()
private boolean hasItems;
```

### 4. `Map.getName()` doesn't exist in for-loops

**Symptom:** Compilation fails with `cannot find symbol: method getName()` on `Map` objects inside `{{ for }}` loops.

**Cause:** The template `{{ for item in list }}{{ item.name }}{{ end }}` generates Java code that calls `item.getName()`. This works for POJOs/entities with getters, but `Map<String, Object>` does not have a `getName()` method.

**Fix:** Use a static inner POJO with `@Getter @AllArgsConstructor`:
```java
@Getter @AllArgsConstructor
public static class ItemRow {
    private final String name;
    private final String status;
    private final String statusBadgeClass;
}

private List<ItemRow> rows = new ArrayList<>();

public void init() {
    for (Item item : pageItems) {
        rows.add(new ItemRow(
            item.getName(),
            item.getStatus(),
            "Active".equals(item.getStatus()) ? "bg-success" : "bg-secondary"
        ));
    }
}
```

Template:
```html
{{ for row in rows }}
<tr>
    <td>{{ row.name }}</td>
    <td><span class="badge {{ raw row.statusBadgeClass }}">{{ row.status }}</span></td>
</tr>
{{ end }}
```

For dropdowns, use `candi.runtime.SelectOption`. For checkboxes, use `candi.runtime.CheckboxOption`. See Pattern E.

### 5. Layout fails to start: `Scope 'request' is not active`

**Symptom:** Application fails to start with: `Error creating bean with name 'mainLayout': Scope 'request' is not active for the current thread`.

**Cause:** The generated layout bean (`MainLayout_Candi`) is a **singleton** (`@Component` without `@Scope`). It's created once at startup. If the layout class has `@Autowired RequestContext ctx`, Spring tries to inject the request-scoped `RequestContext` into a singleton — which fails because there is no active HTTP request at startup.

**Note:** Page beans are different — they ARE request-scoped (`@Scope(SCOPE_REQUEST)`), so they CAN safely use `RequestContext`.

**Fix:** Remove `RequestContext` from the layout. Keep layouts as pure static HTML:
```java
@Layout
@Template("""...""")
public class MainLayout {
    // No @Autowired RequestContext — layout is a singleton
    // No request-specific fields
}
```

For sidebar active highlighting, use client-side JavaScript:
```javascript
document.addEventListener('DOMContentLoaded', function() {
    var path = window.location.pathname;
    document.querySelectorAll('.sidebar-menu .nav-link').forEach(function(link) {
        var href = link.getAttribute('href');
        if (!href) return;
        if ((href === '/' && path === '/') || (href !== '/' && path.startsWith(href))) {
            link.classList.add('active');
        }
    });
});
```

### 6. Template `{{ if }}` conditionals use direct field access

**Symptom:** `{{ if myField }}` in templates generates code like `if (myField != null ...)` — accessing the field directly, not via a getter method.

**Cause:** Unlike `{{ variable }}` output (which generates `this.getVariable()`), `{{ if }}` conditionals generate direct field access in the `_Candi` subclass.

**Implication:** The field must be accessible from the subclass (i.e., package-private or protected, or the subclass is in the same package). With `@Getter` + private fields, this works because the generated `_Candi` class is in the same package. But for layouts (singletons), computed methods like `isDashboardActive()` without backing fields will NOT work — you need actual fields.
