package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.*;
import candi.demo.service.PostService;

/**
 * Hand-compiled page simulating v2 compiler output for:
 *
 * @Page("/post/{id}/edit")
 * public class PostEditPage {
 *
 *     @Autowired
 *     private PostService posts;
 *
 *     @Autowired
 *     private RequestContext ctx;
 *
 *     private PostService.Post post;
 *
 *     public void init() {
 *         post = posts.getById(ctx.path("id"));
 *     }
 *
 *     @Post
 *     public ActionResult update() {
 *         posts.update(ctx.path("id"), ctx.form("title"));
 *         return ActionResult.redirect("/post/" + ctx.path("id"));
 *     }
 *
 *     @Delete
 *     public ActionResult remove() {
 *         posts.delete(ctx.path("id"));
 *         return ActionResult.redirect("/");
 *     }
 * }
 *
 * <template>
 * <h1>Edit Post</h1>
 * <form method="POST">
 *   <input name="title" value="{{ post.title }}">
 *   <button>Save</button>
 * </form>
 * </template>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/post/{id}/edit", methods = {"GET", "POST", "DELETE"})
public class PostEdit__Page implements CandiPage {

    @Autowired
    private PostService posts;

    @Autowired
    private RequestContext ctx;

    private PostService.Post post;

    @Override
    public void init() {
        this.post = posts.getById(ctx.path("id"));
    }

    @Post
    public ActionResult update() {
        this.posts.update(ctx.path("id"), ctx.form("title"));
        return ActionResult.redirect("/post/" + ctx.path("id"));
    }

    @Delete
    public ActionResult remove() {
        this.posts.delete(ctx.path("id"));
        return ActionResult.redirect("/");
    }

    @Override
    public void render(HtmlOutput out) {
        out.append("<h1>Edit Post</h1>\n");
        out.append("<form method=\"POST\">\n");
        out.append("  <input name=\"title\" value=\"");
        out.appendEscaped(String.valueOf(this.post.title()));
        out.append("\">\n");
        out.append("  <button>Save</button>\n");
        out.append("</form>\n");
    }
}
