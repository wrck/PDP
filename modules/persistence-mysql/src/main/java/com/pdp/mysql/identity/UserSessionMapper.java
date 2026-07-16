package com.pdp.mysql.identity;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserSessionMapper {

  UserSessionRow findById(@Param("id") UUID id);

  List<UserSessionRow> findActiveByUserId(@Param("userId") UUID userId);

  int insert(UserSessionRow row);

  int update(UserSessionRow row);
}
