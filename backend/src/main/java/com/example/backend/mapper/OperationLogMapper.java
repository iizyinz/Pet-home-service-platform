package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/** OperationLog Mapper */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}

