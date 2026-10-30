package dev.liuwenjie.assetstudio.identity;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {

    @Select("SELECT id, display_name, created_at FROM app_user WHERE id = #{id}")
    User findById(@Param("id") String id);

    @Insert("INSERT INTO app_user (id, display_name, created_at) VALUES (#{id}, #{displayName}, #{createdAt})")
    int insert(@Param("id") String id, @Param("displayName") String displayName,
               @Param("createdAt") LocalDateTime createdAt);
}