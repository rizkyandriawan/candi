package candi.demo.pages;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.springframework.web.context.WebApplicationContext;
import candi.runtime.CandiPage;
import candi.runtime.HtmlOutput;
import candi.runtime.CandiRoute;
import candi.demo.service.PostService;

import java.util.List;

/**
 * Hand-compiled page simulating compiler output for a page with a fragment:
 *
 * @Page("/search")
 * public class SearchPage {
 *     @Autowired private PostService posts;
 *     private List<PostService.Post> results;
 *
 *     public void onGet() {
 *         results = posts.findAll();
 *     }
 * }
 *
 * <template>
 * <h1>Search</h1>
 * {{ fragment "results" }}
 * <ul>
 * {{ for post in results }}
 *   <li>{{ post.title }}</li>
 * {{ end }}
 * </ul>
 * {{ end }}
 * </template>
 */
@Component
@Scope(WebApplicationContext.SCOPE_REQUEST)
@CandiRoute(path = "/search", methods = {"GET"})
public class Search__Page implements CandiPage {

    @Autowired
    private PostService posts;

    private List<PostService.Post> results;

    @Override
    public void onGet() {
        this.results = posts.findAll();
    }

    @Override
    public void render(HtmlOutput out) {
        out.append("<h1>Search</h1>\n");
        // fragment "results" renders inline
        out.append("<ul>\n");
        for (var post : this.results) {
            out.append("<li>");
            out.appendEscaped(String.valueOf(post.title()));
            out.append("</li>\n");
        }
        out.append("</ul>\n");
    }

    @Override
    public void renderFragment(String _name, HtmlOutput out) {
        switch (_name) {
            case "results" -> renderFragment_results(out);
            default -> throw new IllegalArgumentException("Unknown fragment: " + _name);
        }
    }

    private void renderFragment_results(HtmlOutput out) {
        out.append("<ul>\n");
        for (var post : this.results) {
            out.append("<li>");
            out.appendEscaped(String.valueOf(post.title()));
            out.append("</li>\n");
        }
        out.append("</ul>\n");
    }
}
