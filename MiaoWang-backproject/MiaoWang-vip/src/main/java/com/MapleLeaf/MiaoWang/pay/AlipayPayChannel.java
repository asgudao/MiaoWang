package com.MapleLeaf.MiaoWang.pay;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.MapleLeaf.MiaoWang.common.convention.exception.ClientException;
import com.MapleLeaf.MiaoWang.config.VipPayProperties;
import com.MapleLeaf.MiaoWang.dao.entity.VipOrderDO;
import com.MapleLeaf.MiaoWang.enums.VipErrorCodeEnum;
import com.MapleLeaf.MiaoWang.enums.VipPayChannelEnum;
import com.MapleLeaf.MiaoWang.support.AlipayKeySupport;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayResponse;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 支付宝渠道（PC 网页支付 + 扫码支付 / 沙箱）
 *
 * <p>四件事的落点：
 * <ul>
 *   <li><b>下单</b>：两种<b>支付场景</b>（{@link PayChannel#SCENE_PAGE} / {@link PayChannel#SCENE_QR}）
 *       <ul>
 *         <li>{@code PAGE}（默认）：{@code alipay.trade.page.pay}，一次下单**同时**下发两种投递物
 *             <ul>
 *               <li>{@code payParams.payHtml} —— <b>无参</b> {@code pageExecute(request)} 的返回值，
 *                   是一段自动提交的 HTML 表单（POST 到网关），<b>新沙箱走这条</b></li>
 *               <li>{@code payParams.payUrl} —— {@code pageExecute(request, "GET")} 的返回值，
 *                   是一条可直接打开的 GET 链接，<b>只作旧沙箱/回退</b></li>
 *             </ul>
 *             为什么要两条：{@code openapi-sandbox.dl.alipaydev.com}（新版沙箱）是 <b>API 网关主机</b>，
 *             只接受 POST 的接口调用、不渲染收银台页面 —— 把 GET 链接直接 {@code window.open} 出去
 *             浏览器只会拿到支付宝的 <b>404</b>（真沙箱实测）。{@code alipay.trade.page.pay} 的官方集成方式
 *             本来就是表单自动提交，由网关再跳到收银台。详见 {@link #createPayment(VipOrderDO)} 里的注释
 *             （含 javap 核对到的重载签名与字节码依据）</li>
 *         <li>{@code QR}：{@code alipay.trade.precreate} 预下单，拿回一串<b>支付码字符串</b>
 *             （而不是支付地址），见 {@link #createQrPayment} 的注释</li>
 *       </ul>
 *       两种场景共用同一个 {@code out_trade_no}、金额、超时窗口，所以回调、查单对账、退款
 *       三条链路完全不用区分用户是"跳去收银台付的"还是"扫码付的"</li>
 *   <li><b>回调</b>：{@code AlipaySignature.rsaCheckV1} 验签，{@code TRADE_SUCCESS}/{@code TRADE_FINISHED} 视为成功</li>
 *   <li><b>退款</b>：{@code alipay.trade.refund}，用固定的 {@code out_request_no} 让渠道侧天然幂等</li>
 *   <li><b>查单</b>：{@code alipay.trade.query}。开发机在局域网内，支付宝的异步回调打不进来，
 *       支付结果只能靠 {@code return_url} 跳回 + 主动查单对账 —— 顺带把"回调会丢"这个真实问题也解决了</li>
 * </ul>
 *
 * <p><b>密钥一律来自 Nacos</b>（{@code vip.pay.alipay.*}），代码里只有字段位。关键项没配时
 * {@link #requireAlipayConfig()} 直接抛业务异常（fail-closed），不会 NPE、也不会静默降级到 mock。
 * 用户怎么填都能吃：PEM 文本（带头尾与换行）/ 一整行 base64 / 带引号都行，PKCS#1 与 PKCS#8 私钥
 * 也自动兼容 —— 这些都在 {@code AlipayKeySupport} 里做，启动时还会自检一次并打中文可操作日志
 * （见 {@link #selfCheckOnStartup()}）。
 *
 * <p>两处与支付宝协议有关的"坑"，这里都处理了：
 * <ul>
 *   <li>支付宝的异步通知<b>没有 nonce</b>，所以 {@link #notifyNonceRequired()} 返回 false：
 *       重复通知靠「订单已是已支付就幂等应答」+ 状态机 CAS 兜住</li>
 *   <li>支付宝要求成功应答是<b>纯文本 success</b>，所以 {@link #plainTextAck()} 返回 true，
 *       由 {@code VipPayNotifyController} 决定不套 {@code Result} JSON 信封</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlipayPayChannel implements PayChannel {

    /**
     * 报文格式与字符集：支付宝只认这两个值，做成常量而不是配置项，少一个能配错的地方
     */
    private static final String FORMAT = "json";

    private static final String CHARSET = "UTF-8";

    /**
     * PC 网页支付的固定产品码
     */
    private static final String PRODUCT_CODE = "FAST_INSTANT_TRADE_PAY";

    /**
     * 视为「钱已到账」的交易状态：TRADE_SUCCESS=交易成功，TRADE_FINISHED=交易结束（不可退款）
     */
    private static final String TRADE_SUCCESS = "TRADE_SUCCESS";

    private static final String TRADE_FINISHED = "TRADE_FINISHED";

    /**
     * 支付宝要求「处理成功」时回的纯文本（<b>小写</b>）
     */
    private static final String ACK_SUCCESS = "success";

    /**
     * 查单时"渠道侧不存在这笔交易"的子错误码：这不是故障，只是用户还没付款
     */
    private static final String SUB_CODE_TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    /**
     * 查单结果里代表"渠道侧没有这笔交易"的伪状态（不是支付宝返回的 trade_status，是我们自己标的）
     */
    private static final String STATUS_TRADE_NOT_EXIST = "TRADE_NOT_EXIST";

    /**
     * 退款幂等号前缀：同一订单的 {@code out_request_no} 固定不变，支付宝按它去重，
     * 所以「退款其实成功了、本地没记上、用户再点一次」不会重复退钱
     */
    private static final String REFUND_REQUEST_PREFIX = "RF";

    private final VipPayProperties vipPayProperties;

    @Override
    public String code() {
        return VipPayChannelEnum.ALIPAY.getCode();
    }

    @Override
    public Map<String, Object> createPayment(VipOrderDO orderDO) {
        VipPayProperties.Alipay alipay = requireAlipayConfig();
        String outTradeNo = orderDO.getOrderNo();
        BigDecimal payableAmount = payableAmount(orderDO);
        String subject = StrUtil.blankToDefault(orderDO.getPlanName(), "喵汪会员");

        JSONObject bizContent = new JSONObject();
        bizContent.set("out_trade_no", outTradeNo);
        // 收的是「应付额」而不是原价：有立减码时，渠道收到的金额必须和回调/查单的校验口径完全一致
        bizContent.set("total_amount", payableAmount.toPlainString());
        bizContent.set("subject", subject);
        bizContent.set("product_code", PRODUCT_CODE);
        // 让渠道侧的关单时间和本地待支付时长对齐：
        // 否则本地已按 30 分钟关单、用户却在渠道侧付成功 → 回调会被状态机拒掉（钱收了但没发会员）
        bizContent.set("timeout_express", vipPayProperties.getPendingMinutes() + "m");

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setBizContent(JSONUtil.toJsonStr(bizContent));
        if (StrUtil.isNotBlank(alipay.getNotifyUrl())) {
            request.setNotifyUrl(alipay.getNotifyUrl());
        } else {
            log.warn("支付宝渠道未配置 notifyUrl，下单不带异步通知：支付结果靠 return_url 跳回 + "
                    + "POST /vip/order/{orderNo}/sync 主动查单对账（开发机在局域网内属预期）");
        }
        if (StrUtil.isNotBlank(alipay.getReturnUrl())) {
            request.setReturnUrl(alipay.getReturnUrl());
        }
        try {
            AlipayClient alipayClient = client(alipay);
            /*
             * 两种都取，因为它们是「同一笔收银台下单」的两种投递方式，缺一条路就有一批用户打不开收银台：
             *
             * 1) 无参 pageExecute(request)：SDK 内部就是 pageExecute(request, "POST")。
             *    getBody() 拿到的是**一整段自动提交的 HTML 表单**
             *    （<form name="punchout_form" method="post" action="<网关>">…<script>document.forms[0].submit()</script>），
             *    页面的作用就是用 POST 把它交给网关，由网关再跳到收银台。
             *    —— 这是**新沙箱唯一能用的那条**：openapi-sandbox.dl.alipaydev.com 是 API 网关主机，
             *    只接受接口调用，不渲染收银台页面，把 GET 链接直接 window.open 出去会 404。
             * 2) pageExecute(request, "GET")：getBody() 拿到一条带签名 query 的完整 GET URL
             *    （旧沙箱能直接浏览器打开）。**保留它做回退**，老沙箱/老链接不受影响。
             *
             * 依据（javap 实测本地 jar com/alipay/sdk/alipay-sdk-java/4.34.0.ALL，不是凭记忆）：
             *   AlipayClient 上有两个重载：
             *     pageExecute(AlipayRequest<T>)                —— 无参重载，**没有** httpMethod 形参
             *     pageExecute(AlipayRequest<T>, String)        —— 带 httpMethod
             *   AbstractAlipayClient.pageExecute(request) 的字节码就是
             *     ldc "POST" → invokevirtual pageExecute(request, "POST")
             *   而 pageExecute(request, httpMethod) 里：
             *     httpMethod.equalsIgnoreCase("GET") 为真 → setBody(getRedirectUrl(holder))  ← 一条 URL
             *     否则                                   → setBody(WebUtils.buildForm(getRequestUrl(holder),
             *                                                        holder.getApplicationParams()))  ← HTML 表单
             *   WebUtils.buildForm 拼的正是上面那段 <form method="post" action="…"> + 隐藏域 +
             *   <script>document.forms[0].submit();</script>，所以无参重载的 getBody() 就是「表单 HTML」。
             *   两次调用都只在本地拼串+签名（pageExecute 不发网络请求），可以放心调两次。
             */
            AlipayTradePagePayResponse formResponse = alipayClient.pageExecute(request);
            String payHtml = formResponse == null ? null : formResponse.getBody();
            AlipayTradePagePayResponse getResponse = alipayClient.pageExecute(request, "GET");
            String payUrl = getResponse == null ? null : getResponse.getBody();

            if (StrUtil.isBlank(payHtml) && StrUtil.isBlank(payUrl)) {
                log.error("支付宝下单既没拿到收银台表单也没拿到支付地址：outTradeNo={}，表单失败原因={}，GET 失败原因={}",
                        outTradeNo, failReasonOf(formResponse), failReasonOf(getResponse));
                throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_ERROR);
            }
            if (StrUtil.isBlank(payHtml)) {
                // 只缺表单还能用旧沙箱那条路，但新沙箱会 404，所以必须留下痕迹好排查
                log.warn("支付宝下单没拿到收银台表单（payHtml 为空），只有 GET 链接：outTradeNo={}，"
                        + "失败原因={}（新沙箱下 GET 链接会 404，前端应提示用户走旧沙箱）",
                        outTradeNo, failReasonOf(formResponse));
            }
            // 字段风格对齐 MockPayChannel：渠道 / 订单号 / 金额是公共项，渠道特有项往后放
            Map<String, Object> payParams = new LinkedHashMap<>();
            payParams.put("channel", code());
            payParams.put("orderNo", outTradeNo);
            payParams.put("outTradeNo", outTradeNo);
            payParams.put("subject", subject);
            payParams.put("amount", payableAmount);
            // 老的 GET 链接：保留为回退（旧沙箱可直接打开），键名与形状都不动
            payParams.put("payUrl", StrUtil.blankToDefault(payUrl, ""));
            // 新的表单 HTML：前端把它写进新窗口自动提交，这才是新沙箱的收银台入口。
            // 这两个键**同时**下发，前端按"有 payHtml 优先用 payHtml"选路。
            payParams.put("payHtml", StrUtil.blankToDefault(payHtml, ""));
            payParams.put("tip", "用 payHtml（自动提交表单，POST 到网关）打开沙箱收银台："
                    + "新沙箱的 openapi-sandbox.dl.alipaydev.com 是 API 网关，不渲染收银台页面，"
                    + "直接打开 payUrl 那条 GET 链接会 404，它只作旧沙箱的回退。"
                    + "若异步回调收不到（局域网内属预期），"
                    + "调 POST /mapleleaf/miaowang/v1/vip/order/" + outTradeNo + "/sync 主动对账发货");
            return payParams;
        } catch (AlipayApiException e) {
            // 签不出来（私钥格式不对等）属于配置问题，不是用户的问题：记全日志、回笼统的渠道错误
            log.error("支付宝下单失败：outTradeNo={}", outTradeNo, e);
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_ERROR);
        }
    }

    /**
     * 按支付场景下单：{@code QR} 走预下单拿支付码，{@code PAGE}/空 走原来的网页支付
     *
     * <p><b>刻意不做"未知场景静默当 PAGE"</b>：前端如果把 {@code payScene} 拼错（比如写成
     * {@code qrcode} / {@code SCAN}），静默降级会变成"用户以为要扫码、结果被跳去收银台"这种
     * 很难查的体验问题；宁可现在就报一个能看懂的业务错误。
     */
    @Override
    public Map<String, Object> createPayment(VipOrderDO orderDO, String scene) {
        String sceneValue = StrUtil.isBlank(scene) ? SCENE_PAGE : StrUtil.trim(scene).toUpperCase();
        if (SCENE_PAGE.equals(sceneValue)) {
            return createPayment(orderDO);
        }
        if (SCENE_QR.equals(sceneValue)) {
            return createQrPayment(orderDO);
        }
        log.warn("支付宝渠道收到不支持的支付场景：scene={}，outTradeNo={}（可选值：{} / {}，不传按 {} 处理）",
                scene, orderDO.getOrderNo(), SCENE_QR, SCENE_PAGE, SCENE_PAGE);
        throw new ClientException("不支持的支付场景：" + scene + "（支付宝渠道可选 "
                + SCENE_QR + " 扫码支付 / " + SCENE_PAGE + " 网页支付）",
                VipErrorCodeEnum.VIP_PAY_CHANNEL_UNSUPPORTED);
    }

    /**
     * 扫码支付（当面付预下单）：{@code alipay.trade.precreate}
     *
     * <p><b>最容易误解的一点：{@code response.getQrCode()} 返回的是一串「支付码字符串」
     * （形如 {@code https://qr.alipay.com/xxxx}），不是图片地址、也不是二维码图片的 base64。</b>
     * 它必须由前端（或我们的页面）用二维码库渲染成图片给用户扫；直接塞进 {@code <img src>}
     * 是打不开的。这也是这里把字段名定为 {@code qrCode} 而不是 {@code qrCodeUrl} 的原因。
     *
     * <p>与 {@code PAGE} 场景共用同一套商户订单号 / 金额 / 超时窗口，所以：
     * 用户扫码付款后，支付宝的异步回调与 {@code POST /vip/order/{orderNo}/sync} 主动查单
     * 走的是<b>完全相同</b>的落地链路（验签 → 金额校验 → CAS → 发会员），一条代码都不用分叉。
     *
     * <p>{@code return_url} 在这里<b>刻意不设</b>：扫码支付没有"付完跳回我们页面"这一步
     * （用户在手机上付的，浏览器还停在我们这边），结果统一靠前端轮询 {@code /sync} 拿。
     */
    private Map<String, Object> createQrPayment(VipOrderDO orderDO) {
        VipPayProperties.Alipay alipay = requireAlipayConfig();
        String outTradeNo = orderDO.getOrderNo();
        BigDecimal payableAmount = payableAmount(orderDO);
        String subject = StrUtil.blankToDefault(orderDO.getPlanName(), "喵汪会员");

        JSONObject bizContent = new JSONObject();
        bizContent.set("out_trade_no", outTradeNo);
        // 和 PAGE 场景同一口径：收的是「应付额」，有立减码时渠道金额必须和回调/查单校验一致
        bizContent.set("total_amount", payableAmount.toPlainString());
        bizContent.set("subject", subject);
        // 与本地「待支付 30 分钟懒关单」窗口对齐，原因同 PAGE 场景：
        // 本地已关单而渠道侧还能付成功 → 回调被状态机拒掉（钱收了但没发会员）
        bizContent.set("timeout_express", vipPayProperties.getPendingMinutes() + "m");
        // 刻意不传 product_code：那是「网页支付」的产品码（FAST_INSTANT_TRADE_PAY），
        // 和当面付不是一回事；当面付有它自己的默认产品码，不传就用默认值。
        // （本机没有真密钥、也没法真调，所以这里是"不猜"：宁可留默认，也不硬塞一个可能被渠道拒的值）
        // 另外 SDK 的 AlipayTradePrecreateModel 里还有 qr_code_timeout_express（二维码自身有效期），
        // 这里只用 timeout_express 一个口径 —— 两个超时一起传时以谁为准没有实测过，同样不猜。

        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setBizContent(JSONUtil.toJsonStr(bizContent));
        if (StrUtil.isNotBlank(alipay.getNotifyUrl())) {
            request.setNotifyUrl(alipay.getNotifyUrl());
        } else {
            log.warn("支付宝渠道未配置 notifyUrl，扫码下单不带异步通知：支付结果靠 "
                    + "POST /vip/order/{orderNo}/sync 主动查单对账（开发机在局域网内属预期）");
        }
        try {
            AlipayTradePrecreateResponse response = client(alipay).execute(request);
            String qrCode = response == null ? null : response.getQrCode();
            if (StrUtil.isBlank(qrCode)) {
                // 不返回空码让前端自己发现：前端拿到空码只会渲染出一片空白，用户看到的是"页面坏了"
                log.error("支付宝扫码下单没拿到支付码：outTradeNo={}，失败原因={}", outTradeNo, failReasonOf(response));
                log.warn("若失败原因是「无权限 / 产品未开通」一类，请到支付宝沙箱控制台确认该应用已签约"
                        + "「当面付」（alipay.trade.precreate 属当面付产品），而不是去查密钥");
                throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_ERROR);
            }
            // 字段形状是给前端的契约，顺序照约定来：渠道 / 场景 / 订单号 / 支付码 / 档位名 / 金额 / 提示
            Map<String, Object> payParams = new LinkedHashMap<>();
            payParams.put("channel", code());
            payParams.put("scene", SCENE_QR);
            payParams.put("outTradeNo", outTradeNo);
            payParams.put("qrCode", qrCode);
            payParams.put("subject", subject);
            payParams.put("amount", payableAmount);
            payParams.put("tip", "用手机上的支付宝沙箱版扫这个码付款");
            return payParams;
        } catch (AlipayApiException e) {
            // 签不出来（私钥格式不对等）属于配置问题，不是用户的问题：记全日志、回笼统的渠道错误
            log.error("支付宝扫码下单失败：outTradeNo={}", outTradeNo, e);
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_ERROR);
        }
    }

    @Override
    public PayNotifyResult verifyAndParse(Map<String, String> params) {
        VipPayProperties.Alipay alipay = requireAlipayConfig();
        if (params == null || params.isEmpty()) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        boolean signPass;
        try {
            // 注意：rsaCheckV1 内部会把 sign / sign_type 从 map 里摘掉再拼待验签串，
            // 所以验完签之后不要再依赖这两个字段
            signPass = AlipaySignature.rsaCheckV1(params, alipayPublicKeyOf(alipay),
                    CHARSET, alipay.getSignType());
        } catch (AlipayApiException e) {
            // 公钥格式不对、签名算法不支持等：一律按验签失败处理，绝不能放过
            log.error("支付宝回调验签异常：outTradeNo={}", params.get("out_trade_no"), e);
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        if (!signPass) {
            log.warn("支付宝回调验签不通过：outTradeNo={}，tradeStatus={}",
                    params.get("out_trade_no"), params.get("trade_status"));
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_SIGN_INVALID);
        }
        String tradeStatus = StrUtil.trim(params.get("trade_status"));
        boolean success = isPaidStatus(tradeStatus);
        // 金额按「订单总额」比对：回调里的 total_amount 就是我们下单时传的 payableAmount，
        // 真正的金额校验在 VipOrderServiceImpl 里（那儿才知道应付额），这里只负责如实上报
        return new PayNotifyResult(code(), params.get("out_trade_no"), params.get("trade_no"),
                parseAmount(params.get("total_amount")), success, success ? null : tradeStatus);
    }

    @Override
    public PayQueryResult queryTrade(String outTradeNo) {
        VipPayProperties.Alipay alipay = requireAlipayConfig();
        if (StrUtil.isBlank(outTradeNo)) {
            return null;
        }
        JSONObject bizContent = new JSONObject();
        bizContent.set("out_trade_no", outTradeNo);
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(JSONUtil.toJsonStr(bizContent));

        AlipayTradeQueryResponse response;
        try {
            response = client(alipay).execute(request);
        } catch (AlipayApiException e) {
            log.error("支付宝查单调用异常：outTradeNo={}", outTradeNo, e);
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_ERROR);
        }
        if (response == null) {
            log.error("支付宝查单没有应答：outTradeNo={}", outTradeNo);
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_ERROR);
        }
        if (!response.isSuccess()) {
            if (SUB_CODE_TRADE_NOT_EXIST.equalsIgnoreCase(response.getSubCode())) {
                // 渠道侧还没有这笔交易：说明用户还没付款，这是正常情况而不是故障
                log.info("支付宝查单：渠道侧暂无交易，outTradeNo={}", outTradeNo);
                return new PayQueryResult(code(), outTradeNo, null, null, false, STATUS_TRADE_NOT_EXIST);
            }
            log.error("支付宝查单失败：outTradeNo={}，失败原因={}", outTradeNo, failReasonOf(response));
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_ERROR);
        }
        String tradeStatus = response.getTradeStatus();
        return new PayQueryResult(code(), outTradeNo, response.getTradeNo(),
                parseAmount(response.getTotalAmount()), isPaidStatus(tradeStatus), tradeStatus);
    }

    /**
     * 退款：调 {@code alipay.trade.refund}
     *
     * <p>渠道明确拒绝（如已全额退过、交易不存在）时返回 {@code success=false} + 原因，
     * 由业务侧决定报什么错；而<b>密钥配错/格式不对</b>这种配置问题直接抛 {@code B000533}
     * （消息是中文可操作提示），不混在"渠道说不行"里 —— 两者对用户的意义完全不同。
     */
    @Override
    public PayRefundResult refund(VipOrderDO orderDO, String reason) {
        VipPayProperties.Alipay alipay = requireAlipayConfig();
        BigDecimal payableAmount = payableAmount(orderDO);
        String outRequestNo = REFUND_REQUEST_PREFIX + orderDO.getOrderNo();

        JSONObject bizContent = new JSONObject();
        bizContent.set("out_trade_no", orderDO.getOrderNo());
        // 退的是实付的应付额（和收款、和回调校验同一口径）
        bizContent.set("refund_amount", payableAmount.toPlainString());
        bizContent.set("out_request_no", outRequestNo);
        if (StrUtil.isNotBlank(reason)) {
            bizContent.set("refund_reason", reason);
        }

        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent(JSONUtil.toJsonStr(bizContent));
        try {
            AlipayTradeRefundResponse response = client(alipay).execute(request);
            if (response != null && response.isSuccess()) {
                log.info("支付宝退款成功：outTradeNo={}，refundNo={}，refundFee={}，资金变动={}",
                        orderDO.getOrderNo(), outRequestNo, response.getRefundFee(), response.getFundChange());
                // 渠道不返回"退款单号"，我们自己的 out_request_no 就是对外可对账的退款单号
                return new PayRefundResult(outRequestNo, true, null);
            }
            String failReason = failReasonOf(response);
            log.error("支付宝退款失败：outTradeNo={}，refundNo={}，失败原因={}",
                    orderDO.getOrderNo(), outRequestNo, failReason);
            return new PayRefundResult(outRequestNo, false, failReason);
        } catch (AlipayApiException e) {
            // 退款失败不能先改本地状态：订单保持已支付，用户可以再试（渠道侧按 out_request_no 去重）
            log.error("支付宝退款调用异常：outTradeNo={}，refundNo={}", orderDO.getOrderNo(), outRequestNo, e);
            return new PayRefundResult(outRequestNo, false, e.getMessage());
        }
    }

    @Override
    public String ackBody() {
        return ACK_SUCCESS;
    }

    @Override
    public boolean notifyNonceRequired() {
        // 支付宝的异步通知没有 nonce 字段，见类注释
        return false;
    }

    @Override
    public boolean plainTextAck() {
        return true;
    }

    /**
     * 取支付宝配置：关键项缺失就抛「渠道未配置」，<b>fail-closed</b>
     *
     * <p>不降级到 mock：密钥没配时若静默走模拟支付，等于用户不花钱就能拿到会员。
     * 日志里只说<b>缺哪几个配置项</b>，绝不打印密钥内容。
     */
    private VipPayProperties.Alipay requireAlipayConfig() {
        VipPayProperties.Alipay alipay = vipPayProperties.getAlipay();
        if (alipay == null) {
            throw new ClientException(VipErrorCodeEnum.VIP_PAY_CHANNEL_NOT_CONFIGURED);
        }
        List<String> missing = new ArrayList<>();
        if (StrUtil.isBlank(alipay.getGatewayUrl())) {
            missing.add("gatewayUrl");
        }
        if (StrUtil.isBlank(alipay.getAppId())) {
            missing.add("appId");
        }
        if (StrUtil.isBlank(alipay.getMerchantPrivateKey())) {
            missing.add("merchantPrivateKey");
        }
        if (StrUtil.isBlank(alipay.getAlipayPublicKey())) {
            missing.add("alipayPublicKey");
        }
        if (!missing.isEmpty()) {
            // 错误码用「渠道未配置」，但消息给具体的 key —— 用户照着 Nacos 补就行，不用去翻日志
            String detail = "支付宝渠道未配置：缺少 vip.pay.alipay."
                    + String.join(" / vip.pay.alipay.", missing);
            log.warn(detail);
            throw new ClientException(detail, VipErrorCodeEnum.VIP_PAY_CHANNEL_NOT_CONFIGURED);
        }
        return alipay;
    }

    /**
     * 启动自检：密钥能不能解析、配置齐不齐，打一条<b>中文可操作</b>的日志
     *
     * <p><b>刻意只告警、不抛异常</b>：mock 是默认渠道，演示/联调不配支付宝也必须能正常启动；
     * 真正要收钱时渠道自己会 fail-closed 报 {@code B000533}。
     * 但如果只让用户去猜那句英文异常（InvalidKeySpecException / 验签失败），成本太高 ——
     * 所以缺哪个 key、哪个 key 格式不对、怎么修，启动时就写清楚。
     */
    @PostConstruct
    void selfCheckOnStartup() {
        VipPayProperties.Alipay alipay = vipPayProperties.getAlipay();
        List<String> problems = AlipayKeySupport.collectProblems(alipay);
        if (problems.isEmpty()) {
            log.info("支付宝渠道配置自检通过：appId={}，网关={}，异步通知={}",
                    alipay.getAppId(), alipay.getGatewayUrl(),
                    StrUtil.blankToDefault(alipay.getNotifyUrl(), "未配置（靠 return_url 跳回 + /sync 主动对账）"));
            return;
        }
        log.warn("支付宝渠道暂不可用：ALIPAY 下单与回调都会返回 B000533「支付渠道未配置」。"
                + "共 {} 项待处理（密钥请配在 Nacos，不要提交进代码库）：", problems.size());
        for (int i = 0; i < problems.size(); i++) {
            log.warn("  {}. {}", i + 1, problems.get(i));
        }
    }

    /**
     * 构造支付宝客户端
     *
     * <p>每次调用现建一个：{@link DefaultAlipayClient} 只是把配置塞进字段（不带连接池、
     * 不会发起网络请求），构造成本可以忽略；这样密钥也不会被固化在一个长生命周期的单例里。
     */
    private AlipayClient client(VipPayProperties.Alipay alipay) {
        return new DefaultAlipayClient(alipay.getGatewayUrl(), alipay.getAppId(),
                privateKeyOf(alipay), FORMAT, CHARSET, alipayPublicKeyOf(alipay), alipay.getSignType());
    }

    /**
     * 应用私钥：归一化成 PKCS#8（PEM / 一行 base64 / PKCS#1 都能吃），格式不对给中文提示
     */
    private String privateKeyOf(VipPayProperties.Alipay alipay) {
        try {
            return AlipayKeySupport.normalizePrivateKey(alipay.getMerchantPrivateKey());
        } catch (IllegalArgumentException e) {
            // 密钥格式问题不是"渠道调用失败"，而是"配置不对"：同样用 B000533，但把可操作的消息给出去
            log.error("支付宝应用私钥不可用：{}", e.getMessage());
            throw new ClientException(e.getMessage(), VipErrorCodeEnum.VIP_PAY_CHANNEL_NOT_CONFIGURED);
        }
    }

    /**
     * 支付宝公钥：归一化成 X.509（一行 base64 / PEM 都能吃），格式不对给中文提示
     */
    private String alipayPublicKeyOf(VipPayProperties.Alipay alipay) {
        try {
            return AlipayKeySupport.normalizePublicKey(alipay.getAlipayPublicKey());
        } catch (IllegalArgumentException e) {
            log.error("支付宝公钥不可用：{}", e.getMessage());
            throw new ClientException(e.getMessage(), VipErrorCodeEnum.VIP_PAY_CHANNEL_NOT_CONFIGURED);
        }
    }

    /**
     * 渠道要收/要退的钱 = 订单应付额（有立减码时不是原价）
     *
     * <p>老订单没有 {@code payable_amount} 时回退成原价，与 {@code VipOrderServiceImpl}
     * 回调校验用的 {@code effectivePayableAmount} 保持同一口径 —— 两边不一致就会变成
     * "收得到钱但发不出会员"。
     */
    private BigDecimal payableAmount(VipOrderDO orderDO) {
        BigDecimal amount = orderDO.getPayableAmount() == null ? orderDO.getAmount() : orderDO.getPayableAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.error("订单 {} 的金额异常（amount={}，payableAmount={}），无法发起渠道请求",
                    orderDO.getOrderNo(), orderDO.getAmount(), orderDO.getPayableAmount());
            throw new ClientException("订单金额异常，无法发起支付");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isPaidStatus(String tradeStatus) {
        return TRADE_SUCCESS.equalsIgnoreCase(tradeStatus) || TRADE_FINISHED.equalsIgnoreCase(tradeStatus);
    }

    /**
     * 解析金额字符串，非法返回 null（交给上层按"金额不一致"拒绝，不在这里猜）
     */
    private BigDecimal parseAmount(String amount) {
        if (StrUtil.isBlank(amount)) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String failReasonOf(AlipayResponse response) {
        if (response == null) {
            return "渠道无应答";
        }
        return StrUtil.blankToDefault(response.getSubMsg(),
                StrUtil.blankToDefault(response.getMsg(), response.getCode()));
    }
}