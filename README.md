# AI 代码变更评估系统

基于代码知识图谱与向量语义检索的代码变更影响评估服务。系统通过解析 Java 源代码构建 AST 知识图谱，结合向量嵌入实现语义检索，支持对代码变更的影响面分析。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 运行时 | JDK 17、Spring Boot 3.2.5 |
| 知识图谱 | Neo4j（Cypher） |
| 向量存储 | Milvus |
| 关系数据库 | PostgreSQL + MyBatis |
| 消息队列 | Apache Kafka |
| 代码解析 | JavaParser |
| Git 操作 | JGit |
| 向量化 | 本地 Docker 部署的 `BAAI/bge-m3`（OpenAI 兼容 Embedding API） |

---

## 项目结构

项目采用 Maven 多模块布局，严格按层次划分职责：

```
ai-code-change-evaluation/
├── pom.xml                      # 父 POM，统一管理版本与依赖
├── evaluation-types/            # 枚举与跨模块常量
├── evaluation-api/              # 对外 DTO（请求/响应契约）
├── evaluation-domain/           # 领域服务、端口接口、领域模型
├── evaluation-infrastructure/   # 基础设施适配器实现
├── evaluation-trigger/          # 外部触达入口（HTTP / Kafka / 定时）
└── evaluation-app/              # 启动类、配置文件、SQL 映射
```

### 模块依赖关系

```
evaluation-types
    ↑
evaluation-api ──────────────── 依赖 types
    ↑
evaluation-domain ──────────── 依赖 types、api
    ↑
evaluation-infrastructure ──── 实现 domain 中的 Adapter/Port 接口
    ↑
evaluation-trigger ──────────── 依赖 domain、api
    ↑
evaluation-app ──────────────── 组装所有模块，提供启动入口
```

---

## 各模块职责详解

### `evaluation-types`

存放枚举与常量，无业务逻辑，可被任意模块引用。

| 类型 | 说明 |
|------|------|
| `FileChangeType` | 文件变更类型（ADD / MODIFY / DELETE） |
| `TaskType` | 索引任务类型（FULL / INCREMENTAL） |
| `TaskStatus` | 任务状态（PENDING / RUNNING / SUCCESS / FAILED） |
| `NodeType` / `RelationType` | 图谱节点与关系类型 |
| `TypeKind` | Java 类型分类（CLASS / INTERFACE / ENUM 等） |

---

### `evaluation-api`

对外暴露的数据契约层，仅包含 DTO，不含任何业务逻辑。

| DTO | 说明 |
|-----|------|
| `IndexTriggerRequest` / `IndexTriggerResponse` | 手动触发全量索引 |
| `ImpactAnalyzeRequest` / `ImpactAnalyzeResponse` | 影响面分析请求与结果 |
| `SemanticSearchRequest` / `SemanticSearchResponse` | 语义检索请求与结果 |

---

### `evaluation-domain`

系统核心，包含全部业务规则与领域服务。通过 **Adapter / Port 接口**隔离对外部系统的依赖，具体实现由 `evaluation-infrastructure` 注入。

#### 索引子域（`domain.index`）

负责 Java 源码的全量/增量索引流水线。

| 类 | 职责 |
|----|------|
| `FullIndexService` | 全量索引主编排：clone/pull 仓库 → 扫描 Java 文件 → checksum 幂等 → AST 解析 → 批量写图 → 异步 Embedding |
| `FileWalkerService` | 遍历本地 Java 文件，计算文件 checksum |
| `JavaAstParserService` | 使用 JavaParser 将 `.java` 文件解析为 `ParseResult`（节点 + 关系） |
| `IndexTaskPort` | 索引任务的持久化抽象接口 |
| `ParseErrorPort` | 解析失败记录的持久化抽象接口 |
| `IndexTask` | 索引任务领域模型 |

#### 代码图谱子域（`domain.codegraph`）

管理 Neo4j 中的代码知识图谱。

| 类 | 职责 |
|----|------|
| `CodeGraphService` | 批量写入解析结果到图谱，查询文件 checksum，写入仓库节点 |
| `GraphAdapter` | Neo4j 操作的领域端口：MERGE 节点/关系、删除节点、查询影响面、获取 checksum |
| `ParseResult` / `*Node` / `GraphRelation` | AST 解析结果及图谱节点/关系模型 |

#### 向量嵌入子域（`domain.embedding`）

管理代码片段的向量化与语义检索。

| 类 | 职责 |
|----|------|
| `EmbeddingService` | 提交异步向量化任务的领域接口 |
| `EmbeddingSubmitter` | 异步执行器（`@Async`），调用 `EmbeddingStoreAdapter` 完成向量写入 |
| `EmbeddingStoreAdapter` | 向量存储端口：upsert / batch upsert / delete / 语义检索 |

#### 影响面子域（`domain.impact`）

