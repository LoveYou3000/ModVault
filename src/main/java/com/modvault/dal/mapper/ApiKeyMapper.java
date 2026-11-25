package com.modvault.dal.mapper;

import com.modvault.dal.entity.ApiKey;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ApiKeyMapper {

    String COLUMNS = "id, name, key_hash, key_prefix, enabled, expires_at, last_used_at, created_at";

    @Select("SELECT " + COLUMNS + " FROM api_keys ORDER BY created_at")
    List<ApiKey> findAll();

    @Select("SELECT " + COLUMNS + " FROM api_keys WHERE key_hash = #{keyHash}")
    ApiKey findByHash(@Param("keyHash") String keyHash);

    @Select("SELECT " + COLUMNS + " FROM api_keys WHERE id = #{id}")
    ApiKey findById(@Param("id") UUID id);

    @Insert("INSERT INTO api_keys (id, name, key_hash, key_prefix, enabled, expires_at) "
            + "VALUES (#{id}, #{name}, #{keyHash}, #{keyPrefix}, #{enabled}, #{expiresAt})")
    int insert(ApiKey apiKey);

    @Update("UPDATE api_keys SET enabled = FALSE WHERE id = #{id}")
    int revoke(@Param("id") UUID id);

    @Update("UPDATE api_keys SET last_used_at = now() WHERE id = #{id}")
    int touchLastUsed(@Param("id") UUID id);

    @Delete("DELETE FROM api_keys WHERE id = #{id}")
    int delete(@Param("id") UUID id);
}
