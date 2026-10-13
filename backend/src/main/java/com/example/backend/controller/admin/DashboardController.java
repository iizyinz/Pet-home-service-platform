package com.example.backend.controller.admin;

import com.example.backend.common.api.Result;
import com.example.backend.service.admin.DashboardService;
import com.example.backend.service.admin.WordCloudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 管理端-数据看板与评价词云（仅 ADMIN）
 */
@Tag(name = "管理端/数据看板", description = "仅 ADMIN：运营指标、趋势、分布、排行、词云")
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final WordCloudService wordCloudService;

    @Operation(summary = "核心指标卡", description = "累计/今日订单、平台收入、用户/服务师/宠物数、满意率")
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        return Result.ok(dashboardService.overview());
    }

    @Operation(summary = "近 N 天订单趋势", description = "折线图数据，按日聚合")
    @GetMapping("/order-trend")
    public Result<List<Map<String, Object>>> orderTrend(
            @Parameter(description = "天数", example = "7") @RequestParam(defaultValue = "7") int days) {
        return Result.ok(dashboardService.orderTrend(days));
    }

    @Operation(summary = "订单状态分布", description = "饼图数据")
    @GetMapping("/status-dist")
    public Result<List<Map<String, Object>>> statusDist() {
        return Result.ok(dashboardService.statusDist());
    }

    @Operation(summary = "服务项目排行", description = "按已完成订单数 Top10")
    @GetMapping("/item-rank")
    public Result<List<Map<String, Object>>> itemRank() {
        return Result.ok(dashboardService.itemRank());
    }

    @Operation(summary = "服务人员接单排行", description = "按已完成订单数 Top10")
    @GetMapping("/staff-rank")
    public Result<List<Map<String, Object>>> staffRank() {
        return Result.ok(dashboardService.staffRank());
    }

    @Operation(summary = "评分分布与满意率", description = "各星级数量/占比")
    @GetMapping("/rating-dist")
    public Result<List<Map<String, Object>>> ratingDist() {
        return Result.ok(dashboardService.ratingDist());
    }

    @Operation(summary = "评价词云", description = "HanLP 分词去停用词，按词频取 Top80")
    @GetMapping("/wordcloud")
    public Result<List<Map<String, Object>>> wordcloud(
            @Parameter(description = "返回词数（最大 80）", example = "80")
            @RequestParam(defaultValue = "80") int topN) {
        return Result.ok(wordCloudService.wordCloud(topN));
    }
}
