package com.MapleLeaf.MiaoWang.support;


import cn.hutool.core.util.StrUtil;
import com.MapleLeaf.MiaoWang.config.VipPayProperties;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 支付宝密钥格式化工具（把"用户怎么填"和"SDK 要什么"对起来）
 *
 * <p>用户在控制台/密钥工具里拿到的密钥有好几种形态，直接塞给 SDK 会以
 * "验签失败""InvalidKeySpecException"这种看不懂的英文错误收场，所以统一在这里归一化：
 * <ol>
 *   <li><b>PEM 文本</b>：带 {@code -----BEGIN/END-----} 与换行 —— SDK 只要 base64 本体</li>
 *   <li><b>一整行 base64</b>：从控制台复制支付宝公钥就是这样</li>
 *   <li><b>带引号或首尾空格/换行</b>：从 yml / json / 记事本里复制常见</li>
 * </ol>
 *
 * <p>第二个坑是 <b>PKCS#1 与 PKCS#8</b>：{@code openssl genrsa} 生成的是 PKCS#1
 * （PEM 头 {@code -----BEGIN RSA PRIVATE KEY-----}），而 Java 只认 PKCS#8
 * （{@code -----BEGIN PRIVATE KEY-----}）。拿到 PKCS#1 时这里会自动补一个 PKCS#8 头转过去
 * （DER 结构是固定的，不引 BouncyCastle、不落盘）；万一转不了，抛的也是中文可操作提示。
 *
 * <p><b>这个类不存密钥、不打日志</b>：只做格式转换与判定，调用方负责别把密钥写进日志。
 */
public final class AlipayKeySupport {

    /**
     * PEM 头尾标记（私钥/公钥各两种编码）
     */
    private static final String[] PEM_MARKERS = {
            "-----BEGIN PRIVATE KEY-----", "-----END PRIVATE KEY-----",
            "-----BEGIN RSA PRIVATE KEY-----", "-----END RSA PRIVATE KEY-----",
            "-----BEGIN ENCRYPTED PRIVATE KEY-----", "-----END ENCRYPTED PRIVATE KEY-----",
            "-----BEGIN PUBLIC KEY-----", "-----END PUBLIC KEY-----",
            "-----BEGIN RSA PUBLIC KEY-----", "-----END RSA PUBLIC KEY-----",
    };

