package com.example.backend.service.review;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.common.util.JsonUtil;
import com.example.backend.dto.review.ReviewRequest;
import com.example.backend.entity.Review;
import com.example.backend.entity.ServiceOrder;
import com.example.backend.mapper.ReviewMapper;
import com.example.backend.mapper.ServiceOrderMapper;
import com.example.backend.service.notice.NoticeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评价服务（PRD 5.6 / 8.6）：
 * - 仅「已完成」订单可评；一单一评（uk_order 兜底）
 * - 保存后同事务：重算服务师 avg_rating/completed_count + 推送评价通知
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final ServiceOrderMapper orderMapper;
    private final ReputationService reputationService;
    private final NoticeService noticeService;

    /** 提交评价（AC-06） */
    @Transactional(rollbackFor = Exception.class)
    public Review submit(Long userId, ReviewRequest req) {
        ServiceOrder order = orderMapper.selectById(req.getOrderId());
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NO_ACCESS);
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.REVIEW_NOT_ALLOWED);
        }
        if (req.getRating() < 1 || req.getRating() > 5) {
            throw new BizException(ErrorCode.REVIEW_RATING_INVALID);
        }
        Long exists = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                .eq(Review::getOrderId, order.getId()).eq(Review::getUserId, userId));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.REVIEW_DUPLICATE);
        }

        Review review = new Review();
        review.setOrderId(order.getId());
        review.setUserId(userId);
        review.setStaffId(order.getStaffId());
        review.setRating(req.getRating());
        review.setContent(req.getContent());
        review.setImages(JsonUtil.toJson(req.getImages()));
        review.setStatus(1);
        try {
            reviewMapper.insert(review);
        } catch (DuplicateKeyException e) {
            // uk_order 唯一约束兜底：并发重复提交直接拒绝
            throw new BizException(ErrorCode.REVIEW_DUPLICATE);
        }

        reputationService.recompute(order.getStaffId());   // 聚合 SQL 重算，防漂移
        noticeService.send(order.getStaffId(), "REVIEW",
                "收到新评价", "订单 " + order.getOrderNo() + " 收到 " + req.getRating() + " 星评价", order.getId());
        log.info("评价提交 orderId={} staffId={} rating={}", order.getId(), order.getStaffId(), req.getRating());
        return review;
    }

    /** 服务人员评价列表（仅显示 status=1） */
    public PageResult<Review> pageByStaff(Long staffId, int page, int size) {
        IPage<Review> result = reviewMapper.selectPage(new Page<>(page, size),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getStaffId, staffId).eq(Review::getStatus, 1)
                        .orderByDesc(Review::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 我发出的评价 */
    public PageResult<Review> pageMy(Long userId, int page, int size) {
        IPage<Review> result = reviewMapper.selectPage(new Page<>(page, size),
                Wrappers.<Review>lambdaQuery()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 管理端全量评价 */
    public PageResult<Review> pageAll(Long staffId, Long userId, Integer status, int page, int size) {
        IPage<Review> result = reviewMapper.selectPage(new Page<>(page, size),
                Wrappers.<Review>lambdaQuery()
                        .eq(staffId != null, Review::getStaffId, staffId)
                        .eq(userId != null, Review::getUserId, userId)
                        .eq(status != null, Review::getStatus, status)
                        .orderByDesc(Review::getId));
        return PageResult.of(result.getRecords(), result.getTotal(), page, size);
    }

    /** 管理端隐藏/恢复违规评价，触发信誉重算 */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long reviewId, Integer status) {
        Review review = reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评价不存在");
        }
        review.setStatus(status);
        reviewMapper.updateById(review);
        reputationService.recompute(review.getStaffId());
    }
}
