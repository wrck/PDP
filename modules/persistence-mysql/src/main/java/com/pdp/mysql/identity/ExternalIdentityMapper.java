package com.pdp.mysql.identity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ExternalIdentityMapper {

  ExternalIdentityRow findByIssuerAndSubject(
      @Param("issuer") String issuer, @Param("subject") String subject);

  int insert(ExternalIdentityRow row);

  int update(ExternalIdentityRow row);
}
