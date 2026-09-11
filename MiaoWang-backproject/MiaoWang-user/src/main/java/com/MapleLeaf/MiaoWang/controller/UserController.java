package com.MapleLeaf.MiaoWang.controller;


import cn.hutool.core.bean.BeanUtil;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.UserLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserRegisterReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserActualRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserLoginRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserRespDTO;
import com.MapleLeaf.MiaoWang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     *根据用户名查询用户信息
     */
    @GetMapping("/MapleLeaf/MiaoWang/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username){
        return Results.success(userService.getUserByUsername(username));
    }

    /**
     *根据用户名查询无脱敏用户信息
     */
    @GetMapping("/MapleLeaf/MiaoWang/v1/actual/user/{username}")
    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username){
        return Results.success(BeanUtil.toBean(userService.getUserByUsername(username), UserActualRespDTO.class));
    }


    /**
     * 查询用户名是否存在
     */
    @GetMapping("/MapleLeaf/MiaoWang/v1/user/has-username")
    public Result<Boolean> hasUserName(@RequestParam("username") String username){
        return Results.success(userService.hasUserName(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/MapleLeaf/MiaoWang/v1/user")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam){
        userService.register(requestParam);
        return Results.success();
    }

    /**
     * 修改用户
     */
    @PutMapping("/MapleLeaf/MiaoWang/v1/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam){
        userService.update(requestParam);
        return Results.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/MapleLeaf/MiaoWang/v1/user/login")
    public Result<UserLoginRespDTO> login (@RequestBody UserLoginReqDTO requestParam){
        return Results.success(userService.login(requestParam));
    }

    /**
     * 检查用户是否登录
     */
    @GetMapping("/MapleLeaf/MiaoWang/v1/user/check-login")
    public Result<Boolean> checkLogin(@RequestParam("username") String username,@RequestParam("token") String token){
        return Results.success(userService.checkLogin(username,token));
    }

    /**
     * 用户退出登录
     */
    @DeleteMapping("/MapleLeaf/MiaoWang/v1/user/logout")
    public Result<Void> logout (@RequestParam("username") String username,@RequestParam("token") String token){
        userService.logout(username,token);
        return Results.success();

    }






}
