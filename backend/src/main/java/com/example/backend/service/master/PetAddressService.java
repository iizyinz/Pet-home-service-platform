package com.example.backend.service.master;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.backend.common.error.BizException;
import com.example.backend.common.error.ErrorCode;
import com.example.backend.dto.address.AddressRequest;
import com.example.backend.dto.pet.PetRequest;
import com.example.backend.entity.Pet;
import com.example.backend.entity.UserAddress;
import com.example.backend.mapper.PetMapper;
import com.example.backend.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 宠物档案 + 上门地址（本人归属维护）
 */
@Service
@RequiredArgsConstructor
public class PetAddressService {

    private final PetMapper petMapper;
    private final UserAddressMapper addressMapper;

    // ---------------- 宠物档案 ----------------

    public List<Pet> listPets(Long userId) {
        return petMapper.selectList(Wrappers.<Pet>lambdaQuery()
                .eq(Pet::getUserId, userId).orderByDesc(Pet::getId));
    }

    public Pet getPet(Long userId, Long petId) {
        Pet pet = petMapper.selectById(petId);
        if (pet == null || !pet.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PET_NOT_FOUND);
        }
        return pet;
    }

    public Pet createPet(Long userId, PetRequest req) {
        Pet pet = new Pet();
        applyPet(pet, req);
        pet.setUserId(userId);
        petMapper.insert(pet);
        return pet;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updatePet(Long userId, Long petId, PetRequest req) {
        Pet pet = getPet(userId, petId);
        applyPet(pet, req);
        petMapper.updateById(pet);
    }

    @Transactional(rollbackFor = Exception.class)
    public void deletePet(Long userId, Long petId) {
        Pet pet = getPet(userId, petId); // 校验归属
        petMapper.deleteById(petId);     // 逻辑删除
    }

    private void applyPet(Pet pet, PetRequest req) {
        pet.setName(req.getName());
        pet.setBreed(req.getBreed());
        pet.setAge(req.getAge());
        pet.setWeightKg(req.getWeightKg());
        pet.setHealthInfo(req.getHealthInfo());
        pet.setSpecialNeed(req.getSpecialNeed());
        pet.setAvatar(req.getAvatar());
    }

    // ---------------- 上门地址 ----------------

    public List<UserAddress> listAddresses(Long userId) {
        return addressMapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault).orderByDesc(UserAddress::getId));
    }

    public UserAddress getAddress(Long userId, Long addressId) {
        UserAddress address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.ADDRESS_NOT_FOUND);
        }
        return address;
    }

    @Transactional(rollbackFor = Exception.class)
    public UserAddress createAddress(Long userId, AddressRequest req) {
        UserAddress address = new UserAddress();
        applyAddress(address, req);
        address.setUserId(userId);
        addressMapper.insert(address);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearOtherDefault(userId, address.getId());
        }
        return address;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateAddress(Long userId, Long addressId, AddressRequest req) {
        UserAddress address = getAddress(userId, addressId);
        applyAddress(address, req);
        addressMapper.updateById(address);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearOtherDefault(userId, address.getId());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteAddress(Long userId, Long addressId) {
        getAddress(userId, addressId);
        addressMapper.deleteById(addressId);
    }

    private void applyAddress(UserAddress address, AddressRequest req) {
        address.setContactName(req.getContactName());
        address.setContactPhone(req.getContactPhone());
        address.setRegion(req.getRegion());
        address.setDetail(req.getDetail());
        address.setIsDefault(req.getIsDefault() == null ? 0 : req.getIsDefault());
    }

    /** 唯一默认地址 */
    private void clearOtherDefault(Long userId, Long keepId) {
        List<UserAddress> all = addressMapper.selectList(Wrappers.<UserAddress>lambdaQuery()
                .eq(UserAddress::getUserId, userId).eq(UserAddress::getIsDefault, 1));
        for (UserAddress a : all) {
            if (!a.getId().equals(keepId)) {
                a.setIsDefault(0);
                addressMapper.updateById(a);
            }
        }
    }
}
