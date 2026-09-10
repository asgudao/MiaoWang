package com.MapleLeaf.MiaoWang.controller;


import com.MapleLeaf.MiaoWang.common.result.JsonResult;
import com.MapleLeaf.MiaoWang.dto.req.UserLoginReqDTO;
import com.MapleLeaf.MiaoWang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("MapleLeaf/MiaoWang/v1/user/login")
    public JsonResult login(@RequestBody UserLoginReqDTO requestParam) {
        return userService.login(requestParam);
    }






}