基于知识图谱进行代码变更影响面分析。

| 类 | 职责 |
|----|------|
| `ImpactAnalysisService` | 接收 `ImpactQuery`，委托 `GraphAdapter` 查询调用链与子类型链，返回 `ImpactResult` |
| `ImpactQuery` / `ImpactResult` | 查询与结果领域模型 |

#### 仓库/Git 子域（`domain.repository`）

管理 Git 仓库元数据与提交记录。

| 类 | 职责 |
|----|------|
| `GitAdapter` | Git 操作端口：clone / pull / diff 提交 |
| `RepositoryPort` | 仓库注册信息持久化接口 |
| `CommitRecordPort` | 提交幂等记录持久化接口 |
| `RepositoryInfo` / `ChangedFile` | 仓库与变更文件领域模型 |

---

### `evaluation-infrastructure`

实现 domain 层定义的所有 Adapter / Port 接口，直接访问外部资源。

| 实现类 | 实现接口 | 外部资源 |
|--------|----------|----------|
| `JGitAdapterImpl` | `GitAdapter` | 本地文件系统 + Git 远端 |
| `Neo4jGraphAdapterImpl` | `GraphAdapter` | Neo4j（Cypher/Driver） |
| `MilvusEmbeddingAdapterImpl` | `EmbeddingStoreAdapter` | Milvus 向量数据库 |
| `RepositoryPortImpl` | `RepositoryPort` | PostgreSQL（MyBatis） |
| `IndexTaskPortImpl` | `IndexTaskPort` | PostgreSQL（MyBatis） |
| `CommitRecordPortImpl` | `CommitRecordPort` | PostgreSQL（MyBatis） |
| `ParseErrorPortImpl` | `ParseErrorPort` | PostgreSQL（MyBatis） |
| `EmbeddingApiClient` | — | 本地 `bge-m3` Embedding REST API |

---

### `evaluation-trigger`

系统的所有外部触达入口，不含业务逻辑，只做参数校验与协议转换后委托 domain 层处理。

#### HTTP Controller

| Controller | 路径前缀 | 接口 |
|------------|----------|------|
| `IndexController` | `/api/v1/index` | `POST /trigger` — 手动触发全量索引 |
| `ImpactController` | `/api/v1` | `POST /impact/analyze` — 影响面分析 |
| | | `POST /search/semantic` — 语义检索 |

#### Kafka 消费者

| 类 | Topic | 消费组 | 说明 |
|----|-------|--------|------|
| `IndexConsumer` | `code-change-event` | `index-service-group` | 监听代码提交事件，执行增量索引 |

#### 定时任务

| 类 | cron 配置项 | 说明 |
|----|------------|------|
| `IndexScheduler` | `index.scheduler.cron` | 定期遍历所有 ACTIVE 仓库执行全量索引 |

---

### `evaluation-app`

Spring Boot 启动模块，负责应用装配。

- **启动类**：`EvaluationApplication`（扫描 `com.sigma.ai.evaluation`，启用调度、配置属性）
- **配置文件**：`application.yml`
- **数据库初始化**：`db/init.sql`（`t_repository`、`t_index_task`、`t_commit_record`、`t_parse_error`）
- **MyBatis XML**：`mapper/*.xml`
- **Neo4j 约束**：`neo4j/constraints.cypher`

---

## 外部触达方式与请求链条

### A. 手动触发全量索引（HTTP）

```
POST /api/v1/index/trigger
  { "repoId": "..." }
```

```
IndexController.triggerIndex（虚拟线程异步提交）
  └─ FullIndexService.runFullIndex(repoId)
       ├─ RepositoryPort.findById            → PostgreSQL（读取仓库元数据）
       ├─ IndexTaskPort.createTask           → PostgreSQL（创建任务记录）
       ├─ GitAdapter.cloneOrPull             → JGit（拉取/更新本地仓库）
       ├─ FileWalkerService.walkJavaFiles    → 本地文件系统
       ├─ CodeGraphService.getFileChecksum  → Neo4j（幂等：跳过未变更文件）
       ├─ JavaAstParserService.parse        → JavaParser（生成 ParseResult）
       ├─ CodeGraphService.batchWriteParseResults → Neo4j（MERGE 节点与关系）
       ├─ EmbeddingService.submitAsync（异步）
       │    └─ EmbeddingStoreAdapter.upsertEmbedding
       │         ├─ EmbeddingApiClient      → 本地 bge-m3 Embedding API（HTTP）
       │         └─ MilvusEmbeddingAdapterImpl → Milvus（写入向量）
       └─ IndexTaskPort.updateTaskStatus    → PostgreSQL（更新任务状态）
```

### B. 定时全量索引

```
IndexScheduler（按 cron 触发）
  └─ RepositoryPort.findAllActive     → PostgreSQL（获取所有活跃仓库）
       └─ 对每个仓库执行 FullIndexService.runFullIndex（同 A）
```

