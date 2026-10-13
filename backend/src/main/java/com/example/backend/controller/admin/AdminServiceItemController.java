package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.audit.AuditService;
import com.example.backend.entity.ServiceItem;
import com.example.backend.mapper.ServiceItemMapper;
import com.example.backend.service.master.ServiceCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端-服务项目 CRUD（下架不影响历史订单快照）
 */
@Tag(name = "管理端/服务项目", description = "仅 ADMIN：服务项目 CRUD（审计）")
@RestController
@RequestMapping("/api/admin/service-items")
public class AdminServiceItemController extends AbstractCrudController<ServiceItem, ServiceItemMapper> {

    private final ServiceCatalogService catalogService;

    public AdminServiceItemController(ServiceItemMapper mapper, AuditService auditService,
                                      ServiceCatalogService catalogService) {
        super(mapper, auditService);
        this.catalogService = catalogService;
    }

    @Override
    protected String target() {
        return "service-item";
    }

    @Override
    protected LambdaQueryWrapper<ServiceItem> search(String keyword) {
        return Wrappers.<ServiceItem>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), ServiceItem::getName, keyword)
                .orderByDesc(ServiceItem::getId);
    }

    @Override
    @DeleteMapping("/{id}")
    public com.example.backend.common.api.Result<Void> delete(@PathVariable Long id) {
        // 删除不影响历史订单快照（订单存项目名/价格快照）
        return super.delete(id);
    }
}
