package dev.liuwenjie.assetstudio.asset;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AssetMapper {

    @Select("SELECT id, user_id, original_name, stored_path, mime_type, size_bytes, created_at "
            + "FROM asset WHERE id = #{id}")
    Asset findById(@Param("id") String id);

    @Insert("INSERT INTO asset (id, user_id, original_name, stored_path, mime_type, size_bytes, created_at) "
            + "VALUES (#{id}, #{userId}, #{originalName}, #{storedPath}, #{mimeType}, #{sizeBytes}, #{createdAt})")
    int insert(@Param("id") String id, @Param("userId") String userId,
               @Param("originalName") String originalName, @Param("storedPath") String storedPath,
               @Param("mimeType") String mimeType, @Param("sizeBytes") long sizeBytes,
               @Param("createdAt") LocalDateTime createdAt);
}