### C. Kafka 增量索引

```
Kafka Topic: code-change-event
  └─ IndexConsumer.onCommitEvent（手动 ACK）
       └─ IncrementalIndexOrchestrator.run（与 Webhook 共用）
            ├─ CommitRecordPort.isProcessed     → PostgreSQL（幂等检查）
            ├─ RepositoryPort.findById          → PostgreSQL
            ├─ IndexTaskPort.createTask         → PostgreSQL
            ├─ GitAdapter.cloneOrPull + fetch   → JGit
            ├─ GitAdapter.diffCommits / diffCommitAgainstFirstParent → JGit（变更 Java 文件）
            ├─ GitAdapter.diffLineStats         → JGit（行级增删统计）
            ├─ 按 FileChangeType 分支处理：
            │   ├─ DELETE：删 Milvus 向量 + Neo4j 文件子图
            │   └─ ADD / MODIFY：清旧边 → JavaAstParserService.parse → Neo4j 批量写入
            ├─ GraphAdapter.batchMergeCommitNodes → Neo4j
            ├─ CommitRecordPort.markProcessed   → PostgreSQL
            └─ IndexTaskPort.updateTaskStatus  → PostgreSQL
```

### C2. GitHub Push Webhook（同步增量 + 变更证据 + Dify）

```
POST /api/v1/webhooks/github
  Header: X-GitHub-Event: push, X-Hub-Signature-256: sha256=...
  Body: GitHub push JSON

  └─ GithubPushWebhookService
       ├─ HMAC-SHA256 验签（github.webhook.secret，未配置则跳过并 WARN）
       ├─ RepositoryPort.findActiveByCloneUrl（与 repository.clone_url / ssh_url 对齐 repoId）
       ├─ IncrementalIndexOrchestrator.run（同步写 Neo4j，不经 Kafka）
       ├─ ChangeEvidenceAssembler（ParseResult 节点 + 与行号相关的 unified diff 片段，不做图多跳搜索）
       └─ DifyWorkflowClient → POST {dify.workflow.base-url}/v1/workflows/run（dify.workflow.enabled=true 时）
```

环境变量建议：`GITHUB_WEBHOOK_SECRET`、`DIFY_API_KEY`、可选 `DIFY_BASE_URL`。Kafka 消息体 `CommitEvent` 的包名已改为 `com.sigma.ai.evaluation.domain.index.model`，与 `spring.kafka.consumer.properties.spring.json.trusted.packages` 一致。

### D. 影响面分析（HTTP）

```
POST /api/v1/impact/analyze
  { "methodId": "...", ... }
```

```
ImpactController.analyze
  └─ ImpactAnalysisService.analyze(ImpactQuery)
       ├─ GraphAdapter.findCallerMethodIds       → Neo4j（调用链上溯）
       └─ GraphAdapter.findSubTypeQualifiedNames → Neo4j（子类/实现类）
```

### E. 语义检索（HTTP）

```
POST /api/v1/search/semantic
  { "query": "...", "topK": 10 }
```

```
ImpactController.semanticSearch
  └─ EmbeddingStoreAdapter.semanticSearch
       ├─ EmbeddingApiClient       → 本地 bge-m3 Embedding API（将查询文本向量化）
       └─ MilvusEmbeddingAdapterImpl → Milvus（ANN 相似度检索）
```

---

## 数据库表说明

| 表名 | 用途 |
|------|------|
| `t_repository` | 仓库注册信息（URL、本地路径、状态等） |
| `t_index_task` | 索引任务记录（类型、状态、开始/结束时间） |
| `t_commit_record` | 已处理的 Commit 幂等记录 |
| `t_parse_error` | AST 解析失败的文件与错误信息 |

---

## 关键配置项

```yaml
server:
  port: 8080

spring:
  datasource:          # PostgreSQL 连接
  neo4j:               # Bolt 连接（知识图谱）
  kafka:
    bootstrap-servers: # Kafka 地址
    consumer:
      group-id: index-service-group

milvus:
  host: ...
  port: ...

embedding:
  url: ...             # 本地 Embedding API 地址，如 http://localhost:8081/v1/embeddings
  api-key: ${EMBEDDING_API_KEY:}
  model: BAAI/bge-m3
  batch-size: ...
  dimension: 1024

kafka:
  topic:
    code-change-event: code-change-event

github:
  webhook:
    secret: ${GITHUB_WEBHOOK_SECRET:}

dify:
  workflow:
    enabled: false
    base-url: ${DIFY_BASE_URL:https://api.dify.ai}
    api-key: ${DIFY_API_KEY:}
    input-key: change_payload

index:
  scheduler:
    cron: "0 0 2 * * ?"   # 默认每天凌晨 2 点全量索引
```

TVbF4gU9ch7txWy4