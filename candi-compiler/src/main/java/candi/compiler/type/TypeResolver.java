package candi.compiler.type;

import candi.compiler.ast.*;
import candi.compiler.expr.Expression;

import java.util.*;

/**
 * Resolves types for page variables and expressions using classpath reflection.
 *
 * <p>In v2, field types come directly from the Java source (explicit declarations)
 * instead of being inferred from @init code patterns.
 */
public class TypeResolver {

    private final ClassLoader classLoader;
    private final Map<String, TypeInfo> symbolTable = new LinkedHashMap<>();
    private final List<TypeCheckError> errors = new ArrayList<>();

    public TypeResolver(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public TypeResolver() {
        this(TypeResolver.class.getClassLoader());
    }

    /**
     * Resolve all types in a page AST. Returns the list of errors found.
     */
    public List<TypeCheckError> resolve(PageNode page) {
        errors.clear();
        symbolTable.clear();

        // Step 1: Resolve field types from explicit declarations
        for (var entry : page.fieldTypes().entrySet()) {
            String fieldName = entry.getKey();
            String typeName = entry.getValue();
            TypeInfo type = resolveTypeName(typeName);
            if (type != null) {
                symbolTable.put(fieldName, type);
            } else {
                errors.add(new TypeCheckError(
                        "Cannot resolve type: " + typeName,
                        page.location()));
            }
        }

        // Step 2: Type-check body expressions
        if (page.body() != null) {
            checkBody(page.body().children());
        }

        return errors;
    }

    /**
     * Get the resolved type of a variable.
     */
    public TypeInfo getVariableType(String name) {
        return symbolTable.get(name);
    }

    /**
     * Get the full symbol table.
     */
    public Map<String, TypeInfo> getSymbolTable() {
        return Collections.unmodifiableMap(symbolTable);
    }

    /**
     * Resolve a type name string to TypeInfo via classpath reflection.
     */
    TypeInfo resolveTypeName(String typeName) {
        // Strip generics for class loading (e.g. "List<Post>" -> "List")
        String rawName = typeName;
        int genericStart = typeName.indexOf('<');
        if (genericStart >= 0) {
            rawName = typeName.substring(0, genericStart).trim();
        }

        Class<?> clazz = tryLoadClass(rawName);
        if (clazz == null) return null;

        return new TypeInfo(clazz);
    }

    private Class<?> tryLoadClass(String name) {
        try {
            return classLoader.loadClass(name);
        } catch (ClassNotFoundException ignored) {}

        try {
            return classLoader.loadClass("java.lang." + name);
        } catch (ClassNotFoundException ignored) {}

        try {
            return classLoader.loadClass("java.util." + name);
        } catch (ClassNotFoundException ignored) {}

        return null;
    }

    // ========== Body type checking ==========

    private void checkBody(List<Node> nodes) {
        for (Node node : nodes) {
            checkNode(node);
        }
    }

    private void checkNode(Node node) {
        switch (node) {
            case HtmlNode ignored -> {}
            case ExpressionOutputNode expr -> resolveExpressionType(expr.expression());
            case RawExpressionOutputNode expr -> resolveExpressionType(expr.expression());
            case IfNode ifNode -> {
                resolveExpressionType(ifNode.condition());
                checkBody(ifNode.thenBody().children());
                if (ifNode.elseBody() != null) {
                    checkBody(ifNode.elseBody().children());
                }
            }
            case ForNode forNode -> {
                TypeInfo collectionType = resolveExpressionType(forNode.collection());
                if (collectionType != null) {
                    if (!collectionType.isIterable() && !collectionType.rawClass().isArray()) {
                        errors.add(new TypeCheckError(
                                "For loop requires iterable, got " + collectionType,
                                forNode.location()));
                    } else {
                        TypeInfo elementType = collectionType.elementType();
                        symbolTable.put(forNode.variableName(), elementType);
                    }
                }
                checkBody(forNode.body().children());
            }
            case IncludeNode include -> {
                for (Expression expr : include.params().values()) {
                    resolveExpressionType(expr);
                }
            }
            case ComponentCallNode comp -> {
                for (Expression expr : comp.params().values()) {
                    resolveExpressionType(expr);
                }
            }
            case ContentNode ignored -> {}
            default -> {}
        }
    }

    /**
     * Resolve the type of an expression. Returns null if unresolvable.
     */
    TypeInfo resolveExpressionType(Expression expr) {
        return switch (expr) {
            case Expression.Variable v -> {
                TypeInfo type = symbolTable.get(v.name());
                if (type == null) {
                    errors.add(new TypeCheckError(
                            "Unknown variable: " + v.name(), v.location()));
                }
                yield type;
            }
            case Expression.StringLiteral ignored -> TypeInfo.STRING;
            case Expression.NumberLiteral n -> {
                yield n.value().contains(".") ? TypeInfo.DOUBLE : TypeInfo.INT;
            }
            case Expression.BooleanLiteral ignored -> TypeInfo.BOOLEAN;
            case Expression.PropertyAccess p -> {
                TypeInfo objectType = resolveExpressionType(p.object());
                if (objectType == null) yield null;
                TypeInfo propType = objectType.resolveProperty(p.property());
                if (propType == null) {
                    errors.add(new TypeCheckError(
                            "Unknown property '" + p.property() + "' on type " + objectType,
                            p.location()));
                }
                yield propType;
            }
            case Expression.NullSafePropertyAccess p -> {
                TypeInfo objectType = resolveExpressionType(p.object());
                if (objectType == null) yield null;
                yield objectType.resolveProperty(p.property());
            }
            case Expression.MethodCall m -> {
                TypeInfo objectType = resolveExpressionType(m.object());
                if (objectType == null) yield null;
                TypeInfo returnType = objectType.resolveMethod(m.methodName(), m.arguments().size());
                if (returnType == null) {
                    errors.add(new TypeCheckError(
                            "Unknown method '" + m.methodName() + "' on type " + objectType,
                            m.location()));
                }
                yield returnType;
            }
            case Expression.NullSafeMethodCall m -> {
                TypeInfo objectType = resolveExpressionType(m.object());
                if (objectType == null) yield null;
                yield objectType.resolveMethod(m.methodName(), m.arguments().size());
            }
            case Expression.BinaryOp b -> {
                resolveExpressionType(b.left());
                resolveExpressionType(b.right());
                yield switch (b.operator()) {
                    case "==", "!=", "&&", "||", "<", ">", "<=", ">=" -> TypeInfo.BOOLEAN;
                    default -> TypeInfo.OBJECT;
                };
            }
            case Expression.UnaryNot u -> {
                resolveExpressionType(u.operand());
                yield TypeInfo.BOOLEAN;
            }
            case Expression.UnaryMinus u -> {
                resolveExpressionType(u.operand());
                yield TypeInfo.DOUBLE;
            }
            case Expression.Grouped g -> resolveExpressionType(g.inner());
            case Expression.Ternary t -> {
                resolveExpressionType(t.condition());
                TypeInfo thenType = resolveExpressionType(t.thenExpr());
                resolveExpressionType(t.elseExpr());
                yield thenType != null ? thenType : TypeInfo.OBJECT;
            }
            case Expression.NullCoalesce nc -> {
                TypeInfo leftType = resolveExpressionType(nc.left());
                resolveExpressionType(nc.fallback());
                yield leftType != null ? leftType : TypeInfo.OBJECT;
            }
            case Expression.FilterCall f -> {
                resolveExpressionType(f.input());
                for (Expression arg : f.arguments()) {
                    resolveExpressionType(arg);
                }
                yield TypeInfo.OBJECT;
            }
            case Expression.IndexAccess ia -> {
                resolveExpressionType(ia.object());
                resolveExpressionType(ia.index());
                yield TypeInfo.OBJECT;
            }
        };
    }
}
