package com.doccheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doccheck.domain.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
