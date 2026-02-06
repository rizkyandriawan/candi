package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.*;
import candi.demo.service.PostService;
import java.util.Objects;

/**
 * Hand-compiled page simulating compiler output for:
 *
 * @page "/post/{id}/edit"
 * @inject PostService posts
 * @inject RequestContext ctx
 *
 * @init {
 *   post = posts.getById(ctx.path("id"));
 * }
 *
 * @action POST {
 *   posts.update(ctx.path("id"), ctx.form("title"));
 *   redirect("/post/" + ctx.path("id"));
 * }
 *
 * @action DELETE {
 *   posts.delete(ctx.path("id"));
 *   redirect("/");
 * }
 *
 * <h1>Edit Post</h1>
 * <form method="POST">
 *   <input name="title" value="{{ post.title }}">
 *   <button>Save</button>
 * </form>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/post/{id}/edit", methods = {"GET", "POST", "DELETE"})
public class PostEdit__Page implements CandiPage {

    @Autowired
    private PostService posts;

    @Autowired
    private RequestContext ctx;

    private Object post;

    @Override
    public void init() {
        this.post = posts.getById(ctx.path("id"));
    }

    @Override
    public ActionResult handleAction(String method) {
        if ("POST".equals(method)) {
            this.posts.update(ctx.path("id"), ctx.form("title"));
            return ActionResult.redirect("/post/" + ctx.path("id"));
        }
        if ("DELETE".equals(method)) {
            this.posts.delete(ctx.path("id"));
            return ActionResult.redirect("/");
        }
        return ActionResult.methodNotAllowed();
    }

    @Override
    public void render(HtmlOutput out) {
        PostService.Post p = (PostService.Post) this.post;
        out.append("<h1>Edit Post</h1>\n");
        out.append("<form method=\"POST\">\n");
        out.append("  <input name=\"title\" value=\"");
        out.appendEscaped(String.valueOf(p.title()));
        out.append("\">\n");
        out.append("  <button>Save</button>\n");
        out.append("</form>\n");
    }
}
