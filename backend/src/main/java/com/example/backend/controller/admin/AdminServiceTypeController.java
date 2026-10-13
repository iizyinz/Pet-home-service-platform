package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.audit.AuditService;
import com.example.backend.entity.ServiceType;
import com.example.backend.mapper.ServiceTypeMapper;
import com.example.backend.service.master.ServiceCatalogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端-服务类型 CRUD（删除前校验关联项目） */
@Tag(name = "管理端/服务类型", description = "仅 ADMIN：服务类型 CRUD（审计）")
@RestController
@RequestMapping("/api/admin/service-types")
public class AdminServiceTypeController extends AbstractCrudController<ServiceType, ServiceTypeMapper> {

    private final ServiceCatalogService catalogService;

    public AdminServiceTypeController(ServiceTypeMapper mapper, AuditService auditService,
                                      ServiceCatalogService catalogService) {
        super(mapper, auditService);
        this.catalogService = catalogService;
    }

    @Override
    protected String target() {
        return "service-type";
    }

    @Override
    protected LambdaQueryWrapper<ServiceType> search(String keyword) {
        return Wrappers.<ServiceType>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), ServiceType::getName, keyword)
                .orderByAsc(ServiceType::getSort);
    }

    @Override
    @DeleteMapping("/{id}")
    public com.example.backend.common.api.Result<Void> delete(@PathVariable Long id) {
        catalogService.deleteTypeChecked(id);   // 存在关联项目时拒绝（2015）
        auditService.record("delete", target(), "id=" + id);
        return com.example.backend.common.api.Result.ok();
    }
}
