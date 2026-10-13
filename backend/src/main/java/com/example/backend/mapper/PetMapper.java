package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.Pet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** Pet Mapper */
@Mapper
public interface PetMapper extends BaseMapper<Pet> {

    @Select("SELECT COUNT(*) FROM pet WHERE deleted = 0")
    long countAll();
}