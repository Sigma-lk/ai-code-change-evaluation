package com.sigma.ai.evaluation.domain.index.service.impl;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.sigma.ai.evaluation.domain.codegraph.model.*;
import com.sigma.ai.evaluation.domain.index.service.JavaAstParserService;
import com.sigma.ai.evaluation.types.RelationType;
import com.sigma.ai.evaluation.types.TypeKind;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Java AST 解析服务实现，使用 JavaParser + SymbolSolver 提取代码图谱节点与关系。
 *
 * <p>符号解析策略：优先解析到全限定名；遇到外部依赖（未在 sourceRoots 中）时跳过对应关系，
 * 避免因缺少 classpath 而中断整个文件的解析流程。
 */
@Slf4j
@Service
public class JavaAstParserServiceImpl implements JavaAstParserService {

    @Override
    public ParseResult parse(Path javaFile, List<Path> sourceRoots, String repoId) {
        String filePath = javaFile.toString();

        CombinedTypeSolver typeSolver = buildTypeSolver(sourceRoots);
        ParserConfiguration config = new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(typeSolver))
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
        JavaParser parser = new JavaParser(config);

        // 解析源代码
        com.github.javaparser.ParseResult<CompilationUnit> parseResult;
        try {
            parseResult = parser.parse(javaFile);
        } catch (IOException e) {
            log.error("读取 Java 文件失败: {}", filePath, e);
            return failResult(filePath, "IO error: " + e.getMessage());
        }

        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
            log.warn("Java 文件语法解析失败: {}, problems={}", filePath, parseResult.getProblems());
            return failResult(filePath, parseResult.getProblems().toString());
        }

        CompilationUnit cu = parseResult.getResult().get();
        try {
            ParseContext ctx = new ParseContext(javaFile, repoId, filePath);
            ctx.process(cu);
            log.debug("解析成功: {}, types={}, methods={}, fields={}, relations={}",
                    filePath, ctx.types.size(), ctx.methods.size(), ctx.fields.size(), ctx.relations.size());
            return ctx.build();
        } catch (Exception e) {
            log.error("AST 信息提取异常: {}", filePath, e);
            return failResult(filePath, "Extract error: " + e.getMessage());
        }
    }

    // 创建源码解析器
    private CombinedTypeSolver buildTypeSolver(List<Path> sourceRoots) {
        CombinedTypeSolver solver = new CombinedTypeSolver();
        // 不加载完整 JDK 反射树，只解析 java.lang 等核心包，性能更优
        solver.add(new ReflectionTypeSolver(false));
        for (Path root : sourceRoots) {
            if (Files.isDirectory(root)) {
                try {
                    solver.add(new JavaParserTypeSolver(root));
                } catch (Exception e) {
                    log.debug("TypeSolver 添加源根失败（跳过）: {}, reason={}", root, e.getMessage());
                }
            }
        }
        return solver;
    }

    private static ParseResult failResult(String filePath, String msg) {
        return ParseResult.builder().filePath(filePath).success(false).errorMessage(msg).build();
    }

    // 解析上下文
    private static class ParseContext {

        private final Path javaFile;
        private final String repoId;
        private final String filePath;

        private PackageNode packageNode;
        private JavaFileNode fileNode;
        private final List<TypeNode> types = new ArrayList<>();
        private final List<MethodNode> methods = new ArrayList<>();
        private final List<FieldNode> fields = new ArrayList<>();
        private final List<GraphRelation> relations = new ArrayList<>();

        ParseContext(Path javaFile, String repoId, String filePath) {
            this.javaFile = javaFile;
            this.repoId = repoId;
            this.filePath = filePath;
        }

        void process(CompilationUnit cu) throws IOException {
            String pkgName = cu.getPackageDeclaration()
                    .map(NodeWithName::getNameAsString)
                    .orElse("");

            // 包节点
            this.packageNode = PackageNode.builder()
                    .id(repoId + ":" + pkgName)
                    .qualifiedName(pkgName)
                    .path(javaFile.getParent().toString())
                    .repoId(repoId)
                    .build();

            // 文件节点
            this.fileNode = JavaFileNode.builder()
                    .path(filePath)
                    .relativePath("")
                    .checksum(computeFileChecksum(javaFile))
                    .lineCount(countLines(javaFile))
                    .lastModified(Files.getLastModifiedTime(javaFile).toMillis())
                    .repoId(repoId)
                    .build();

            // 建立包到文件的映射，包包含Java文件
            addRelation(packageNode.getId(), "Package", "id",
                    RelationType.CONTAINS_FILE, filePath, "JavaFile", "path", null);

            // 为import 语句建立 IMPORTS关系 
            for (ImportDeclaration imp : cu.getImports()) {
                if (!imp.isAsterisk() && !imp.isStatic()) {
                    addRelation(filePath, "JavaFile", "path",
                            RelationType.IMPORTS, imp.getNameAsString(), "Type", "qualifiedName", null);
                }
            }

            // 处理所有顶层类型声明
            for (TypeDeclaration<?> td : cu.getTypes()) {
                processType(td, pkgName, null);
            }
        }

        private void processType(TypeDeclaration<?> td, String pkgName, String outerQualifiedName) {
            String qualifiedName = outerQualifiedName != null
                    ? outerQualifiedName + "." + td.getNameAsString()
                    : (pkgName.isEmpty() ? td.getNameAsString() : pkgName + "." + td.getNameAsString());

            TypeKind kind = resolveTypeKind(td);
            // 抽象、final、static识别
            boolean isAbstractFlag = (td instanceof ClassOrInterfaceDeclaration coid) && coid.isAbstract();
            boolean isFinalFlag = (td instanceof ClassOrInterfaceDeclaration c) && c.isFinal();
            boolean isStaticFlag = td.isStatic();

            types.add(TypeNode.builder()
                    .qualifiedName(qualifiedName)
                    .simpleName(td.getNameAsString())
                    .kind(kind)
                    .accessModifier(td.getAccessSpecifier().asString())
                    .isAbstract(isAbstractFlag)
                    .isFinal(isFinalFlag)
                    .isStatic(isStaticFlag)
                    .filePath(filePath)
                    .lineStart(td.getRange().map(r -> r.begin.line).orElse(0))
                    .lineEnd(td.getRange().map(r -> r.end.line).orElse(0))
                    .build());

            // 建立文件到Java类型的关系（类、接口、枚举等）
            addRelation(filePath, "JavaFile", "path",
                    RelationType.DEFINES_TYPE, qualifiedName, "Type", "qualifiedName", null);

            // 建立内部类到外部类的关系
            if (outerQualifiedName != null) {
                addRelation(qualifiedName, "Type", "qualifiedName",
                        RelationType.INNER_CLASS_OF, outerQualifiedName, "Type", "qualifiedName", null);
            }

            // 建立继承、实现关系（适用类、接口）
            if (td instanceof ClassOrInterfaceDeclaration coid) {
                for (ClassOrInterfaceType ext : coid.getExtendedTypes()) {
                    String extName = resolveClassOrInterfaceTypeName(ext);
                    addRelation(qualifiedName, "Type", "qualifiedName",
                            RelationType.EXTENDS, extName, "Type", "qualifiedName", null);
                }
                for (ClassOrInterfaceType impl : coid.getImplementedTypes()) {
                    String implName = resolveClassOrInterfaceTypeName(impl);
                    addRelation(qualifiedName, "Type", "qualifiedName",
                            RelationType.IMPLEMENTS, implName, "Type", "qualifiedName", null);
                }
            }

            // 字段
            for (FieldDeclaration fd : td.getFields()) {
                processField(fd, qualifiedName);
            }

            // 方法
            for (MethodDeclaration md : td.getMethods()) {
                processMethod(md, qualifiedName);
            }

            // 构造器
            for (ConstructorDeclaration cd : td.getConstructors()) {
                processConstructor(cd, qualifiedName);
            }

            // 嵌套类型递归
            td.getMembers().stream()
                    .filter(m -> m instanceof TypeDeclaration)
                    .map(m -> (TypeDeclaration<?>) m)
                    .forEach(inner -> processType(inner, pkgName, qualifiedName));
        }

        private void processField(FieldDeclaration fd, String ownerQualifiedName) {
            String typeNameStr = fd.getElementType().asString();
            int lineNo = fd.getRange().map(r -> r.begin.line).orElse(0);

            // 尝试解析字段类型的全限定名，失败则保持 null
            String typeQualifiedName = null;
            try {
                typeQualifiedName = fd.getElementType().resolve().describe();
            } catch (Exception ignored) {
            }

            for (VariableDeclarator vd : fd.getVariables()) {
                String fieldId = ownerQualifiedName + "#" + vd.getNameAsString();
                fields.add(FieldNode.builder()
                        .id(fieldId)
                        .ownerQualifiedName(ownerQualifiedName)
                        .simpleName(vd.getNameAsString())
                        .typeName(typeNameStr)
                        .typeQualifiedName(typeQualifiedName)
                        .accessModifier(fd.getAccessSpecifier().asString())
                        .isStatic(fd.isStatic())
                        .isFinal(fd.isFinal())
                        .filePath(filePath)
                        .lineNo(lineNo)
                        .build());

                // 建立类型（类、接口、枚举等）到字段的关系
                addRelation(ownerQualifiedName, "Type", "qualifiedName",
                        RelationType.HAS_FIELD, fieldId, "Field", "id", null);

                // 如果是非基本类型，还需要建立depends-on关系，也就是类与类之间的依赖关系
                if (typeQualifiedName != null && !isPrimitiveLike(typeQualifiedName)) {
                    addRelation(ownerQualifiedName, "Type", "qualifiedName",
                            RelationType.DEPENDS_ON, typeQualifiedName, "Type", "qualifiedName", null);
                }
            }
        }

        private void processMethod(MethodDeclaration md, String ownerQualifiedName) {
            String methodId = buildMethodId(ownerQualifiedName, md.getNameAsString(), md.getParameters());
            methods.add(MethodNode.builder()
                    .id(methodId)
                    .ownerQualifiedName(ownerQualifiedName)
                    .simpleName(md.getNameAsString())
                    .signature(md.getDeclarationAsString(false, false, true))
                    .returnType(md.getType().asString())
                    .accessModifier(md.getAccessSpecifier().asString())
                    .isStatic(md.isStatic())
                    .isAbstract(md.isAbstract())
                    .isConstructor(false)
                    .filePath(filePath)
                    .lineStart(md.getRange().map(r -> r.begin.line).orElse(0))
                    .lineEnd(md.getRange().map(r -> r.end.line).orElse(0))
                    .build());

            // 建立类与方法的关系
            addRelation(ownerQualifiedName, "Type", "qualifiedName",
                    RelationType.HAS_METHOD, methodId, "Method", "id", null);

            // 从方法体提取 CALLS 关系
            md.getBody().ifPresent(body -> extractCalls(body, methodId));
        }

        private void processConstructor(ConstructorDeclaration cd, String ownerQualifiedName) {
            String params = cd.getParameters().stream()
                    .map(p -> stripToSimpleName(p.getType().asString()))
                    .collect(Collectors.joining(","));
            String methodId = ownerQualifiedName + "#<init>(" + params + ")";

            methods.add(MethodNode.builder()
                    .id(methodId)
                    .ownerQualifiedName(ownerQualifiedName)
                    .simpleName("<init>")
                    .signature(cd.getDeclarationAsString(false, false, true))
                    .returnType("void")
                    .accessModifier(cd.getAccessSpecifier().asString())
                    .isStatic(false)
                    .isAbstract(false)
                    .isConstructor(true)
                    .filePath(filePath)
                    .lineStart(cd.getRange().map(r -> r.begin.line).orElse(0))
                    .lineEnd(cd.getRange().map(r -> r.end.line).orElse(0))
                    .build());

            // 建立类与构造器方法的关系
            addRelation(ownerQualifiedName, "Type", "qualifiedName",
                    RelationType.HAS_METHOD, methodId, "Method", "id", null);

            extractCalls(cd.getBody(), methodId);
        }

        /** 遍历方法体，提取所有能解析的方法调用 CALLS 关系。 */
        private void extractCalls(BlockStmt body, String callerMethodId) {
            body.accept(new VoidVisitorAdapter<Void>() {
                @Override
                public void visit(MethodCallExpr n, Void arg) {
                    super.visit(n, arg);
                    try {
                        ResolvedMethodDeclaration resolved = n.resolve();
                        String calleeId = buildCalleeIdFromResolved(resolved);
                        int lineNo = n.getRange().map(r -> r.begin.line).orElse(0);
                        // 建立方法调用关系
                        addRelation(callerMethodId, "Method", "id",
                                RelationType.CALLS, calleeId, "Method", "id",
                                Map.of("lineNo", lineNo));
                    } catch (Exception e) {
                        // 外部依赖或解析失败时跳过，不影响其他关系提取
                        log.error("外部依赖解析失败", e);
                    }
                }
            }, null);
        }

        private void addRelation(String fromId, String fromLabel, String fromKey,
                                 RelationType type, String toId, String toLabel, String toKey,
                                 Map<String, Object> props) {
            relations.add(GraphRelation.builder()
                    .fromNodeId(fromId)
                    .fromNodeLabel(fromLabel)
                    .fromKeyName(fromKey)
                    .type(type)
                    .toNodeId(toId)
                    .toNodeLabel(toLabel)
                    .toKeyName(toKey)
                    .properties(props)
                    .build());
        }

        private String resolveClassOrInterfaceTypeName(ClassOrInterfaceType type) {
            try {
                return type.resolve().describe();
            } catch (Exception e) {
                return type.getNameAsString();
            }
        }

        private TypeKind resolveTypeKind(TypeDeclaration<?> td) {
            if (td instanceof ClassOrInterfaceDeclaration coid) {
                return coid.isInterface() ? TypeKind.INTERFACE : TypeKind.CLASS;
            } else if (td instanceof EnumDeclaration) {
                return TypeKind.ENUM;
            } else if (td instanceof AnnotationDeclaration) {
                return TypeKind.ANNOTATION;
            }
            return TypeKind.CLASS;
        }

        private String buildMethodId(String owner, String name, NodeList<Parameter> params) {
            String paramStr = params.stream()
                    .map(p -> stripToSimpleName(p.getType().asString()))
                    .collect(Collectors.joining(","));
            return owner + "#" + name + "(" + paramStr + ")";
        }

        private String buildCalleeIdFromResolved(ResolvedMethodDeclaration resolved) {
            String owner = resolved.declaringType().getQualifiedName();
            String name = resolved.getName();
            int count = resolved.getNumberOfParams();
            String params = count == 0 ? "" : IntStream.range(0, count)
                    .mapToObj(i -> {
                        try {
                            return stripToSimpleName(resolved.getParam(i).describeType());
                        } catch (Exception e) {
                            return "?";
                        }
                    })
                    .collect(Collectors.joining(","));
            return owner + "#" + name + "(" + params + ")";
        }

        /**
         * 将类型名称简化为简单名称（去掉包路径与泛型参数），
         * 保证方法 ID 格式的一致性。
         */
        private static String stripToSimpleName(String typeName) {
            int genericStart = typeName.indexOf('<');
            if (genericStart > 0) {
                typeName = typeName.substring(0, genericStart);
            }
            typeName = typeName.replace("[]", "").trim();
            int lastDot = typeName.lastIndexOf('.');
            return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
        }

        private static boolean isPrimitiveLike(String typeName) {
            return switch (typeName) {
                case "int", "long", "double", "float", "boolean", "byte", "short", "char", "void",
                     "java.lang.String", "java.lang.Integer", "java.lang.Long",
                     "java.lang.Boolean", "java.lang.Double", "java.lang.Float",
                     "java.lang.Short", "java.lang.Byte", "java.lang.Character" -> true;
                default -> false;
            };
        }

        private static String computeFileChecksum(Path file) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                try (InputStream is = Files.newInputStream(file);
                     DigestInputStream dis = new DigestInputStream(is, md)) {
                    byte[] buf = new byte[8192];
                    //noinspection StatementWithEmptyBody
                    while (dis.read(buf) != -1) {
                    }
                }
                return HexFormat.of().formatHex(md.digest());
            } catch (Exception e) {
                return "";
            }
        }

        private static int countLines(Path file) {
            try (var lines = Files.lines(file)) {
                return (int) lines.count();
            } catch (IOException e) {
                return 0;
            }
        }

        ParseResult build() {
            return ParseResult.builder()
                    .filePath(filePath)
                    .success(true)
                    .packageNode(packageNode)
                    .javaFileNode(fileNode)
                    .types(types)
                    .methods(methods)
                    .fields(fields)
                    .relations(relations)
                    .build();
        }
    }
}
