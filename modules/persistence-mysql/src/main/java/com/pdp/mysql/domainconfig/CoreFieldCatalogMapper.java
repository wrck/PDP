package com.pdp.mysql.domainconfig;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoreFieldCatalogMapper {
  List<CoreFieldRow> findAll();
}
