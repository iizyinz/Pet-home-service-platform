package com.example.backend.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.order.OrderListVO;
import com.example.backend.entity.ServiceOrder;
import com.example.backend.entity.StaffProfile;
import com.example.backend.mapper.ServiceOrderMapper;
import com.example.backend.service.order.OrderService;
import com.example.backend.service.staff.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务人员工作台：概览（待办/进行中/收入/评分）与排期日历（PRD 6.1 / 6.4）
 */
@Tag(name = "服务人员工作台", description = "仅 STAFF：概览统计与月度排期")
@RestController
@RequestMapping("/api/staff/workbench")
@RequiredArgsConstructor
public class StaffWorkbenchController {

    private final OrderService orderService;
    private final ServiceOrderMapper orderMapper;
    private final StaffService staffService;

    @Operation(summary = "工作台概览", description = "待接单数/进行中数/今日完成/累计完成/累计收入/评分/认证状态")
    @GetMapping
    public Result<Map<String, Object>> overview() {
        Long staffId = UserContext.requireStaff().getId();
        Map<String, Object> data = new LinkedHashMap<>(orderService.staffWorkbench(staffId));
        StaffProfile profile = staffService.myProfile(staffId);
        data.put("avgRating", profile.getAvgRating());
        data.put("certStatus", profile.getCertStatus());
        data.put("status", profile.getStatus());
        return Result.ok(data);
    }

    @Operation(summary = "月度排期", description = "按天分组返回当月订单排期（时间/项目/宠物/客户/状态）")
    @GetMapping("/schedule")
    public Result<Map<String, List<OrderListVO>>> schedule(
            @Parameter(description = "月份 yyyy-MM", example = "2026-10")
            @RequestParam String month) {
        Long staffId = UserContext.requireStaff().getId();
        LocalDateTime start = LocalDateTime.parse(month + "-01T00:00:00");
        LocalDateTime end = start.plusMonths(1);
        List<ServiceOrder> orders = orderMapper.selectList(Wrappers.<ServiceOrder>lambdaQuery()
                .eq(ServiceOrder::getStaffId, staffId)
                .ge(ServiceOrder::getAppointmentTime, start)
                .lt(ServiceOrder::getAppointmentTime, end)
                .orderByAsc(ServiceOrder::getAppointmentTime));

        Map<String, List<OrderListVO>> grouped = new LinkedHashMap<>();
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (ServiceOrder order : orders) {
            String day = order.getAppointmentTime().format(dayFmt);
            grouped.computeIfAbsent(day, k -> new ArrayList<>()).add(orderService.toListVO(order));
        }
        return Result.ok(grouped);
    }
}
