package com.sigma.ai.evaluation.infrastructure.pg.adapter;

import com.sigma.ai.evaluation.domain.index.adapter.ParseErrorPort;
import com.sigma.ai.evaluation.infrastructure.pg.mapper.ParseErrorMapper;
import com.sigma.ai.evaluation.infrastructure.pg.po.ParseErrorPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * {@link ParseErrorPort} 的 PostgreSQL 实现。
 */
@Component
@RequiredArgsConstructor
public class ParseErrorPortImpl implements ParseErrorPort {

    private final ParseErrorMapper parseErrorMapper;

    @Override
    public void record(Long taskId, String filePath, String errorType, String errorMsg) {
        ParseErrorPO po = new ParseErrorPO();
        po.setTaskId(taskId);
        po.setFilePath(filePath);
        po.setErrorType(errorType);
        po.setErrorMsg(errorMsg);
        po.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        parseErrorMapper.insert(po);
    }
}
