package candi.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as bound to a URL path variable.
 * The field value is populated before init() runs.
 *
 * <pre>
 * {@literal @}Page(value = "/items/{id}", layout = "main")
 * public class ItemViewPage {
 *     {@literal @}PathVariable {@literal @}Setter private String id;
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PathVariable {
    /** The name of the path variable. Defaults to the field name. */
    String value() default "";
    /** Alias for value. */
    String name() default "";
}
