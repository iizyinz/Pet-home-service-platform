package com.example.backend.service.order;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.context.LoginUser;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.dto.order.CreateOrderRequest;
import com.example.backend.dto.order.OrderDetailVO;
import com.example.backend.dto.order.OrderListVO;
import com.example.backend.entity.OrderProgress;
import com.example.backend.entity.Pet;
import com.example.backend.entity.Review;
import com.example.backend.entity.ServiceItem;
import com.example.backend.entity.ServiceOrder;
import com.example.backend.entity.StaffProfile;
import com.example.backend.entity.UserAddress;
import com.example.backend.enums.OrderAction;
import com.example.backend.enums.OrderStatus;
import com.example.backend.mapper.OrderProgressMapper;
import com.example.backend.mapper.PetMapper;
import com.example.backend.mapper.ReviewMapper;
import com.example.backend.mapper.ServiceItemMapper;
import com.example.backend.mapper.ServiceOrderMapper;
import com.example.backend.mapper.StaffProfileMapper;
import com.example.backend.mapper.SysUserMapper;
import com.example.backend.mapper.UserAddressMapper;
import com.example.backend.service.finance.BalanceService;
import com.example.backend.service.notice.NoticeService;
import com.example.backend.service.review.ReputationService;
import com.example.backend.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 服务订单核心服务：下单扣款、状态机流转、进度反馈。
 * 强约束（PRD 第四/八章）：
 * 1) 状态变更集中经 OrderStateMachine 校验，并以条件 UPDATE 落库（并发安全/幂等）
 * 2) 状态更新 + 资金流水 + 通知 + 信誉重算 同一事务，失败整体回滚
 * 3) 归属校验：服务师/用户只能操作本人订单，越权 403
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ServiceOrderMapper orderMapper;
    private final OrderProgressMapper progressMapper;
    private final PetMapper petMapper;
    private final UserAddressMapper addressMapper;
    private final ServiceItemMapper itemMapper;
    private final StaffProfileMapper profileMapper;
    private final SysUserMapper userMapper;
    private final ReviewMapper reviewMapper;
    private final BalanceService balanceService;
    private final NoticeService noticeService;
    private final ReputationService reputationService;
    private final OrderStateMachine stateMachine;

    /**
     * 提交预约（AC-01/AC-02）：归属/上架/认证/时间校验 -> 生成 SO 单号 ->
     * 原子扣余额 + CONSUME 流水 + 新订单通知；任一步失败整体回滚。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderListVO create(Long userId, CreateOrderRequest req) {
        Pet pet = petMapper.selectById(req.getPetId());
        if (pet == null || !userId.equals(pet.getUserId())) {
            throw new BizException(ErrorCode.PET_NOT_FOUND);
        }
        UserAddress address = addressMapper.selectById(req.getAddressId());
        if (address == null || !userId.equals(address.getUserId())) {
            throw new BizException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        ServiceItem item = itemMapper.selectById(req.getServiceItemId());
        if (item == null) {
            throw new BizException(ErrorCode.ITEM_NOT_FOUND);
        }
        if (item.getStatus() == null || item.getStatus() != 1) {
            throw new BizException(ErrorCode.ITEM_OFF_SHELF);
        }
        StaffProfile staff = profileMapper.selectById(req.getStaffId());
        if (staff == null) {
            throw new BizException(ErrorCode.STAFF_NOT_FOUND);
        }
        if (staff.getCertStatus() == null || staff.getCertStatus() != 1
                || staff.getStatus() == null || staff.getStatus() != 1) {
            throw new BizException(ErrorCode.STAFF_NOT_CERTIFIED);
        }
        if (req.getAppointmentTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.APPOINTMENT_INVALID);
        }

        ServiceOrder order = new ServiceOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setStaffId(req.getStaffId());
        order.setPetId(req.getPetId());
        order.setServiceItemId(req.getServiceItemId());
        order.setAddressId(req.getAddressId());
        order.setItemName(item.getName());
        order.setAmount(item.getPrice());
        order.setStatus(OrderStatus.PENDING_CONFIRM.getCode());
        order.setAppointmentTime(req.getAppointmentTime());
        order.setRemark(req.getRemark());
        orderMapper.insert(order);

        // 原子扣款 + 消费流水（余额不足抛 2001 -> 事务回滚，订单与流水都不落库）
        balanceService.deduct(userId, item.getPrice(), order.getId(), "订单消费 " + order.getOrderNo());

        noticeService.send(order.getStaffId(), "ORDER",
                "您有新订单待接单", "订单号 " + order.getOrderNo() + "，请尽快接单", order.getId());
        return toListVO(order);
    }

    /** SO + 时间戳 + 随机尾号，唯一冲突重试 */
    private String generateOrderNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String no = "SO" + ts + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        while (orderMapper.selectCount(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getOrderNo, no)) > 0) {
            no = "SO" + ts + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
        }
        return no;
    }

    // ================================================================
    // 查询：列表 / 详情 / 进度
    // ================================================================

    /** 我的订单（USER）或我的接单（STAFF），按状态筛选分页 */
    public PageResult<OrderListVO> pageMyOrders(LoginUser viewer, String status, int page, int size) {
        boolean isStaff = viewer.isStaff();
        IPage<ServiceOrder> result = orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<ServiceOrder>lambdaQuery()
                        .eq(isStaff ? ServiceOrder::getStaffId : ServiceOrder::getUserId, viewer.getId())
                        .eq(status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status),
                                ServiceOrder::getStatus, status)
                        .orderByDesc(ServiceOrder::getId));
        List<OrderListVO> voList = result.getRecords().stream().map(this::toListVO).toList();
        return PageResult.of(voList, result.getTotal(), page, size);
    }

    /** 管理端全量订单查询 */
    public PageResult<OrderListVO> pageAll(String status, Long userId, Long staffId, int page, int size) {
        IPage<ServiceOrder> result = orderMapper.selectPage(new Page<>(page, size),
                Wrappers.<ServiceOrder>lambdaQuery()
                        .eq(status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status),
                                ServiceOrder::getStatus, status)
                        .eq(userId != null, ServiceOrder::getUserId, userId)
                        .eq(staffId != null, ServiceOrder::getStaffId, staffId)
                        .orderByDesc(ServiceOrder::getId));
        List<OrderListVO> voList = result.getRecords().stream().map(this::toListVO).toList();
        return PageResult.of(voList, result.getTotal(), page, size);
    }

    /** 订单详情：完整快照 + 进度（含未读）+ 可执行动作；越权 403 */
    public OrderDetailVO detail(Long orderId, LoginUser viewer) {
        ServiceOrder order = getOwnedOrder(orderId, viewer);
        OrderDetailVO vo = new OrderDetailVO();
        fillBase(vo, order);

        UserAddress address = addressMapper.selectById(order.getAddressId());
        if (address != null) {
            vo.setContactName(address.getContactName());
            vo.setContactPhone(UserService.maskPhone(address.getContactPhone()));
            vo.setAddress((address.getRegion() == null ? "" : address.getRegion()) + address.getDetail());
        }
        ServiceItem item = itemMapper.selectById(order.getServiceItemId());
        if (item != null) {
            vo.setDurationMinutes(item.getDurationMinutes());
        }

        List<OrderProgress> progresses = progressMapper.selectList(Wrappers.<OrderProgress>lambdaQuery()
                .eq(OrderProgress::getOrderId, orderId).orderByDesc(OrderProgress::getId));
        vo.setProgressList(progresses);
        LocalDateTime readAt = order.getProgressReadAt();
        long unread = progresses.stream()
                .filter(p -> readAt == null || (p.getCreatedAt() != null && p.getCreatedAt().isAfter(readAt)))
                .count();
        vo.setProgressUnread(unread > 0);
        vo.setUnreadProgress(unread);

        if (viewer.getId().equals(order.getUserId())) {
            Long reviewed = reviewMapper.selectCount(Wrappers.<Review>lambdaQuery()
                    .eq(Review::getOrderId, orderId).eq(Review::getUserId, viewer.getId()));
            vo.setReviewed(reviewed != null && reviewed > 0);
        }
        vo.setAvailableActions(availableActions(order, viewer));
        return vo;
    }

    /** 进度列表：订单主人查看即标记已读并记录阅读时间（AC-05） */
    @Transactional(rollbackFor = Exception.class)
    public List<OrderProgress> listProgress(Long orderId, LoginUser viewer) {
        ServiceOrder order = getOwnedOrder(orderId, viewer);
        boolean isOwner = viewer.getId().equals(order.getUserId());
        if (isOwner && order.getProgressReadAt() == null) {
            ServiceOrder update = new ServiceOrder();
            update.setId(orderId);
            update.setProgressReadAt(LocalDateTime.now());
            orderMapper.updateById(update);
        }
        return progressMapper.selectList(Wrappers.<OrderProgress>lambdaQuery()
                .eq(OrderProgress::getOrderId, orderId).orderByDesc(OrderProgress::getId));
    }

    /** 归属校验：用户本人 / 服务师本人 / 管理员，否则 403（AC-09） */
    public ServiceOrder getOwnedOrder(Long orderId, LoginUser viewer) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        boolean isOwner = viewer.getId().equals(order.getUserId())
                || (order.getStaffId() != null && viewer.getId().equals(order.getStaffId()));
        if (!isOwner && !viewer.isAdmin()) {
            throw new BizException(ErrorCode.ORDER_NO_ACCESS);
        }
        return order;
    }

    public OrderListVO toListVO(ServiceOrder order) {
        OrderListVO vo = new OrderListVO();
        fillBase(vo, order);
        return vo;
    }

    private void fillBase(OrderListVO vo, ServiceOrder order) {
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setUserId(order.getUserId());
        vo.setStaffId(order.getStaffId());
        vo.setPetId(order.getPetId());
        vo.setServiceItemId(order.getServiceItemId());
        vo.setAddressId(order.getAddressId());
        vo.setItemName(order.getItemName());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setAppointmentTime(order.getAppointmentTime());
        vo.setStartTime(order.getStartTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setConfirmTime(order.getConfirmTime());
        vo.setCancelReason(order.getCancelReason());
        vo.setRemark(order.getRemark());
        vo.setProgressReadAt(order.getProgressReadAt());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setUpdatedAt(order.getUpdatedAt());

        OrderStatus st = OrderStatus.of(order.getStatus());
        vo.setStatusLabel(st == null ? order.getStatus() : st.getLabel());

        Pet pet = petMapper.selectById(order.getPetId());
        if (pet != null) {
            vo.setPetName(pet.getName());
            vo.setPetBreed(pet.getBreed());
        }
        if (order.getStaffId() != null) {
            var staffUser = userMapper.selectById(order.getStaffId());
            if (staffUser != null) {
                vo.setStaffName(staffUser.getNickname());
                vo.setStaffAvatar(staffUser.getAvatar());
            }
        }
        var owner = userMapper.selectById(order.getUserId());
        if (owner != null) {
            vo.setOwnerName(owner.getNickname());
        }
    }

    /** 按状态 + 角色计算可执行动作（前端按钮控制） */
    private List<String> availableActions(ServiceOrder order, LoginUser viewer) {
        List<String> actions = new ArrayList<>();
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status == null) {
            return actions;
        }
        boolean isOwner = viewer.getId().equals(order.getUserId());
        boolean isStaffOwner = order.getStaffId() != null && viewer.getId().equals(order.getStaffId());
        switch (status) {
            case PENDING_CONFIRM -> {
                if (isOwner) actions.add("cancel");
                if (isStaffOwner) { actions.add("accept"); actions.add("reject"); }
            }
            case CONFIRMED -> {
                if (isOwner) actions.add("cancel");
                if (isStaffOwner) actions.add("start");
            }
            case SERVING -> {
                if (isStaffOwner) { actions.add("feedback"); actions.add("finish"); }
            }
            case PENDING_SETTLE -> {
                if (isOwner) actions.add("confirm");
            }
            case COMPLETED -> {
                if (isOwner) actions.add("review");
            }
            default -> { }
        }
        actions.add("chat");
        return actions;
    }

    // ================================================================
    // 状态机流转：每个动作 = 校验 -> 条件 UPDATE -> 副作用（同事务）
    // ================================================================

    /**
     * 条件更新状态：WHERE id=? AND status=当前状态，成功才生效。
     * 并发/重复请求下 rows=0 -> 返回业务错误「订单当前状态不允许此操作」（AC-03）。
     */
    private void doTransition(ServiceOrder order, OrderAction action, ServiceOrder extras) {
        OrderStatus from = OrderStatus.of(order.getStatus());
        OrderStatus to = stateMachine.apply(from, action);   // 非法流转直接抛 2002
        ServiceOrder upd = extras == null ? new ServiceOrder() : extras;
        upd.setId(order.getId());
        upd.setStatus(to.getCode());
        int rows = orderMapper.update(upd, Wrappers.<ServiceOrder>lambdaUpdate()
                .eq(ServiceOrder::getId, order.getId())
                .eq(ServiceOrder::getStatus, from.getCode()));
        if (rows == 0) {
            throw new BizException(ErrorCode.ORDER_STATUS_ILLEGAL);
        }
        order.setStatus(to.getCode());
    }

    /** 用户取消（PENDING_CONFIRM/CONFIRMED -> CANCELLED，退款 + 通知服务师） */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long orderId, String reason) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NO_ACCESS);
        }
        ServiceOrder extras = new ServiceOrder();
        extras.setCancelReason(reason == null || reason.isBlank() ? "用户取消" : reason);
        doTransition(order, OrderAction.CANCEL, extras);

        balanceService.refund(order.getUserId(), order.getAmount(), order.getId(),
                "取消退款 " + order.getOrderNo());
        noticeService.send(order.getStaffId(), "ORDER",
                "订单已取消", "订单 " + order.getOrderNo() + " 被用户取消", order.getId());
        log.info("订单取消退款完成 orderNo={} amount={}", order.getOrderNo(), order.getAmount());
    }

    /** 服务师接单（PENDING_CONFIRM -> CONFIRMED） */
    @Transactional(rollbackFor = Exception.class)
    public void accept(Long staffId, Long orderId) {
        ServiceOrder order = getStaffOrder(staffId, orderId);
        doTransition(order, OrderAction.ACCEPT, null);
        noticeService.send(order.getUserId(), "ORDER",
                "服务师已接单", "订单 " + order.getOrderNo() + " 已接单，请留意上门时间", order.getId());
    }

    /** 服务师拒单（PENDING_CONFIRM -> CANCELLED，退款 + 通知用户改选） */
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long staffId, Long orderId, String reason) {
        ServiceOrder order = getStaffOrder(staffId, orderId);
        ServiceOrder extras = new ServiceOrder();
        extras.setCancelReason(reason == null || reason.isBlank() ? "服务师拒单" : reason);
        doTransition(order, OrderAction.REJECT, extras);

        balanceService.refund(order.getUserId(), order.getAmount(), order.getId(),
                "拒单退款 " + order.getOrderNo());
        noticeService.send(order.getUserId(), "ORDER",
                "订单已被接单方拒绝", "订单 " + order.getOrderNo() + " 已退款，您可以重新预约", order.getId());
    }

    /** 服务师开始服务（CONFIRMED -> SERVING，记录开始时间） */
    @Transactional(rollbackFor = Exception.class)
    public void start(Long staffId, Long orderId) {
        ServiceOrder order = getStaffOrder(staffId, orderId);
        ServiceOrder extras = new ServiceOrder();
        extras.setStartTime(LocalDateTime.now());
        doTransition(order, OrderAction.START, extras);
        noticeService.send(order.getUserId(), "ORDER",
                "服务已开始", "订单 " + order.getOrderNo() + " 服务师已上门开始服务", order.getId());
    }

    /** 服务师标记完成（SERVING -> PENDING_SETTLE，录入实际服务时间，通知用户确认） */
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long staffId, Long orderId, com.example.backend.dto.order.FinishRequest req) {
        ServiceOrder order = getStaffOrder(staffId, orderId);
        ServiceOrder extras = new ServiceOrder();
        extras.setFinishTime(req.getActualFinishTime() != null ? req.getActualFinishTime() : LocalDateTime.now());
        if (req.getActualStartTime() != null) {
            extras.setStartTime(req.getActualStartTime());
        } else if (order.getStartTime() == null) {
            extras.setStartTime(LocalDateTime.now());
        }
        doTransition(order, OrderAction.FINISH, extras);
        noticeService.send(order.getUserId(), "ORDER",
                "服务已完成待确认", "订单 " + order.getOrderNo() + " 已完成，请确认并评价", order.getId());
    }

    /** 用户确认完成（PENDING_SETTLE -> COMPLETED，重算信誉 + 通知服务师） */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long userId, Long orderId) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ORDER_NO_ACCESS);
        }
        ServiceOrder extras = new ServiceOrder();
        extras.setConfirmTime(LocalDateTime.now());
        doTransition(order, OrderAction.CONFIRM, extras);

        reputationService.recompute(order.getStaffId());   // 累计完成数重算（聚合 SQL）
        noticeService.send(order.getStaffId(), "ORDER",
                "订单已确认完成", "订单 " + order.getOrderNo() + " 用户已确认完成", order.getId());
    }

    /** 服务师归属校验 */
    private ServiceOrder getStaffOrder(Long staffId, Long orderId) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        if (order.getStaffId() == null || !order.getStaffId().equals(staffId)) {
            throw new BizException(ErrorCode.ORDER_NO_ACCESS);
        }
        return order;
    }

    // ================================================================
    // 定时任务动作（幂等）：超时未接自动取消退款、超时未确认自动完成
    // ================================================================

    /** 超时未接（PENDING_CONFIRM 超过 24h -> CANCELLED，退款 + 通知双方） */
    @Transactional(rollbackFor = Exception.class)
    public void timeoutAccept(Long orderId) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null || !OrderStatus.PENDING_CONFIRM.getCode().equals(order.getStatus())) {
            return; // 状态已被并发处理，幂等跳过
        }
        ServiceOrder extras = new ServiceOrder();
        extras.setCancelReason("超时未接单，系统自动取消");
        doTransition(order, OrderAction.TIMEOUT, extras);

        balanceService.refund(order.getUserId(), order.getAmount(), order.getId(),
                "超时未接退款 " + order.getOrderNo());
        noticeService.send(order.getUserId(), "ORDER",
                "订单超时自动取消", "订单 " + order.getOrderNo() + " 服务师未及时接单，款项已退回", order.getId());
        noticeService.send(order.getStaffId(), "ORDER",
                "订单超时未接已取消", "订单 " + order.getOrderNo() + " 因超时未接单已自动取消", order.getId());
    }

    /** 待确认完成超时（72h -> COMPLETED，防滞留，开放评价） */
    @Transactional(rollbackFor = Exception.class)
    public void autoConfirm(Long orderId) {
        ServiceOrder order = orderMapper.selectById(orderId);
        if (order == null || !OrderStatus.PENDING_SETTLE.getCode().equals(order.getStatus())) {
            return; // 幂等
        }
        ServiceOrder extras = new ServiceOrder();
        extras.setConfirmTime(LocalDateTime.now());
        doTransition(order, OrderAction.AUTO_CONFIRM, extras);

        reputationService.recompute(order.getStaffId());
        noticeService.send(order.getUserId(), "ORDER",
                "订单已自动完成", "订单 " + order.getOrderNo() + " 超时未确认，系统已自动完成，可提交评价", order.getId());
        noticeService.send(order.getStaffId(), "ORDER",
                "订单已自动完成", "订单 " + order.getOrderNo() + " 已自动确认完成", order.getId());
    }

    // ================================================================
    // 服务过程反馈（PRD 6.3）
    // ================================================================

    /** 服务师发布进度（仅服务中订单），并推送通知给用户 */
    @Transactional(rollbackFor = Exception.class)
    public OrderProgress publishProgress(Long staffId, Long orderId,
                                         com.example.backend.dto.order.ProgressRequest req) {
        ServiceOrder order = getStaffOrder(staffId, orderId);
        // 状态机校验：SERVING + feedback（其他状态返回 2002）
        stateMachine.apply(OrderStatus.of(order.getStatus()), OrderAction.FEEDBACK);

        OrderProgress progress = new OrderProgress();
        progress.setOrderId(orderId);
        progress.setTitle(req.getTitle());
        progress.setImages(com.example.backend.common.util.JsonUtil.toJson(req.getImages()));
        progress.setServiceTime(req.getServiceTime());
        progress.setCreatedBy(staffId);
        progressMapper.insert(progress);

        noticeService.send(order.getUserId(), "ORDER",
                "服务进度更新", "订单 " + order.getOrderNo() + "：" + req.getTitle(), order.getId());
        return progress;
    }

    // ================================================================
    // 服务师工作台概览（PRD 6.1）
    // ================================================================

    /** 待接单数 / 进行中数 / 今日完成数 */
    public Map<String, Object> staffWorkbench(Long staffId) {
        long pending = count(staffId, "PENDING_CONFIRM");
        long serving = count(staffId, "CONFIRMED") + count(staffId, "SERVING") + count(staffId, "PENDING_SETTLE");
        long todayCompleted = orderMapper.selectCount(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStaffId, staffId)
                .eq(ServiceOrder::getStatus, "COMPLETED")
                .ge(ServiceOrder::getConfirmTime, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0)));
        List<ServiceOrder> completed = orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStaffId, staffId).eq(ServiceOrder::getStatus, "COMPLETED"));
        java.math.BigDecimal income = completed.stream()
                .map(ServiceOrder::getAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> staffUser = new java.util.LinkedHashMap<>();
        staffUser.put("pendingCount", pending);
        staffUser.put("inProgressCount", serving);
        staffUser.put("todayCompletedCount", todayCompleted);
        staffUser.put("completedCount", completed.size());
        staffUser.put("totalIncome", income);
        return staffUser;
    }

    private long count(Long staffId, String status) {
        Long cnt = orderMapper.selectCount(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStaffId, staffId).eq(ServiceOrder::getStatus, status));
        return cnt == null ? 0 : cnt;
    }
}




