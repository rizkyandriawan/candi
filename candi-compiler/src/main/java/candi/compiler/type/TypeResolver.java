package candi.compiler.type;

import candi.compiler.ast.*;
import candi.compiler.codegen.CodeGenerator;
import candi.compiler.expr.Expression;

import java.util.*;

/**
 * Resolves types for page variables and expressions using classpath reflection.
 *
 * <p>Usage:
 * <pre>
 *   TypeResolver resolver = new TypeResolver(classLoader);
 *   List<TypeCheckError> errors = resolver.resolve(pageNode);
 *   TypeInfo postType = resolver.getVariableType("post");
 * </pre>
 *
 * <p>Resolution steps:
 * 1. Resolve @inject types from classpath
 * 2. Infer @init variable types from RHS expressions
 * 3. Walk body AST and type-check all expressions
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

        // Step 1: Resolve @inject types
        for (InjectNode inject : page.injects()) {
            TypeInfo type = resolveTypeName(inject.typeName());
            if (type != null) {
                symbolTable.put(inject.variableName(), type);
            } else {
                errors.add(new TypeCheckError(
                        "Cannot resolve type: " + inject.typeName(),
                        inject.location()));
            }
        }

        // Step 2: Infer @init variable types
        if (page.init() != null) {
            inferInitTypes(page.init());
        }

        // Step 3: Type-check body expressions
        if (page.body() != null) {
            checkBody(page.body().children());
        }

        // Step 4: Type-check fragment bodies
        for (FragmentDefNode fragment : page.fragments()) {
            checkBody(fragment.body().children());
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
     * Handles simple names, fully-qualified names, and common imports.
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
        // Try as-is (fully qualified)
        try {
            return classLoader.loadClass(name);
        } catch (ClassNotFoundException ignored) {}

        // Try java.lang package
        try {
            return classLoader.loadClass("java.lang." + name);
        } catch (ClassNotFoundException ignored) {}

        // Try java.util package
        try {
            return classLoader.loadClass("java.util." + name);
        } catch (ClassNotFoundException ignored) {}

        return null;
    }

    /**
     * Infer types of @init block variables from their RHS.
     * Uses simple pattern matching on the init code.
     */
    private void inferInitTypes(InitNode init) {
        Set<String> vars = CodeGenerator.extractInitVariables(init.code());
        for (String var : vars) {
            // Try to infer type from the assignment RHS
            TypeInfo inferred = inferFromInitCode(init.code(), var);
            symbolTable.put(var, inferred != null ? inferred : TypeInfo.OBJECT);
        }
    }

    /**
     * Try to infer the type of a variable from its @init assignment.
     * e.g. "post = posts.getById(id);" → look up posts type, resolve getById return type
     */
    private TypeInfo inferFromInitCode(String code, String varName) {
        for (String line : code.split("\n")) {
            String trimmed = line.trim();
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx <= 0) continue;

            String lhs = trimmed.substring(0, eqIdx).trim();
            if (!lhs.equals(varName)) continue;

            String rhs = trimmed.substring(eqIdx + 1).trim();
            if (rhs.endsWith(";")) rhs = rhs.substring(0, rhs.length() - 1).trim();

            // Simple pattern: varName.methodName(...)
            int dotIdx = rhs.indexOf('.');
            if (dotIdx > 0) {
                String objectName = rhs.substring(0, dotIdx).trim();
                TypeInfo objectType = symbolTable.get(objectName);
                if (objectType != null) {
                    String rest = rhs.substring(dotIdx + 1).trim();
                    int parenIdx = rest.indexOf('(');
                    if (parenIdx > 0) {
                        String methodName = rest.substring(0, parenIdx).trim();
                        // Count args roughly
                        String argsStr = rest.substring(parenIdx + 1);
                        int argCount = argsStr.contains(",") ? argsStr.split(",").length : (argsStr.trim().startsWith(")") ? 0 : 1);
                        TypeInfo result = objectType.resolveMethod(methodName, argCount);
                        if (result != null) return result;
                    } else {
                        TypeInfo result = objectType.resolveProperty(rest);
                        if (result != null) return result;
                    }
                }
            }
        }
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
            case HtmlNode ignored -> {} // Static HTML, no type checking needed
            case ExpressionOutputNode expr -> resolveExpressionType(expr.expression());
            case RawExpressionOutputNode expr -> resolveExpressionType(expr.expression());
            case IfNode ifNode -> {
                TypeInfo condType = resolveExpressionType(ifNode.condition());
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
                        // Add loop variable to scope
                        TypeInfo elementType = collectionType.elementType();
                        symbolTable.put(forNode.variableName(), elementType);
                    }
                }
                checkBody(forNode.body().children());
            }
            case FragmentCallNode ignored -> {} // Fragment calls are validated at runtime
            case ComponentCallNode comp -> {
                for (Expression expr : comp.params().values()) {
                    resolveExpressionType(expr);
                }
            }
            case SlotRenderNode ignored -> {}
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
            case Expression.Grouped g -> resolveExpressionType(g.inner());
        };
    }
}
