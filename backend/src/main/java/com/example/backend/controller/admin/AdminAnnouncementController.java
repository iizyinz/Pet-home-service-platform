package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.audit.AuditService;
import com.example.backend.entity.Announcement;
import com.example.backend.mapper.AnnouncementMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 管理端-系统公告 CRUD */
@Tag(name = "管理端/系统公告", description = "仅 ADMIN：公告 CRUD（审计）")
@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController extends AbstractCrudController<Announcement, AnnouncementMapper> {

    public AdminAnnouncementController(AnnouncementMapper mapper, AuditService auditService) {
        super(mapper, auditService);
    }

    @Override
    protected String target() {
        return "announcement";
    }

    @Override
    protected LambdaQueryWrapper<Announcement> search(String keyword) {
        return Wrappers.<Announcement>lambdaQuery()
                .like(keyword != null && !keyword.isBlank(), Announcement::getTitle, keyword)
                .orderByDesc(Announcement::getId);
    }
}
