# Java AST 解析与实体节点识别

本文说明本项目中如何通过 **JavaParser** 与 **Symbol Solver** 从单个 `.java` 源文件识别**代码图谱实体**（类型、方法、字段等）及其**稳定业务键**，便于与 `图谱数据结构设计.md`、`主体流程.md` 中的 Neo4j 模型与 `ChangeEvidenceDocument.nodes` 对照阅读。

实现入口：`evaluation-domain` 模块中的 `JavaAstParserServiceImpl`（`com.sigma.ai.evaluation.domain.index.service.impl`）。

---

## 一、技术栈与总体流程

| 组件 | 作用 |
|------|------|
| **JavaParser** | 将源文件解析为 `CompilationUnit`（AST 根），遍历 `TypeDeclaration`、`MethodDeclaration`、`FieldDeclaration`、`MethodCallExpr` 等节点。 |
| **ParserConfiguration** | 语言级别固定为 **JAVA_17**；注册 `JavaSymbolSolver`，使 AST 节点可调用 `resolve()` 做符号绑定。 |
| **CombinedTypeSolver** | 组合多类「类型来源」：反射求解器（仅核心 JDK，不加载完整反射树）+ 各 `sourceRoots` 下的 `JavaParserTypeSolver`（源码目录，通常为 `**/src/main/java`）。 |
| **ParseContext** | 单次解析的内存上下文：累积 `TypeNode` / `MethodNode` / `FieldNode` / `GraphRelation`，最后组装为 `ParseResult`。 |

**高层步骤**

1. 根据仓库本地路径扫描出的 `sourceRoots` 构建 `CombinedTypeSolver`。  
2. 使用带符号解析器的 `JavaParser` 解析文件 → 得到 `CompilationUnit`。  
3. 读取 `package`（可无，视为默认包）、建立 `PackageNode` 与 `JavaFileNode`，处理 `import` 生成 `IMPORTS` 关系。  
4. 遍历 `cu.getTypes()` 中每个顶层 `TypeDeclaration`，**递归**处理成员中的嵌套类型。  
5. 在每个类型上处理字段、方法、构造器；对方法/构造器体做 visitor，抽取可调用的 `CALLS` 边。  
6. 输出 `ParseResult`（成功）或带 `errorMessage` 的失败结果（语法错误、IO、抽取异常）。

**设计取舍（源码注释）**：优先解析到**全限定名**；若类型/调用落在 **sourceRoots 未覆盖的外部依赖**上，`resolve()` 失败则**跳过该关系或该调用边**，避免因缺少完整 classpath 而中断整个文件的解析。

---

## 二、类型（Type）实体如何识别

### 2.1 AST 来源

- 顶层：`CompilationUnit.getTypes()` 中的 `ClassOrInterfaceDeclaration`、`EnumDeclaration`、`AnnotationDeclaration` 等均实现 `TypeDeclaration<?>`。  
- 嵌套：某类型的 `getMembers()` 中仍为 `TypeDeclaration` 的成员，**递归**调用 `processType(inner, pkgName, outerQualifiedName)`。

### 2.2 全限定名 `qualifiedName` 的拼接规则

记 `pkgName` 为 `package` 声明的包名（无 package 则为空串 `""`），`outerQualifiedName` 为外层类型的全限定名（顶层时为 `null`）。

| 场景 | 规则 |
|------|------|
| **顶层类型** | `pkgName` 非空：`qualifiedName = pkgName + "." + simpleName`；否则仅为 `simpleName`（默认包）。 |
| **嵌套类型** | `qualifiedName = outerQualifiedName + "." + simpleName`（与 Java 语言嵌套类命名一致）。 |

该字符串写入 `TypeNode.qualifiedName`，并作为图中 **Type 节点的业务主键**（与 Neo4j 中按 `qualifiedName` MERGE 的策略一致）。

### 2.3 `TypeKind` 判定

