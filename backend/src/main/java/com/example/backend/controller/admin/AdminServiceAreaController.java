package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.audit.AuditService;
import com.example.backend.entity.ServiceArea;
import com.example.backend.mapper.ServiceAreaMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端-服务区域 CRUD */
@Tag(name = "管理端/服务区域", description = "仅 ADMIN：服务区域 CRUD（审计）")
@RestController
@RequestMapping("/api/admin/service-areas")
public class AdminServiceAreaController extends AbstractCrudController<ServiceArea, ServiceAreaMapper> {

    public AdminServiceAreaController(ServiceAreaMapper mapper, AuditService auditService) {
        super(mapper, auditService);
    }

    @Override
    protected String target() {
        return "service-area";
    }

    @Override
    protected LambdaQueryWrapper<ServiceArea> search(String keyword) {
        return Wrappers.<ServiceArea>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), ServiceArea::getName, keyword)
                .orderByAsc(ServiceArea::getLevel).orderByAsc(ServiceArea::getId);
    }
}
