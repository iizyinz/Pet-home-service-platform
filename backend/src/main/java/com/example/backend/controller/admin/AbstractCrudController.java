package com.example.backend.controller.admin;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.backend.common.api.PageResult;
import com.example.backend.common.api.Result;
import com.example.backend.common.audit.AuditService;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.ResolvableType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 管理端通用 CRUD 基类（GET 分页/详情、POST 新增、PUT 修改、DELETE 删除）。
 * 写操作统一写入审计日志；子类仅需提供 target 名与可选关键词检索。
 * 子类 @RequestMapping("...") 指定资源路径，均位于 /api/admin 下（仅 ADMIN 可访问）。
 */
public abstract class AbstractCrudController<T, M extends BaseMapper<T>> {

    protected final M mapper;
    protected final AuditService auditService;
    private final Class<T> entityClass;

    @SuppressWarnings("unchecked")
    protected AbstractCrudController(M mapper, AuditService auditService) {
        this.mapper = mapper;
        this.auditService = auditService;
        this.entityClass = (Class<T>) ResolvableType.forClass(getClass())
                .as(AbstractCrudController.class).getGeneric(0).resolve();
    }

    /** 审计对象名，如 service-item */
    protected abstract String target();

    /** 关键词检索条件（子类按需重写，如 name like keyword） */
    protected com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<T> search(String keyword) {
        return Wrappers.lambdaQuery();
    }

    @Operation(summary = "分页查询", description = "支持关键词检索，按 ID 倒序")
    @GetMapping
    public Result<PageResult<T>> page(
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "页码", example = "1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页条数", example = "10") @RequestParam(defaultValue = "10") int size) {
        IPage<T> result = mapper.selectPage(new Page<>(page, size), search(keyword));
        return Result.ok(PageResult.of(result.getRecords(), result.getTotal(), page, size));
    }

    @Operation(summary = "详情")
    @GetMapping("/{id}")
    public Result<T> detail(@PathVariable Long id) {
        T entity = mapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据不存在");
        }
        return Result.ok(entity);
    }

    @Operation(summary = "新增")
    @PostMapping
    public Result<T> create(@Valid @RequestBody T entity) {
        mapper.insert(entity);
        auditService.record("create", target(), String.valueOf(idOf(entity)));
        return Result.ok(entity);
    }

    @Operation(summary = "修改")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody T entity) {
        setId(entity, id);
        int rows = mapper.updateById(entity);
        if (rows == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "数据不存在");
        }
        auditService.record("update", target(), "id=" + id);
        return Result.ok();
    }

    @Operation(summary = "删除", description = "存在关联数据时由子类校验后拒绝")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mapper.deleteById(id);
        auditService.record("delete", target(), "id=" + id);
        return Result.ok();
    }

    private Object idOf(T entity) {
        return new BeanWrapperImpl(entity).getPropertyValue(keyProperty());
    }

    private void setId(T entity, Long id) {
        new BeanWrapperImpl(entity).setPropertyValue(keyProperty(), id);
    }

    private String keyProperty() {
        var tableInfo = com.baomidou.mybatisplus.core.metadata.TableInfoHelper.getTableInfo(entityClass);
        return tableInfo == null ? "id" : tableInfo.getKeyProperty();
    }
}