根据 `TypeDeclaration` 的具体子类型映射为 `CLASS` / `INTERFACE` / `ENUM` / `ANNOTATION`；类上的 `abstract` / `final` / `static`（如静态内部类）从 AST 读取。

### 2.4 行号与文件

- `lineStart` / `lineEnd`：取该类型声明在源码中的 `Range`（无范围时为 0）。  
- `filePath`：当前解析文件的**绝对路径**字符串。

### 2.5 继承与实现

对 `ClassOrInterfaceDeclaration`：遍历 `getExtendedTypes()`、`getImplementedTypes()`，对每个 `ClassOrInterfaceType` 尝试 `resolve().describe()` 得到全限定名；失败则退化为 `getNameAsString()`（可能非 FQN），并建立 `EXTENDS` / `IMPLEMENTS` 关系边。

---

## 三、字段（Field）实体如何识别

### 3.1 AST 来源

在每个 `TypeDeclaration` 上遍历 `getFields()`，元素类型为 `FieldDeclaration`（可一次声明多个变量 `int a, b`）。

### 3.2 业务主键 `FieldNode.id`

对 `FieldDeclaration` 中每个 `VariableDeclarator`：

```text
id = ownerQualifiedName + "#" + variableName
```

其中 `ownerQualifiedName` 为**当前正在处理的类型**的全限定名（含嵌套类），`variableName` 为 `vd.getNameAsString()`。

**示例**：类 `com.example.UserService` 中字段 `userRepository` → id 为 `com.example.UserService#userRepository`。

### 3.3 类型信息与 DEPENDS_ON

- `typeName`：元素类型在源码中的 `asString()`（可能含泛型字面量）。  
- `typeQualifiedName`：对 `fd.getElementType().resolve().describe()` **尝试**解析；失败则为 `null`。  
- 若解析得到非空且**非「类原语式」**（见实现中的 `isPrimitiveLike`：基本类型、`String`、包装类等），则从**所属类型**向被引用类型连一条 `DEPENDS_ON`，表示结构依赖。

### 3.4 行号

`lineNo` 取整个 `FieldDeclaration` 的 `Range.begin.line`（同一声明多变量时共享一行号）。

---

## 四、方法（Method）与构造器如何识别

### 4.1 普通方法 `MethodDeclaration`

**业务主键 `MethodNode.id`** 由所属类型全限定名、方法简名与**参数类型列表（简化形式）**拼接：

```text
id = ownerQualifiedName + "#" + methodName + "(" + param1Simple + "," + param2Simple + ")"
```

- `param*Simple`：对每个参数 `Parameter`，取 `p.getType().asString()` 后经 `stripToSimpleName`：**去掉泛型实参尖括号内内容**、去掉 `[]`、再取**最后一段包名之后的简单类名**（用于统一 ID 形态，避免过长 FQN 进入括号）。  
- **示例**：`com.example.UserService#findById(Long)`。

其它字段：`signature` 使用 `md.getDeclarationAsString(false, false, true)`（含修饰符与返回类型的声明字符串）、`returnType`、`accessModifier`、静态/抽象、`lineStart`/`lineEnd` 等。

### 4.2 构造器 `ConstructorDeclaration`

视为一种特殊方法节点：

- `simpleName` 固定为 `"<init>"`。  
- **id 规则**：`ownerQualifiedName + "#<init>(" + paramSimpleList + ")"`。  
- `isConstructor = true`，`returnType` 在模型中写为 `"void"`（便于统一展示，语义上仍表示构造器）。

### 4.3 与图中 `CALLS` 目标 ID 的一致性

方法体内对每个 `MethodCallExpr` 尝试：

