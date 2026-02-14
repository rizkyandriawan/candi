package candi.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as bound to a query/form parameter.
 * The field value is populated before init() runs.
 *
 * <pre>
 * {@literal @}Page(value = "/items", layout = "main")
 * public class ItemListPage {
 *     {@literal @}RequestParam(value = "q", defaultValue = "") {@literal @}Setter
 *     private String searchTerm;
 *     ...
 * }
 * </pre>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParam {
    /** The name of the request parameter. Defaults to the field name. */
    String value() default "";
    /** Alias for value. */
    String name() default "";
    /** Default value if the parameter is missing. */
    String defaultValue() default "\u0000";
    /** Whether the parameter is required. Default false. */
    boolean required() default false;
}
