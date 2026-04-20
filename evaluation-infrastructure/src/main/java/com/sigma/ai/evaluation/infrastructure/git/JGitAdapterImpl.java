package com.sigma.ai.evaluation.infrastructure.git;

import com.sigma.ai.evaluation.domain.repository.adapter.GitAdapter;
import com.sigma.ai.evaluation.domain.repository.model.ChangedFile;
import com.sigma.ai.evaluation.domain.repository.model.DiffLineStats;
import com.sigma.ai.evaluation.types.FileChangeType;
import com.sigma.ai.evaluation.types.exception.GitOperationException;
import com.sigma.ai.evaluation.types.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link GitAdapter} 的 JGit 实现，封装 clone/pull/diff 操作。
 */
@Slf4j
@Component
public class JGitAdapterImpl implements GitAdapter {

    @Override
    public void cloneOrPull(String cloneUrl, String branch, String localPath) {
        File localDir = new File(localPath);
        File gitDir = new File(localPath, ".git");

        if (gitDir.exists()) {
            try {
                log.info("本地仓库已存在，执行 git pull: {}", localPath);
                try (Git git = Git.open(localDir)) {
                    git.pull()
                        .setRemoteBranchName(branch)
                        .call();
                    log.info("git pull 完成: {}, branch={}", localPath, branch);
                }
            } catch (IOException | GitAPIException e) {
                log.error("git pull 失败: url={}, localPath={}", cloneUrl, localPath, e);
                throw GitOperationException.pullFailed(e);
            }
        } else {
            try {
                log.info("开始 git clone: url={}, branch={}, localPath={}", cloneUrl, branch, localPath);
                boolean flag = localDir.mkdirs();
                if (!flag) {
                    throw ResourceNotFoundException.folderCreateFail(localPath);
                }
                try (Git git = Git.cloneRepository()
                        .setURI(cloneUrl)
                        .setBranch(branch)
                        .setDirectory(localDir)
                        .call()) {
                    log.info("git clone 完成: {}", localPath);
                }
            }
            catch (ResourceNotFoundException e) {
                log.error("文件夹创建失败: localPath={}", localPath, e);
                throw e;
            }
            catch (GitAPIException e) {
                log.error("git clone 失败: url={}, localPath={}", cloneUrl, localPath, e);
                throw GitOperationException.cloneFailed(e);
            }
        }
    }

    @Override
    public void fetch(String localPath) {
        File localDir = new File(localPath);
        try (Git git = Git.open(localDir)) {
            git.fetch().call();
            log.info("git fetch 完成: {}", localPath);
        } catch (IOException | GitAPIException e) {
            log.error("git fetch 失败: localPath={}", localPath, e);
            throw GitOperationException.fetchFailed(e);
        }
    }