1. `n.resolve()` → `ResolvedMethodDeclaration`。  
2. `buildCalleeIdFromResolved`：声明类型 `getQualifiedName()` + `#` + 方法名 + `(` + 各参数 `describeType()` 再经 `stripToSimpleName` + `...` + `)`。  
3. 解析失败（外部库、不完整符号表等）则**不生成**该条 `CALLS`（实现中捕获异常；若日志级别为 error 可能打栈，但不影响继续遍历其它调用）。

因此：**调用边两端的方法 id 与声明侧生成的 id 使用同一套拼接规则**，前提是 callee 能被 Symbol Solver 解析到声明。

---

## 五、包与 Java 文件节点

| 实体 | 主键字段 | 规则 |
|------|----------|------|
| **PackageNode** | `id` | `repoId + ":" + packageQualifiedName`（默认包时 `pkgName` 为空，注意与空包语义一致）。 |
| **JavaFileNode** | `path` | 文件绝对路径；并计算 MD5 `checksum`、行数 `lineCount`、最后修改时间。 |

关系上：`Package` → `CONTAINS_FILE` → `JavaFile`；`JavaFile` → `DEFINES_TYPE` → `Type`。

---

## 六、`import` 与类型引用

- 非 `*`、非 `static` 的 `ImportDeclaration`：从文件到被 import 的**类型全名**建立 `IMPORTS` 边（目标键为 `Type.qualifiedName`）。  
- 通配符 import 与 static import **不**在此处展开为边（避免爆炸式边集）。

---

## 七、与变更证据、风险种子的对齐

增量索引成功后，`ChangeEvidenceAssembler` 将 `ParseResult` 转为 `ChangeEvidenceDocument.nodes`：

| 证据 `kind` | 对应 `NodeEvidence.qualifiedName` | 图中对齐 |
|-------------|-------------------------------------|----------|
| `TYPE` | `TypeNode.qualifiedName` | `Type(qualifiedName)` |
| `METHOD` | `MethodNode.id` | `Method(id)` |
| `FIELD` | `FieldNode.id` | `Field(id)` |

风险传播 API 的种子字段与上表一致：**METHOD / FIELD 的 `qualifiedName` 字段承载的是上述 id**，**TYPE** 则为类型全限定名（见 `主体流程.md` 与 `Neo4jRiskPropagationExpander`）。

---

## 八、局限与边界情况（阅读图谱时需注意）

1. **无完整 Maven/Gradle classpath**：外部三方类上的方法调用、字段类型往往无法 resolve，对应 **CALLS / DEPENDS_ON 可能缺失**。  
2. **方法重载**：以「简名参数类型列表」区分；若 `stripToSimpleName` 后不同重载在某种边界下字符串碰撞，理论上存在歧义风险（与 Java 语言层重载解析一致依赖解析器）。  
3. **泛型擦除与简化**：参数在 id 中多为简单名，复杂泛型签名被截断，**与 JVM 签名不完全等价**。  
4. **解析失败文件**：不产生 `ParseResult` 中的 types/methods/fields，或 `success=false`；该文件在增量路径上可能仍有 `changedFiles` 记录，但 `nodes` 中无对应 AST 条目。  
5. **日志**：`MethodCallExpr` resolve 失败时当前实现会 `log.error`；生产上若噪音大可改为 `debug`/`warn`（属运维调优，不改变识别规则）。

---

## 九、源码索引

| 内容 | 路径 |
|------|------|
| AST 解析与 visitor | `evaluation-domain/.../JavaAstParserServiceImpl.java` |
| 节点 DTO | `evaluation-domain/.../codegraph/model/TypeNode.java`、`MethodNode.java`、`FieldNode.java` |
| 解析结果容器 | `evaluation-domain/.../codegraph/model/ParseResult.java` |
| 服务接口 | `evaluation-domain/.../index/service/JavaAstParserService.java` |

若需了解解析结果如何写入 Neo4j（MERGE、关系类型枚举），可继续阅读 `CodeGraphService` 及 `evaluation-infrastructure` 中的图适配实现，与 `图谱构建.md` 交叉对照。
