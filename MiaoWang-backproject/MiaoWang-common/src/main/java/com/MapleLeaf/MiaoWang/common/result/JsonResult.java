package com.MapleLeaf.MiaoWang.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回前端的Json对象
 *
 * @param <T>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JsonResult<T> {
    //0 代表成功，大于0代表失败,-1代表登录过期
    private int state;
    //失败的提示信息
    private String message;
    //返回的数据
    private T data;

    public static JsonResult success(String message) {
        return new JsonResult(0, message, null);
    }

    public static <T> JsonResult success(T data) {
        return new JsonResult(0, null, data);
    }

    public static <T> JsonResult success(String message, T data) {
        return new JsonResult(0, message, data);
    }

    public static JsonResult fail(int state, String message) {
        return new JsonResult(state, message, null);
    }

    public static JsonResult fail(String message) {
        return new JsonResult(1, message, null);
    }
}
