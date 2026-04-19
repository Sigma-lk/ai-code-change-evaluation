package com.sigma.ai.evaluation.types.exception;

import com.sigma.ai.evaluation.types.ErrorCode;

/**
 * 资源不存在异常（20xxxx），表示业务对象在系统中查不到。
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String detailMessage) {
        super(errorCode, detailMessage);
    }

    public static ResourceNotFoundException repositoryNotFound(String repoId) {
        return new ResourceNotFoundException(ErrorCode.REPOSITORY_NOT_FOUND,
                ErrorCode.REPOSITORY_NOT_FOUND.getMessage() + ": repoId=" + repoId);
    }

    public static ResourceNotFoundException gitBranchNotFound(String branch) {
        return new ResourceNotFoundException(ErrorCode.GIT_BRANCH_NOT_FOUND,
                ErrorCode.GIT_BRANCH_NOT_FOUND.getMessage() + ": branch=" + branch);
    }

    public static ResourceNotFoundException indexTaskNotFound(Long taskId) {
        return new ResourceNotFoundException(ErrorCode.INDEX_TASK_NOT_FOUND,
                ErrorCode.INDEX_TASK_NOT_FOUND.getMessage() + ": taskId=" + taskId);
    }

    public static ResourceNotFoundException folderCreateFail(String localPath) {
        return new ResourceNotFoundException(ErrorCode.FOLDER_CREATE_FAIL,
                ErrorCode.FOLDER_CREATE_FAIL.getMessage() + ": localPath=" + localPath);
    }

    /**
     * 仓库工作区内指定文件路径不存在，或不是普通文件。
     */
    public static ResourceNotFoundException repoFileNotFound(String resolvedPath) {
        return new ResourceNotFoundException(ErrorCode.REPO_FILE_NOT_FOUND,
                ErrorCode.REPO_FILE_NOT_FOUND.getMessage() + ": path=" + resolvedPath);
    }

    /**
     * 仓库已注册，但配置的本地克隆目录在主机上不可用。
     */
    public static ResourceNotFoundException repoLocalWorkspaceNotFound(String localPath) {
        return new ResourceNotFoundException(ErrorCode.REPO_LOCAL_WORKSPACE_NOT_FOUND,
                ErrorCode.REPO_LOCAL_WORKSPACE_NOT_FOUND.getMessage() + ": localPath=" + localPath);
    }
}
