package candi.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation placed on generated page classes to declare their route.
 * Used by PageRegistry to discover and index routes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CandiRoute {

    /**
     * The URL path pattern (e.g. "/posts", "/post/{id}/edit").
     */
    String path();

    /**
     * Supported HTTP methods (e.g. {"GET", "POST", "DELETE"}).
     */
    String[] methods() default {"GET"};
}
