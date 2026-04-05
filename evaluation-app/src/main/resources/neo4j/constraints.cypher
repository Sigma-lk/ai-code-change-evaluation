// ====================================================================
// Neo4j 约束与索引初始化脚本（在应用首次启动前手动执行）
// 兼容 Neo4j 5.x 语法
// ====================================================================

// 唯一约束
CREATE CONSTRAINT type_qualified_name_unique IF NOT EXISTS
    FOR (n:Type) REQUIRE n.qualifiedName IS UNIQUE;

CREATE CONSTRAINT method_id_unique IF NOT EXISTS
    FOR (n:Method) REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT field_id_unique IF NOT EXISTS
    FOR (n:Field) REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT java_file_path_unique IF NOT EXISTS
    FOR (n:JavaFile) REQUIRE n.path IS UNIQUE;

CREATE CONSTRAINT commit_hash_unique IF NOT EXISTS
    FOR (n:Commit) REQUIRE n.hash IS UNIQUE;

CREATE CONSTRAINT repository_id_unique IF NOT EXISTS
    FOR (n:Repository) REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT module_id_unique IF NOT EXISTS
    FOR (n:Module) REQUIRE n.id IS UNIQUE;

CREATE CONSTRAINT package_id_unique IF NOT EXISTS
    FOR (n:Package) REQUIRE n.id IS UNIQUE;

// 查询优化索引
CREATE INDEX type_simple_name_index IF NOT EXISTS FOR (n:Type) ON (n.simpleName);
CREATE INDEX method_simple_name_index IF NOT EXISTS FOR (n:Method) ON (n.simpleName);
CREATE INDEX java_file_checksum_index IF NOT EXISTS FOR (n:JavaFile) ON (n.checksum);
CREATE INDEX type_repo_index IF NOT EXISTS FOR (n:Type) ON (n.filePath);
