package com.pdp.mysql.identity;

import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper {

  UserAccountRow findById(@Param("id") UUID id);

  UserAccountRow findByExternalSubject(@Param("externalSubject") String externalSubject);

  int insert(UserAccountRow row);

  int update(UserAccountRow row);
}
