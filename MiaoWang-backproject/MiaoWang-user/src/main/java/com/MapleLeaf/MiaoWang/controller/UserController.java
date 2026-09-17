package com.MapleLeaf.MiaoWang.controller;


import cn.hutool.core.bean.BeanUtil;
import com.MapleLeaf.MiaoWang.common.convention.result.Result;
import com.MapleLeaf.MiaoWang.common.convention.result.Results;
import com.MapleLeaf.MiaoWang.dto.req.PhoneLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.SmsSendReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserRegisterReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserPasswordUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserPhoneUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.CaptchaRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserActualRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserLoginRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserRespDTO;
import com.MapleLeaf.MiaoWang.service.CaptchaService;
import com.MapleLeaf.MiaoWang.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CaptchaService captchaService;

    /**
     *根据用户名查询用户信息
     */
    @GetMapping("/mapleleaf/miaowang/v1/user/{username}")
    public Result<UserRespDTO> getUserByUsername(@PathVariable("username") String username){
        return Results.success(userService.getUserByUsername(username));
    }

    /**
     *根据用户名查询无脱敏用户信息
     */
    @GetMapping("/mapleleaf/miaowang/v1/actual/user/{username}")
    public Result<UserActualRespDTO> getActualUserByUsername(@PathVariable("username") String username){
        return Results.success(BeanUtil.toBean(userService.getUserByUsername(username), UserActualRespDTO.class));
    }


    /**
     * 查询用户名是否存在
     */
    @GetMapping("/mapleleaf/miaowang/v1/user/has-username")
    public Result<Boolean> hasUserName(@RequestParam("username") String username){
        return Results.success(userService.hasUserName(username));
    }

    /**
     * 注册用户
     */
    @PostMapping("/mapleleaf/miaowang/v1/user")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam){
        userService.register(requestParam);
        return Results.success();
    }

    /**
     * 修改用户
     */
    @PutMapping("/mapleleaf/miaowang/v1/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam){
        userService.update(requestParam);
        return Results.success();
    }

    /**
     * 修改密码（校验原密码；成功后登录态失效，需要重新登录）
     * <p>
     * 改谁由请求头 username/token 还原出的 UserContext 决定，body 里不传 username。
     */
    @PutMapping("/mapleleaf/miaowang/v1/user/password")
    public Result<Void> updatePassword(@RequestBody UserPasswordUpdateReqDTO requestParam){
        userService.updatePassword(requestParam);
        return Results.success();
    }

    /**
     * 换绑手机号（发到新手机号上的短信验证码，scene=change_phone）
     */
    @PutMapping("/mapleleaf/miaowang/v1/user/phone")
    public Result<Void> updatePhone(@RequestBody UserPhoneUpdateReqDTO requestParam){
        userService.updatePhone(requestParam);
        return Results.success();
    }

    /**
     * 用户登录
     */
    @PostMapping("/mapleleaf/miaowang/v1/user/login")
    public Result<UserLoginRespDTO> login (@RequestBody UserLoginReqDTO requestParam){
        return Results.success(userService.login(requestParam));
    }

    /**
     * 检查用户是否登录
     */
    @GetMapping("/mapleleaf/miaowang/v1/user/check-login")
    public Result<Boolean> checkLogin(@RequestParam("username") String username,@RequestParam("token") String token){
        return Results.success(userService.checkLogin(username,token));
    }

    /**
     * 用户退出登录
     */
    @DeleteMapping("/mapleleaf/miaowang/v1/user/logout")
    public Result<Void> logout (@RequestParam("username") String username,@RequestParam("token") String token){
        userService.logout(username,token);
        return Results.success();

    }

    /**
     * 上传头像（multipart/form-data，字段名 file）
     * <p>
     * 改谁的头像由请求头 username/token 还原出的 UserContext 决定，前端不传"改谁"，
     * 从结构上避免"改别人头像"这类越权。
     */
    @PostMapping("/mapleleaf/miaowang/v1/user/avatar")
    public Result<String> uploadAvatar(@RequestParam("file") MultipartFile file){
        return Results.success(userService.uploadAvatar(file));
    }

    /**
     * 获取图形验证码（真人校验）
     * <p>
     * 发送短信验证码前必须先过这道，否则短信接口能被脚本刷爆
     */
    @GetMapping("/mapleleaf/miaowang/v1/user/captcha")
    public Result<CaptchaRespDTO> captcha(){
        return Results.success(captchaService.generate());
    }

    /**
     * 发送短信验证码
     * <p>
     * 顺序：图形验证码 → 间隔/每日限流 → 生成 6 位码存 Redis → 交给 SmsSender（当前是 mock，只打日志）
     */
    @PostMapping("/mapleleaf/miaowang/v1/user/sms")
    public Result<Void> sendSmsCode(@RequestBody SmsSendReqDTO requestParam){
        userService.sendSmsCode(requestParam);
        return Results.success();
    }

    /**
     * 手机号 + 短信验证码登录（手机号未注册则自动注册）
     */
    @PostMapping("/mapleleaf/miaowang/v1/user/login/phone")
    public Result<UserLoginRespDTO> loginByPhone(@RequestBody PhoneLoginReqDTO requestParam){
        return Results.success(userService.loginByPhone(requestParam));
    }






}
