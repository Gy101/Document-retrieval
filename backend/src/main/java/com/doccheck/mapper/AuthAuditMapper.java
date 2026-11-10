package com.doccheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doccheck.domain.entity.AuthAudit;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthAuditMapper extends BaseMapper<AuthAudit> {
}
