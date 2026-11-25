package com.modvault.dal.mapper;

import com.modvault.dal.entity.UpstreamKey;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UpstreamKeyMapper {

    String COLUMNS = "id, upstream_id, key_ciphertext, key_masked, enabled, last_used_at, status, created_at";

    @Select("SELECT " + COLUMNS + " FROM upstream_keys WHERE id = #{id}")
    UpstreamKey findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM upstream_keys WHERE upstream_id = #{upstreamId} AND enabled = TRUE "
            + "ORDER BY created_at DESC LIMIT 1")
    UpstreamKey findEnabledByUpstreamId(@Param("upstreamId") String upstreamId);

    @Select("SELECT " + COLUMNS + " FROM upstream_keys WHERE upstream_id = #{upstreamId} ORDER BY created_at DESC LIMIT 1")
    UpstreamKey findLatestByUpstreamId(@Param("upstreamId") String upstreamId);

    @Insert("INSERT INTO upstream_keys (id, upstream_id, key_ciphertext, key_masked, enabled, status) "
            + "VALUES (#{id}, #{upstreamId}, #{keyCiphertext}, #{keyMasked}, #{enabled}, #{status})")
    int insert(UpstreamKey key);

    @Update("UPDATE upstream_keys SET enabled = FALSE WHERE upstream_id = #{upstreamId}")
    int disableAllForUpstream(@Param("upstreamId") String upstreamId);

    @Update("UPDATE upstream_keys SET enabled = #{enabled}, status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("enabled") boolean enabled, @Param("status") String status);

    @Update("UPDATE upstream_keys SET last_used_at = now() WHERE id = #{id}")
    int touchLastUsed(@Param("id") String id);
}
