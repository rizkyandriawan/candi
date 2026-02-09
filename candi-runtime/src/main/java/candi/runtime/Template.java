package candi.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the HTML template for a Candi page, layout, or widget.
 * Use with Java text blocks for inline templates:
 *
 * <pre>
 * {@code @Page("/posts")}
 * {@code @Template("""
 * <h1>{{ title }}</h1>
 * """)}
 * public class PostsPage { ... }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Template {

    /**
     * Inline template content (HTML with {{ }} expressions).
     */
    String value();
}
