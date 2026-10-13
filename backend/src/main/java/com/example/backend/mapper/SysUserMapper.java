package com.example.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.backend.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** SysUser Mapper */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 原子扣款：余额足够才扣减（防超扣），返回受影响行数 */
    @Update("UPDATE sys_user SET balance = balance - #{amount}, updated_at = NOW() " +
            "WHERE id = #{userId} AND balance >= #{amount} AND status = 1")
    int deductBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);

    /** 原子加款：充值/退款/调账 */
    @Update("UPDATE sys_user SET balance = balance + #{amount}, updated_at = NOW() WHERE id = #{userId}")
    int addBalance(@Param("userId") Long userId, @Param("amount") java.math.BigDecimal amount);

    @Select("SELECT COUNT(*) FROM sys_user WHERE role = #{role}")
    long countByRole(@Param("role") String role);
}