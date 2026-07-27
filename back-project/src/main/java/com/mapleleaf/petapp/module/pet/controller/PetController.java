package com.mapleleaf.petapp.module.pet.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mapleleaf.petapp.common.Result;
import com.mapleleaf.petapp.module.pet.entity.Breed;
import com.mapleleaf.petapp.module.pet.entity.Pet;
import com.mapleleaf.petapp.module.pet.service.BreedService;
import com.mapleleaf.petapp.module.pet.service.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PetController {
    private final PetService petService;
    private final BreedService breedService;

    @GetMapping("/pets")
    public Result<List<Pet>> listPets(@RequestParam(defaultValue = "1") Long userId) {
        return Result.ok(petService.list(new LambdaQueryWrapper<Pet>().eq(Pet::getUserId, userId)));
    }

    @PostMapping("/pets")
    public Result<Pet> addPet(@RequestBody Pet pet) {
        petService.save(pet);
        return Result.ok(pet);
    }

    @GetMapping("/pets/{id}")
    public Result<Pet> getPet(@PathVariable Long id) {
        return Result.ok(petService.getById(id));
    }

    @PutMapping("/pets/{id}")
    public Result<Void> updatePet(@PathVariable Long id, @RequestBody Pet pet) {
        pet.setId(id);
        petService.updateById(pet);
        return Result.ok();
    }

    @DeleteMapping("/pets/{id}")
    public Result<Void> deletePet(@PathVariable Long id) {
        petService.removeById(id);
        return Result.ok();
    }

    @GetMapping("/breeds")
    public Result<List<Breed>> listBreeds(@RequestParam(required = false) Integer species) {
        LambdaQueryWrapper<Breed> w = new LambdaQueryWrapper<>();
        if (species != null) w.eq(Breed::getSpecies, species);
        return Result.ok(breedService.list(w));
    }

    @GetMapping("/breeds/{id}")
    public Result<Breed> getBreed(@PathVariable Long id) {
        return Result.ok(breedService.getById(id));
    }
}
