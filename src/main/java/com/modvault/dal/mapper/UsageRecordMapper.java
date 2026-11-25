package com.modvault.dal.mapper;

import com.modvault.dal.entity.UsageRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UsageRecordMapper {

    String COLUMNS = "id, request_id, api_key_id, anthropic_model, upstream_id, upstream_model, status, "
            + "input_tokens, output_tokens, stream, latency_ms, first_token_ms, error_type, created_at";

    @Insert("INSERT INTO usage_records "
            + "(id, request_id, api_key_id, anthropic_model, upstream_id, upstream_model, status, "
            + "input_tokens, output_tokens, stream, latency_ms, first_token_ms, error_type) "
            + "VALUES (#{id}, #{requestId}, #{apiKeyId}, #{anthropicModel}, #{upstreamId}, #{upstreamModel}, "
            + "#{status}, #{inputTokens}, #{outputTokens}, #{stream}, #{latencyMs}, #{firstTokenMs}, #{errorType})")
    int insert(UsageRecord record);

    @Select("SELECT " + COLUMNS + " FROM usage_records ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}")
    List<UsageRecord> findPage(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM usage_records")
    long count();

    @Delete("DELETE FROM usage_records WHERE created_at < (now() - (#{days} * INTERVAL '1 day'))")
    int deleteOlderThan(@Param("days") int days);
}
