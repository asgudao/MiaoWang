package com.MapleLeaf.MiaoWang.controller;

import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.PetCreateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.PetRespDTO;
import com.MapleLeaf.MiaoWang.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PetController {
    private final PetService petService;

    @GetMapping("/mapleleaf/miaowang/v1/pet/{username}")
    public Result<List<PetRespDTO>> getPetByUsername(@PathVariable("username") String username) {
        return Results.success(petService.getPetByUsername(username));
    }


    @PostMapping("/mapleleaf/miaowang/v1/pet/create")
    public Result<Void> createPet(@RequestBody PetCreateReqDTO requestParam) {
        return Results.success(petService.createPetByUsername(requestParam));
    }


}
