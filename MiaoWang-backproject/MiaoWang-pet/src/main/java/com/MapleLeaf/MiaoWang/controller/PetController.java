package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.PetCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.PetUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.MapleLeaf.MiaoWang.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PetController {
    private final PetService petService;

    @GetMapping("/mapleleaf/miaowang/v1/pet")
    public Result<List<PetRespDTO>> getMyPets() {
        return Results.success(petService.getMyPets());
    }


    @PostMapping("/mapleleaf/miaowang/v1/pet")
    public Result<PetRespDTO> createPet(@RequestBody PetCreateReqDTO requestParam) {
        return Results.success(petService.createPetByUsername(requestParam));
    }

    @PutMapping("/mapleleaf/miaowang/v1/pet/{pid}")
    public Result<Void> updatePet(@PathVariable("pid") Long pid, @RequestBody PetUpdateReqDTO requestParam) {
        return Results.success(petService.updatePetByPid(pid, requestParam));
    }

    @DeleteMapping("/mapleleaf/miaowang/v1/pet/{pid}")
    public Result<Void> deletePet(@PathVariable("pid") Long pid) {
        return Results.success(petService.deletePetByPid(pid));
    }

}
