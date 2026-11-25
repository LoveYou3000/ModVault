package com.modvault.service;

import com.modvault.common.Ids;
import com.modvault.dal.entity.UsageRecord;
import com.modvault.dal.mapper.UsageRecordMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Usage recording (metadata only) and listing.
 */
@Service
public class UsageService {

    private final UsageRecordMapper mapper;

    public UsageService(UsageRecordMapper mapper) {
        this.mapper = mapper;
    }

    public void record(String requestId, String apiKeyId, String anthropicModel, String upstreamId,
                       String upstreamModel, String status, Integer inputTokens, Integer outputTokens,
                       boolean stream, Long latencyMs, Long firstTokenMs, String errorType) {
        UsageRecord record = new UsageRecord();
        record.setId(Ids.uuid());
        record.setRequestId(requestId);
        record.setApiKeyId(apiKeyId);
        record.setAnthropicModel(anthropicModel);
        record.setUpstreamId(upstreamId);
        record.setUpstreamModel(upstreamModel);
        record.setStatus(status);
        record.setInputTokens(inputTokens);
        record.setOutputTokens(outputTokens);
        record.setStream(stream);
        record.setLatencyMs(latencyMs);
        record.setFirstTokenMs(firstTokenMs);
        record.setErrorType(errorType);
        mapper.insert(record);
    }

    public List<UsageRecord> findPage(int limit, int offset) {
        return mapper.findPage(limit, offset);
    }

    public long count() {
        return mapper.count();
    }

    public void cleanup(int retentionDays) {
        mapper.deleteOlderThan(retentionDays);
    }
}
