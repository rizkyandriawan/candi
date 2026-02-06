package candi.compiler.type;

import java.lang.reflect.*;
import java.util.List;
import java.util.Objects;

/**
 * Represents a resolved Java type. Wraps java.lang.reflect.Type
 * to provide convenient access to class info and generics.
 */
public class TypeInfo {

    public static final TypeInfo OBJECT = new TypeInfo(Object.class, Object.class);
    public static final TypeInfo STRING = new TypeInfo(String.class, String.class);
    public static final TypeInfo BOOLEAN = new TypeInfo(boolean.class, boolean.class);
    public static final TypeInfo INT = new TypeInfo(int.class, int.class);
    public static final TypeInfo DOUBLE = new TypeInfo(double.class, double.class);
    public static final TypeInfo VOID = new TypeInfo(void.class, void.class);

    private final Class<?> rawClass;
    private final Type genericType;

    public TypeInfo(Class<?> rawClass, Type genericType) {
        this.rawClass = rawClass;
        this.genericType = genericType;
    }

    public TypeInfo(Class<?> rawClass) {
        this(rawClass, rawClass);
    }

    public Class<?> rawClass() {
        return rawClass;
    }

    public Type genericType() {
        return genericType;
    }

    /**
     * Get the element type for Iterable/Collection types.
     * e.g. List<Post> → Post, Set<String> → String
     */
    public TypeInfo elementType() {
        if (genericType instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length > 0 && Iterable.class.isAssignableFrom(rawClass)) {
                return fromReflectType(args[0]);
            }
        }
        return OBJECT;
    }

    /**
     * Resolve a property (getter) on this type.
     * post.title → Post.getTitle() return type
     */
    public TypeInfo resolveProperty(String propertyName) {
        String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        try {
            Method method = rawClass.getMethod(getterName);
            return fromReflectType(method.getGenericReturnType());
        } catch (NoSuchMethodException e) {
            // Try boolean getter: isXxx
            String boolGetter = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            try {
                Method method = rawClass.getMethod(boolGetter);
                return fromReflectType(method.getGenericReturnType());
            } catch (NoSuchMethodException e2) {
                // Try record-style accessor: xxx()
                try {
                    Method method = rawClass.getMethod(propertyName);
                    return fromReflectType(method.getGenericReturnType());
                } catch (NoSuchMethodException e3) {
                    return null;
                }
            }
        }
    }

    /**
     * Resolve a method call on this type.
     */
    public TypeInfo resolveMethod(String methodName, int argCount) {
        for (Method m : rawClass.getMethods()) {
            if (m.getName().equals(methodName) && m.getParameterCount() == argCount) {
                return fromReflectType(m.getGenericReturnType());
            }
        }
        return null;
    }

    public boolean isIterable() {
        return Iterable.class.isAssignableFrom(rawClass);
    }

    public boolean isBoolean() {
        return rawClass == boolean.class || rawClass == Boolean.class;
    }

    public boolean isNumeric() {
        return Number.class.isAssignableFrom(rawClass)
                || rawClass == int.class || rawClass == long.class
                || rawClass == float.class || rawClass == double.class;
    }

    public boolean isString() {
        return rawClass == String.class;
    }

    public static TypeInfo fromReflectType(Type type) {
        if (type instanceof Class<?> c) {
            return new TypeInfo(c, c);
        }
        if (type instanceof ParameterizedType pt) {
            return new TypeInfo((Class<?>) pt.getRawType(), pt);
        }
        return OBJECT;
    }

    @Override
    public String toString() {
        if (genericType instanceof ParameterizedType pt) {
            StringBuilder sb = new StringBuilder(rawClass.getSimpleName());
            sb.append("<");
            Type[] args = pt.getActualTypeArguments();
            for (int i = 0; i < args.length; i++) {
                if (i > 0) sb.append(", ");
                if (args[i] instanceof Class<?> c) {
                    sb.append(c.getSimpleName());
                } else {
                    sb.append(args[i].getTypeName());
                }
            }
            sb.append(">");
            return sb.toString();
        }
        return rawClass.getSimpleName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeInfo that)) return false;
        return Objects.equals(rawClass, that.rawClass) && Objects.equals(genericType, that.genericType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawClass, genericType);
    }
}