    @Override
    public DiffLineStats diffLineStats(String localPath, String oldCommit, String newCommit) {
        int insertions = 0;
        int deletions = 0;
        int javaFiles = 0;
        File localDir = new File(localPath);

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(localDir, ".git"))
                .build();
             Git git = new Git(repository)) {

            ObjectId oldId = repository.resolve(oldCommit + "^{tree}");
            ObjectId newId = repository.resolve(newCommit + "^{tree}");
            if (oldId == null || newId == null) {
                log.warn("diffLineStats 无法解析提交: oldCommit={}, newCommit={}", oldCommit, newCommit);
                return DiffLineStats.builder().javaFilesTouched(0).totalInsertions(0).totalDeletions(0).build();
            }

            AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, oldId);
            AbstractTreeIterator newTreeParser = prepareTreeParser(repository, newId);
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call();

            try (DiffFormatter diffFormatter = new DiffFormatter(OutputStream.nullOutputStream())) {
                diffFormatter.setRepository(repository);
                diffFormatter.setContext(0);
                for (DiffEntry entry : diffs) {
                    String path = entry.getChangeType() == DiffEntry.ChangeType.DELETE
                            ? entry.getOldPath() : entry.getNewPath();
                    if (path == null || !path.endsWith(".java")) {
                        continue;
                    }
                    javaFiles++;
                    try {
                        FileHeader fh = diffFormatter.toFileHeader(entry);
                        for (Edit e : fh.toEditList()) {
                            insertions += e.getEndB() - e.getBeginB();
                            deletions += e.getEndA() - e.getBeginA();
                        }
                    } catch (Exception ex) {
                        log.warn("diffLineStats 跳过条目（可能为二进制或过大）: path={}, msg={}", path, ex.getMessage());
                    }
                }
            }
        } catch (IOException | GitAPIException e) {
            log.error("diffLineStats 失败: localPath={}, old={}, new={}", localPath, oldCommit, newCommit, e);
            throw GitOperationException.diffFailed(e);
        }

        return DiffLineStats.builder()
                .javaFilesTouched(javaFiles)
                .totalInsertions(insertions)
                .totalDeletions(deletions)
                .build();
    }

    @Override
    public String unifiedDiffForJavaFile(String localPath, String oldCommit, String newCommit, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "";
        }
        String want = relativePath.replace('\\', '/').replaceFirst("^/+", "");
        File localDir = new File(localPath);

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(localDir, ".git"))
                .build();
             Git git = new Git(repository)) {

            ObjectId oldId = repository.resolve(oldCommit + "^{tree}");
            ObjectId newId = repository.resolve(newCommit + "^{tree}");
            if (oldId == null || newId == null) {
                log.warn("unifiedDiff 无法解析提交: old={}, new={}", oldCommit, newCommit);
                return "";
            }

            AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, oldId);
            AbstractTreeIterator newTreeParser = prepareTreeParser(repository, newId);
            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call();

            for (DiffEntry diff : diffs) {
                String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? diff.getOldPath()
                        : diff.getNewPath();
                if (path == null || DiffEntry.DEV_NULL.equals(path)) {
                    continue;
                }
                String norm = path.replace('\\', '/');
                if (!want.equals(norm)) {
                    continue;
                }
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                     DiffFormatter diffFormatter = new DiffFormatter(baos)) {
                    diffFormatter.setRepository(repository);
                    diffFormatter.setContext(3);
                    diffFormatter.format(diff);
                    return baos.toString(StandardCharsets.UTF_8);
                }
            }
        } catch (IOException | GitAPIException e) {
            log.error("unifiedDiffForJavaFile 失败: localPath={}, file={}", localPath, relativePath, e);
            throw GitOperationException.diffFailed(e);
        }
        return "";
    }

    @Override
    public List<ChangedFile> diffCommits(String localPath, String oldCommit, String newCommit) {
        List<ChangedFile> changedFiles = new ArrayList<>();
        File localDir = new File(localPath);

        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(localDir, ".git"))
                .build();
             Git git = new Git(repository)) {

            ObjectId oldId = repository.resolve(oldCommit + "^{tree}");
            ObjectId newId = repository.resolve(newCommit + "^{tree}");

            if (oldId == null || newId == null) {
                log.warn("无法解析 commit hash: oldCommit={}, newCommit={}", oldCommit, newCommit);
                return changedFiles;
            }

            AbstractTreeIterator oldTreeParser = prepareTreeParser(repository, oldId);
            AbstractTreeIterator newTreeParser = prepareTreeParser(repository, newId);

            List<DiffEntry> diffs = git.diff()
                    .setOldTree(oldTreeParser)
                    .setNewTree(newTreeParser)
                    .call();

            for (DiffEntry diff : diffs) {
                // 只处理 .java 文件
                String path = diff.getChangeType() == DiffEntry.ChangeType.DELETE
                        ? diff.getOldPath() : diff.getNewPath();
                if (!path.endsWith(".java")) continue;

                FileChangeType changeType = switch (diff.getChangeType()) {
                    case ADD -> FileChangeType.ADDED;
                    case MODIFY, RENAME, COPY -> FileChangeType.MODIFIED;
                    case DELETE -> FileChangeType.DELETED;
                };

                changedFiles.add(ChangedFile.builder()
                        .relativePath(path)
                        .absolutePath(localPath + File.separator + path)
                        .changeType(changeType)
                        .build());
            }

            log.info("git diff 完成: {} 个变更 Java 文件, old={}, new={}", changedFiles.size(), oldCommit, newCommit);

        } catch (IOException | GitAPIException e) {
            log.error("git diff 失败: localPath={}, old={}, new={}", localPath, oldCommit, newCommit, e);
            throw GitOperationException.diffFailed(e);
        }

        return changedFiles;
    }

    @Override
    public List<ChangedFile> diffCommitAgainstFirstParent(String localPath, String commitHash) {
        String parent = commitHash + "^";
        return diffCommits(localPath, parent, commitHash);
    }

    @Override
    public String getHeadCommitHash(String localPath, String branch) {
        File localDir = new File(localPath);
        try (Repository repository = new FileRepositoryBuilder()
                .setGitDir(new File(localDir, ".git"))
                .build()) {
            ObjectId headId = repository.resolve("refs/heads/" + branch);
            if (headId == null) {
                log.warn("分支不存在: {}", branch);
                throw ResourceNotFoundException.gitBranchNotFound(branch);
            }
            return headId.getName();
        } catch (IOException e) {
            log.error("获取 HEAD commit hash 失败: localPath={}, branch={}", localPath, branch, e);
            throw GitOperationException.headHashFailed(e);
        }
    }

    /**
     * 将 objectId（已解析为 tree 对象）包装为 TreeParser，供 diff 使用。
     * objectId 必须是通过 {@code resolve("commitHash^{tree}")} 得到的 tree 对象。
     */
    private AbstractTreeIterator prepareTreeParser(Repository repository, ObjectId objectId) throws IOException {
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (var reader = repository.newObjectReader()) {
            treeParser.reset(reader, objectId);
        }
        return treeParser;
    }
}
