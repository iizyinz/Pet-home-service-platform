package com.example.backend.controller;

import com.example.backend.common.api.Result;
import com.example.backend.common.context.UserContext;
import com.example.backend.dto.address.AddressRequest;
import com.example.backend.dto.pet.PetRequest;
import com.example.backend.entity.Pet;
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
 * 宠物档案管理（本人归属，CRUD）
 */
@Tag(name = "宠物档案", description = "用户维护本人宠物档案（需登录）")
@RestController
@RequestMapping("/api/pets")
@RequiredArgsConstructor
public class PetController {

    private final PetAddressService petAddressService;

    @Operation(summary = "我的宠物列表")
    @GetMapping
    public Result<List<Pet>> list() {
        return Result.ok(petAddressService.listPets(UserContext.requireUserId()));
    }

    @Operation(summary = "宠物详情")
    @GetMapping("/{id}")
    public Result<Pet> detail(@PathVariable Long id) {
        return Result.ok(petAddressService.getPet(UserContext.requireUserId(), id));
    }

    @Operation(summary = "新增宠物档案")
    @PostMapping
    public Result<Pet> create(@Valid @RequestBody PetRequest request) {
        return Result.ok(petAddressService.createPet(UserContext.requireUserId(), request));
    }

    @Operation(summary = "更新宠物档案", description = "仅本人可改（归属校验，越权返回 403/2010）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody PetRequest request) {
        petAddressService.updatePet(UserContext.requireUserId(), id, request);
        return Result.ok();
    }

    @Operation(summary = "删除宠物档案（逻辑删除）")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        petAddressService.deletePet(UserContext.requireUserId(), id);
        return Result.ok();
    }
}
