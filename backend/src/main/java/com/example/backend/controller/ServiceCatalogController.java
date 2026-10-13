package com.example.backend.controller;

import com.example.backend.common.api.Result;
import com.example.backend.entity.ServiceArea;
import com.example.backend.entity.ServiceItem;
import com.example.backend.entity.ServiceType;
import com.example.backend.common.api.PageResult;
import com.example.backend.service.master.ServiceCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务浏览（公开只读，需登录角色均可访问）
 */
@Tag(name = "服务浏览", description = "服务类型/区域/服务项目查询")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ServiceCatalogController {

    private final ServiceCatalogService catalogService;

    @Operation(summary = "服务类型列表", description = "首页分类入口，按排序返回启用类型")
    @GetMapping("/service-types")
    public Result<List<ServiceType>> types() {
        return Result.ok(catalogService.listTypes());
    }

    @Operation(summary = "服务区域列表", description = "用于服务人员筛选")
    @GetMapping("/service-areas")
    public Result<List<ServiceArea>> areas() {
        return Result.ok(catalogService.listAreas());
    }

    @Operation(summary = "服务项目列表", description = "支持按类型 typeId、关键词 keyword 筛选，仅返回上架项目")
    @GetMapping("/service-items")
    public Result<PageResult<ServiceItem>> items(
            @Parameter(description = "服务类型ID") @RequestParam(required = false) Long typeId,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        return Result.ok(catalogService.pageItems(typeId, keyword, page, size));
    }

    @Operation(summary = "服务项目详情", description = "价格/时长/适用品种/注意事项")
    @GetMapping("/service-items/{id}")
    public Result<ServiceItem> itemDetail(@PathVariable Long id) {
        return Result.ok(catalogService.getItem(id));
    }
}