    /**
     * rsaEncryption 的 AlgorithmIdentifier DER（{@code SEQUENCE { OID 1.2.840.113549.1.1.1, NULL }}），
     * 转 PKCS#8 / X.509 时都要用
     */
    private static final byte[] RSA_ALGORITHM_ID = {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48,
            (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00,
    };

    /**
     * PKCS#8 的 version 字段（{@code INTEGER 0}）
     */
    private static final byte[] PKCS8_VERSION = {0x02, 0x01, 0x00};

    private AlipayKeySupport() {
    }

    /**
     * 去掉 PEM 头尾、所有空白换行与包裹引号，只留 base64 本体
     *
     * @return 归一化后的字符串；入参为 null 时返回空串（不抛异常，交给调用方判空）
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        // 从 yml/json 里复制过来常常带一层引号
        boolean quoted = value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")));
        if (quoted) {
            value = value.substring(1, value.length() - 1);
        }
        for (String marker : PEM_MARKERS) {
            value = value.replace(marker, "");
        }
        // 换行/制表/空格全去掉：base64 本体里不可能有空白
        return value.replaceAll("\\s", "");
    }

    /**
     * 归一化「应用私钥」：出来的一定是 <b>PKCS#8 的一行 base64</b>
     *
     * @throws IllegalArgumentException 格式不对时抛出，消息是中文可操作提示（可直接给用户看）
     */
    public static String normalizePrivateKey(String raw) {
        String base64 = normalize(raw);
        if (StrUtil.isBlank(base64)) {
            throw new IllegalArgumentException("支付宝应用私钥（vip.pay.alipay.merchantPrivateKey）为空，"
                    + "请填沙箱应用的「应用私钥」");
        }
        byte[] der = decode(base64, "应用私钥");
        if (isPkcs8PrivateKey(der)) {
            return Base64.getEncoder().encodeToString(der);
        }
        // 常见坑：openssl genrsa 给的是 PKCS#1，Java 只认 PKCS#8 —— 这里自动补头转过去，用户不用管
        byte[] converted = derSequence(concat(PKCS8_VERSION, RSA_ALGORITHM_ID, derTag((byte) 0x04, der)));
        if (isPkcs8PrivateKey(converted)) {
            return Base64.getEncoder().encodeToString(converted);
        }
        throw new IllegalArgumentException("支付宝应用私钥（vip.pay.alipay.merchantPrivateKey）格式不对："
                + "Java 只认 PKCS#8（PEM 头 -----BEGIN PRIVATE KEY-----），当前既不是 PKCS#8 也不是 PKCS#1。"
                + "请用支付宝密钥工具重新生成，或先转换："
                + "openssl pkcs8 -topk8 -inform PEM -in 原私钥.pem -outform PEM -nocrypt -out 私钥_pkcs8.pem");
    }

    /**
     * 归一化「支付宝公钥」：出来的一定是 <b>X.509（SPKI）的一行 base64</b>
     *
     * @throws IllegalArgumentException 格式不对时抛出，消息是中文可操作提示（可直接给用户看）
     */
    public static String normalizePublicKey(String raw) {
        String base64 = normalize(raw);
        if (StrUtil.isBlank(base64)) {
            throw new IllegalArgumentException("支付宝公钥（vip.pay.alipay.alipayPublicKey）为空，"
                    + "请填沙箱控制台里的「支付宝公钥」");
        }
        byte[] der = decode(base64, "支付宝公钥");
        if (isX509PublicKey(der)) {
            return Base64.getEncoder().encodeToString(der);
        }
        // 少数工具导出的是 PKCS#1 公钥（-----BEGIN RSA PUBLIC KEY-----），同样自动补头
        byte[] converted = derSequence(concat(RSA_ALGORITHM_ID,
                derTag((byte) 0x03, concat(new byte[]{0x00}, der))));
        if (isX509PublicKey(converted)) {
            return Base64.getEncoder().encodeToString(converted);
        }
        throw new IllegalArgumentException("支付宝公钥（vip.pay.alipay.alipayPublicKey）格式不对："
                + "应当填沙箱控制台里的「支付宝公钥」（一整行 base64，对应 PEM 头 -----BEGIN PUBLIC KEY-----）；"
                + "注意不要填成「应用公钥」，更不要填应用私钥");
    }

    /**
     * 配置自检：返回「中文问题清单」（空列表 = 通过）
     *
     * <p>只判定"能不能用"，<b>不抛异常</b> —— 启动时用它打一条可操作的日志；
     * 真正要收钱时渠道实现自己会 fail-closed 报 {@code B000533}。
     */
    public static List<String> collectProblems(VipPayProperties.Alipay alipay) {
        List<String> problems = new ArrayList<>();
        if (alipay == null) {
            problems.add("vip.pay.alipay 整块配置缺失");
            return problems;
        }
        if (StrUtil.isBlank(alipay.getGatewayUrl())) {
            problems.add("vip.pay.alipay.gatewayUrl 缺失（沙箱填 https://openapi-sandbox.dl.alipaydev.com/gateway.do）");
        }
        if (StrUtil.isBlank(alipay.getAppId())) {
            problems.add("vip.pay.alipay.appId 缺失（沙箱应用的 APPID，不是真实商户 APPID）");
        }
        if (StrUtil.isBlank(alipay.getMerchantPrivateKey())) {
            problems.add("vip.pay.alipay.merchantPrivateKey 缺失（应用私钥；PEM 或一行 base64 都行，工具会归一化）");
        } else {
            try {
                normalizePrivateKey(alipay.getMerchantPrivateKey());
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        if (StrUtil.isBlank(alipay.getAlipayPublicKey())) {
            problems.add("vip.pay.alipay.alipayPublicKey 缺失（支付宝公钥；控制台给的是一整行 base64）");
        } else {
            try {
                normalizePublicKey(alipay.getAlipayPublicKey());
            } catch (IllegalArgumentException e) {
                problems.add(e.getMessage());
            }
        }
        return problems;
    }

    private static byte[] decode(String base64, String what) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("支付宝" + what + "不是合法的 base64：请确认复制完整"
                    + "（带不带 -----BEGIN/END----- 头尾都可以，工具会自动清理头尾与换行）");
        }
    }

    private static boolean isPkcs8PrivateKey(byte[] der) {
        try {
            KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static boolean isX509PublicKey(byte[] der) {
        try {
            KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
            return true;
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] derSequence(byte[] content) {
        return derTag((byte) 0x30, content);
    }

    /**
     * 按 DER 规则加 tag 与长度（RSA 密钥的长度落在 1～3 字节形态里，够用且不需要引入 ASN.1 库）
     */
    private static byte[] derTag(byte tag, byte[] content) {
        int len = content.length;
        byte[] lengthBytes;
        if (len < 0x80) {
            lengthBytes = new byte[]{(byte) len};
        } else if (len <= 0xFF) {
            lengthBytes = new byte[]{(byte) 0x81, (byte) len};
        } else if (len <= 0xFFFF) {
            lengthBytes = new byte[]{(byte) 0x82, (byte) (len >> 8), (byte) len};
        } else {
            lengthBytes = new byte[]{(byte) 0x83, (byte) (len >> 16), (byte) (len >> 8), (byte) len};
        }
        byte[] out = new byte[1 + lengthBytes.length + content.length];
        out[0] = tag;
        System.arraycopy(lengthBytes, 0, out, 1, lengthBytes.length);
        System.arraycopy(content, 0, out, 1 + lengthBytes.length, content.length);
        return out;
    }

    private static byte[] concat(byte[]... arrays) {
        int size = 0;
        for (byte[] array : arrays) {
            size += array.length;
        }
        byte[] out = new byte[size];
        int offset = 0;
        for (byte[] array : arrays) {
            System.arraycopy(array, 0, out, offset, array.length);
            offset += array.length;
        }
        return out;
    }
}