package com.example.backend.service.finance;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.entity.BalanceRecord;
import com.example.backend.entity.SysUser;
import com.example.backend.mapper.BalanceRecordMapper;
import com.example.backend.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 余额与流水服务。
 * 规则（PRD 8.1）：资金变动一律写流水；扣款/加款走原子 SQL（行锁）防并发超扣；
 * 流水与余额同事务，不允许只改余额不留痕。
 */
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final SysUserMapper userMapper;
    private final BalanceRecordMapper recordMapper;

    public BigDecimal getBalance(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        return user.getBalance();
    }

    public PageResult<BalanceRecord> pageRecords(Long userId, int page, int size) {
        IPage<BalanceRecord> result = recordMapper.selectPage(new Page<>(page, size),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<BalanceRecord>lambdaQuery()
                        .eq(BalanceRecord::getUserId, userId)
                        .orderByDesc(BalanceRecord::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    /** 充值（模拟）：balance 增加 + RECHARGE 流水 */
    @Transactional(rollbackFor = Exception.class)
    public void recharge(Long userId, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BizException(ErrorCode.RECHARGE_INVALID);
        }
        apply(userId, amount, "RECHARGE", null, "账户充值");
    }

    /** 下单扣款：余额不足抛 2001（原子 UPDATE，行锁保证一致性） */
    @Transactional(rollbackFor = Exception.class)
    public void deduct(Long userId, BigDecimal amount, Long orderId, String remark) {
        int rows = userMapper.deductBalance(userId, amount);
        if (rows == 0) {
            throw new BizException(ErrorCode.BALANCE_INSUFFICIENT);
        }
        insertRecord(userId, amount.negate(), "CONSUME", orderId, remark);
    }

    /** 退款：取消/拒单/超时退回，与订单状态流转同事务 */
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long userId, BigDecimal amount, Long orderId, String remark) {
        int rows = userMapper.addBalance(userId, amount);
        if (rows == 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        insertRecord(userId, amount, "REFUND", orderId, remark);
    }

    /** 管理员人工调账（ADJUST 流水 + 审计由调用方记录） */
    @Transactional(rollbackFor = Exception.class)
    public void adjust(Long userId, BigDecimal delta, String remark) {
        if (delta == null || delta.signum() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "调整金额不能为 0");
        }
        if (delta.signum() < 0) {
            int rows = userMapper.deductBalance(userId, delta.negate());
            if (rows == 0) {
                throw new BizException(ErrorCode.BALANCE_INSUFFICIENT);
            }
        } else {
            userMapper.addBalance(userId, delta);
        }
        insertRecord(userId, delta, "ADJUST", null, remark);
    }

    /** 内部：加款 + 流水（同事务） */
    private void apply(Long userId, BigDecimal delta, String type, Long orderId, String remark) {
        int rows = userMapper.addBalance(userId, delta);
        if (rows == 0) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        insertRecord(userId, delta, type, orderId, remark);
    }

    /** 读取变动后余额快照并写流水 */
    private void insertRecord(Long userId, BigDecimal delta, String type, Long orderId, String remark) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.USER_NOT_FOUND);
        }
        BalanceRecord record = new BalanceRecord();
        record.setUserId(userId);
        record.setType(type);
        record.setAmount(delta);
        record.setBalanceAfter(user.getBalance());
        record.setOrderId(orderId);
        record.setChannel("BALANCE");
        record.setRemark(remark);
        recordMapper.insert(record);
    }

    /** 供管理端查询全量流水 */
    public PageResult<BalanceRecord> pageAll(int page, int size, String type, Long userId) {
        IPage<BalanceRecord> result = recordMapper.selectPage(new Page<>(page, size),
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<BalanceRecord>lambdaQuery()
                        .eq(userId != null, BalanceRecord::getUserId, userId)
                        .eq(type != null && !type.isBlank(), BalanceRecord::getType, type)
                        .orderByDesc(BalanceRecord::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), (int) result.getCurrent(), (int) result.getSize());
    }

    public List<BalanceRecord> listByOrder(Long orderId) {
        return recordMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<BalanceRecord>lambdaQuery()
                .eq(BalanceRecord::getOrderId, orderId)
                .orderByAsc(BalanceRecord::getId));
    }
}
