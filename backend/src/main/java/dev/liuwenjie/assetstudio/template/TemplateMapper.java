package dev.liuwenjie.assetstudio.template;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TemplateMapper {

    String COLUMNS = "id, title, category, cover_url, description, required_image_count, "
            + "accepted_mime_types, max_file_size_bytes, sort_order";

    @Select("SELECT " + COLUMNS + " FROM template ORDER BY sort_order, id")
    List<Template> findAll();

    @Select("SELECT " + COLUMNS + " FROM template WHERE category = #{category} ORDER BY sort_order, id")
    List<Template> findByCategory(@Param("category") String category);

    @Select("SELECT " + COLUMNS + " FROM template WHERE id = #{id}")
    Template findById(@Param("id") String id);

    @Select("SELECT DISTINCT category FROM template ORDER BY category")
    List<String> findCategories();
}