package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.UserAddress;
import org.apache.ibatis.annotations.Mapper;

/** UserAddress Mapper */
@Mapper
public interface UserAddressMapper extends BaseMapper<UserAddress> {
}

