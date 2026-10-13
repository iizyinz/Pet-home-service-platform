package com.example.backend.service.review;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.entity.Review;
import com.example.backend.entity.ServiceOrder;
import com.example.backend.entity.StaffProfile;
import com.example.backend.mapper.ReviewMapper;
import com.example.backend.mapper.ServiceOrderMapper;
import com.example.backend.mapper.StaffProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 信誉统计重算（PRD 8.6）：
 * - completed_count = 该服务师已完成(COMPLETED)订单数（聚合 SQL，防漂移）
 * - avg_rating = 全部显示评价的平均分，1 位小数
 * 评价保存/订单完成/评价隐藏后调用，与业务写入同事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReputationService {

    private final StaffProfileMapper profileMapper;
    private final ServiceOrderMapper orderMapper;
    private final ReviewMapper reviewMapper;

    @Transactional(rollbackFor = Exception.class)
    public void recompute(Long staffId) {
        if (staffId == null) {
            return;
        }
        StaffProfile profile = profileMapper.selectById(staffId);
        if (profile == null) {
            return;
        }
        Long completed = orderMapper.selectCount(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStaffId, staffId)
                .eq(ServiceOrder::getStatus, "COMPLETED"));
        BigDecimal avg = reviewMapper.avgRating(staffId);
        profile.setCompletedCount(completed == null ? 0 : completed.intValue());
        profile.setAvgRating(avg == null ? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
                : avg.setScale(1, RoundingMode.HALF_UP));
        profileMapper.updateById(profile);
        log.info("服务师信誉重算 staffId={} completed={} avg={}", staffId, profile.getCompletedCount(), profile.getAvgRating());
    }
}
