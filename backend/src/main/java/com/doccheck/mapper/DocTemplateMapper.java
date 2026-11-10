package com.doccheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doccheck.domain.entity.DocTemplate;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocTemplateMapper extends BaseMapper<DocTemplate> {
}
