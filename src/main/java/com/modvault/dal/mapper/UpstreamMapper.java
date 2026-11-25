package com.modvault.dal.mapper;

import com.modvault.dal.entity.Upstream;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import java.util.UUID;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UpstreamMapper {

    String COLUMNS = "id, name, base_url, enabled, connect_timeout_ms, read_timeout_ms, "
            + "extra_headers::text AS extra_headers, max_tokens_field, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM upstreams ORDER BY created_at")
    List<Upstream> findAll();

    @Select("SELECT " + COLUMNS + " FROM upstreams WHERE id = #{id}")
    Upstream findById(@Param("id") UUID id);

    @Select("SELECT " + COLUMNS + " FROM upstreams WHERE name = #{name}")
    Upstream findByName(@Param("name") String name);

    @Insert("INSERT INTO upstreams "
            + "(id, name, base_url, enabled, connect_timeout_ms, read_timeout_ms, extra_headers, max_tokens_field) "
            + "VALUES (#{id}, #{name}, #{baseUrl}, #{enabled}, #{connectTimeoutMs}, #{readTimeoutMs}, "
            + "CAST(#{extraHeaders} AS jsonb), #{maxTokensField})")
    int insert(Upstream upstream);

    @Update("UPDATE upstreams SET name = #{name}, base_url = #{baseUrl}, enabled = #{enabled}, "
            + "connect_timeout_ms = #{connectTimeoutMs}, read_timeout_ms = #{readTimeoutMs}, "
            + "extra_headers = CAST(#{extraHeaders} AS jsonb), max_tokens_field = #{maxTokensField}, "
            + "updated_at = now() WHERE id = #{id}")
    int update(Upstream upstream);

    @Delete("DELETE FROM upstreams WHERE id = #{id}")
    int delete(@Param("id") UUID id);
}
