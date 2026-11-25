package com.modvault.dal.mapper;

import com.modvault.dal.entity.ModelMapping;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ModelMappingMapper {

    String COLUMNS = "id, anthropic_model, upstream_id, upstream_model, enabled, created_at, updated_at";

    @Select("SELECT " + COLUMNS + " FROM model_mappings ORDER BY created_at")
    List<ModelMapping> findAll();

    @Select("SELECT " + COLUMNS + " FROM model_mappings WHERE id = #{id}")
    ModelMapping findById(@Param("id") String id);

    @Select("SELECT " + COLUMNS + " FROM model_mappings WHERE anthropic_model = #{model} AND enabled = TRUE "
            + "ORDER BY created_at DESC LIMIT 1")
    ModelMapping findEnabledByAnthropicModel(@Param("model") String model);

    @Select("SELECT COUNT(*) FROM model_mappings WHERE anthropic_model = #{model} AND enabled = TRUE")
    int countEnabledByModel(@Param("model") String model);

    @Insert("INSERT INTO model_mappings (id, anthropic_model, upstream_id, upstream_model, enabled) "
            + "VALUES (#{id}, #{anthropicModel}, #{upstreamId}, #{upstreamModel}, #{enabled})")
    int insert(ModelMapping mapping);

    @Update("UPDATE model_mappings SET upstream_id = #{upstreamId}, upstream_model = #{upstreamModel}, "
            + "enabled = #{enabled}, updated_at = now() WHERE id = #{id}")
    int update(ModelMapping mapping);

    @Delete("DELETE FROM model_mappings WHERE id = #{id}")
    int delete(@Param("id") String id);
}
