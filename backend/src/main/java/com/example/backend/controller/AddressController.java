package com.example.backend.controller;

import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.address.AddressRequest;
import com.example.backend.entity.UserAddress;
import com.example.backend.service.master.PetAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 上门地址管理（本人归属）
 */
@Tag(name = "上门地址", description = "用户维护本人上门地址（需登录）")
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final PetAddressService petAddressService;

    @Operation(summary = "我的地址列表", description = "默认地址排最前")
    @GetMapping
    public Result<List<UserAddress>> list() {
        return Result.ok(petAddressService.listAddresses(UserContext.requireUserId()));
    }

    @Operation(summary = "新增地址")
    @PostMapping
    public Result<UserAddress> create(@Valid @RequestBody AddressRequest request) {
        return Result.ok(petAddressService.createAddress(UserContext.requireUserId(), request));
    }

    @Operation(summary = "更新地址")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        petAddressService.updateAddress(UserContext.requireUserId(), id, request);
        return Result.ok();
    }

    @Operation(summary = "删除地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        petAddressService.deleteAddress(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
