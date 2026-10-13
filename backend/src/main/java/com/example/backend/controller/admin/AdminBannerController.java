package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.audit.AuditService;
import com.example.backend.entity.Banner;
import com.example.backend.mapper.BannerMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端-轮播图 CRUD */
@Tag(name = "管理端/轮播图", description = "仅 ADMIN：首页轮播图 CRUD（审计）")
@RestController
@RequestMapping("/api/admin/banners")
public class AdminBannerController extends AbstractCrudController<Banner, BannerMapper> {

    public AdminBannerController(BannerMapper mapper, AuditService auditService) {
        super(mapper, auditService);
    }

    @Override
    protected String target() {
        return "banner";
    }

    @Override
    protected LambdaQueryWrapper<Banner> search(String keyword) {
        return Wrappers.<Banner>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Banner::getTitle, keyword)
                .orderByAsc(Banner::getSort).orderByDesc(Banner::getId);
    }
}
