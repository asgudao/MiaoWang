package com.MapleLeaf.MiaoWang.service;

import com.MapleLeaf.MiaoWang.dao.entity.UserDO;
import com.MapleLeaf.MiaoWang.dto.req.PhoneLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.SmsSendReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserLoginReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserRegisterReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserPasswordUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserPhoneUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.req.UserUpdateReqDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserLoginRespDTO;
import com.MapleLeaf.MiaoWang.dto.resp.UserRespDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;


public interface UserService extends IService<UserDO> {


    /**
     * 根据用户名查询用户信息

     * @param username 用户名
     * @return 用户返回实体
     *
     */
    UserRespDTO getUserByUsername(String username);

    /**
     * 查询用户名是否已存在
     * @param username 用户名
     * @return 用户名存在返回true，不存在返回false
     */
    Boolean hasUserName(String username);

    /**
     * 注册用户
     * @param requestParam 注册用户请求参数
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 根据用户名修改用户
     * @param requestParam 修改用户请求参数
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 修改密码：校验原密码，成功后清掉登录态（需要重新登录）
     */
    void updatePassword(UserPasswordUpdateReqDTO requestParam);

    /**
     * 换绑手机号：新号要过短信验证码（scene=change_phone）
     */
    void updatePhone(UserPhoneUpdateReqDTO requestParam);

    /**
     * 用户登录
     * @param requestParam 用户登录请求参数
     * @return 用户登录返回参数 Token
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    /**
     * 检查用户是否登录
     * @param username 用户名
     * @param token 用户登录 Token
     * @return 用户是否登录标识
     */
    Boolean checkLogin(String username,String token);

    /**
     * 退出登录
     * @param username 用户名
     * @param token 用户登录 Token
     */
    void logout(String username, String token);

    /**
     * 上传当前登录用户的头像
     * <p>
     * 改谁的头像由 UserContext（请求头 username/token 还原）决定，不接收前端传入的用户名
     *
     * @param file 头像图片
     * @return 头像访问 URL
     */
    String uploadAvatar(MultipartFile file);

    /**
     * 发送短信验证码
     * <p>
     * 发送前有两道闸门：图形验证码（真人校验，校验后即作废）+ 频率限制（间隔 + 每日上限）
     *
     * @param requestParam 手机号 + 场景 + 图形验证码
     */
    void sendSmsCode(SmsSendReqDTO requestParam);

    /**
     * 手机号 + 短信验证码登录
     * <p>
     * 手机号没有注册过时自动注册（注册与登录共用一个入口）
     *
     * @param requestParam 手机号 + 短信验证码
     * @return 用户登录返回参数 Token
     */
    UserLoginRespDTO loginByPhone(PhoneLoginReqDTO requestParam);
}
