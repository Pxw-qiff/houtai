package com.chuamgwei.module.recharge.controller;

import com.chuamgwei.common.NoAuth;
import com.chuamgwei.common.RequestContext;
import com.chuamgwei.common.Result;
import com.chuamgwei.module.recharge.entity.RechargeOrder;
import com.chuamgwei.module.recharge.service.RechargeService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * 充值订单控制层接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class RechargeController {

    private final RechargeService rechargeService;

    /**
     * 获取当前的充值与积分转换比例
     */
    @GetMapping("/v1/recharge/ratio")
    public Result<BigDecimal> getRatio() {
        log.info("查询充值比例");
        BigDecimal ratio = rechargeService.getChargeRatio();
        log.info("当前充值比例: {}", ratio);
        return Result.success(ratio);
    }

    /**
     * 获取当前充值管理配置
     */
    @GetMapping("/v1/recharge/settings")
    public Result<Map<String, Object>> getRechargeSettings() {
        log.info("查询充值管理配置");
        return Result.success(rechargeService.getRechargeSettings());
    }

    /**
     * 创建充值订单
     */
    @PostMapping("/v1/recharge/order")
    public Result<RechargeOrder> createOrder(@RequestBody @Valid CreateOrderReq req) {
        String userUuid = RequestContext.getOperatorUuid();
        log.info("创建充值订单: userUuid={}, amount={}, payType={}",
                userUuid, req.getAmount(), req.getPayType());
        RechargeOrder order = rechargeService.createRechargeOrder(
                userUuid, req.getAmount(), req.getPayType());
        log.info("充值订单创建成功: orderNo={}, userUuid={}, amount={}",
                order.getOrderNo(), userUuid, req.getAmount());
        return Result.success(order);
    }

    /**
     * 查询充值订单
     */
    @GetMapping("/v1/recharge/order/{orderNo}")
    public Result<RechargeOrder> getOrder(@PathVariable @NotBlank(message = "订单号不能为空") String orderNo) {
        RechargeOrder order = requireCurrentUserOrder(orderNo);
        return Result.success(order);
    }

    /**
     * 生成支付宝扫码支付二维码内容
     */
    @PostMapping("/v1/recharge/pay/alipay/precreate")
    public Result<Map<String, String>> precreateAlipay(@RequestBody @Valid PrecreateReq req) {
        log.info("生成支付宝扫码二维码: orderNo={}", req.getOrderNo());
        requireCurrentUserOrder(req.getOrderNo());
        String qrCode = rechargeService.createAlipayQrCode(req.getOrderNo());
        Map<String, String> data = new HashMap<>();
        data.put("orderNo", req.getOrderNo());
        data.put("qrCode", qrCode);
        data.put("expireMinutes", rechargeService.getPayTimeoutMinutes().toString());
        return Result.success(data);
    }

    /**
     * 支付宝异步通知回调 (POST, form-urlencoded)
     */
    @NoAuth
    @PostMapping("/v1/recharge/callback/alipay")
    public String callbackAlipay(HttpServletRequest request) {
        Map<String, String> params = parseFormParams(request);
        log.info("支付宝异步通知: orderNo={}, tradeNo={}, status={}, amount={}",
                params.get("out_trade_no"), params.get("trade_no"), params.get("trade_status"), params.get("total_amount"));
        String result = rechargeService.handleAlipayCallback(params);
        log.info("支付宝异步通知处理结果: orderNo={}, result={}", params.get("out_trade_no"), result);
        return result;
    }

    /** 校验当前用户必须具备管理员权限 */
    private void ensureAdmin() {
        if (!RequestContext.isAdmin()) {
            throw new RuntimeException("当前用户无管理员权限");
        }
    }

    /** 获取当前用户有权访问的充值订单 */
    private RechargeOrder requireCurrentUserOrder(String orderNo) {
        RechargeOrder order = rechargeService.getRechargeOrder(orderNo);
        ensureOrderOwner(order);
        return order;
    }

    /** 校验当前用户只能访问自己的充值订单 */
    private void ensureOrderOwner(RechargeOrder order) {
        String currentUserUuid = RequestContext.getOperatorUuid();
        if (currentUserUuid == null || !currentUserUuid.equals(order.getUserUuid())) {
            throw new RuntimeException("无权访问该充值订单");
        }
    }

    /** 解析 POST/GET form 参数为 Map */
    private Map<String, String> parseFormParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        Enumeration<String> names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            params.put(name, request.getParameter(name));
        }
        return params;
    }

    /**
     * 管理员手动触发单笔支付宝查单补偿
     */
    @PostMapping("/v1/admin/recharge/reconcile/alipay/{orderNo}")
    public Result<String> reconcileAlipayOrder(@PathVariable @NotBlank(message = "订单号不能为空") String orderNo) {
        ensureAdmin();
        log.info("手动触发支付宝单笔查单补偿: orderNo={}", orderNo);
        String result = rechargeService.reconcileAlipayOrder(orderNo);
        return Result.success(result);
    }

    /**
     * 管理员手动触发批量支付宝查单补偿
     */
    @PostMapping("/v1/admin/recharge/reconcile/alipay")
    public Result<Map<String, Object>> reconcilePendingAlipayOrders() {
        ensureAdmin();
        log.info("手动触发支付宝批量查单补偿");
        int changedCount = rechargeService.reconcilePendingAlipayOrders();
        Map<String, Object> data = new HashMap<>();
        data.put("changedCount", changedCount);
        return Result.success(data);
    }

    /**
     * 管理员修改充值比例（操作人身份从请求头获取）
     */
    @PostMapping("/v1/admin/recharge/ratio")
    public Result<String> updateRatio(@RequestBody @Valid UpdateRatioReq req) {
        ensureAdmin();
        String operatorUuid = RequestContext.getOperatorUuid();
        String operatorName = RequestContext.getOperatorName();

        log.info("修改充值比例: ratio={}, operator={}", req.getRatio(), operatorName);
        rechargeService.updateChargeRatio(req.getRatio(), operatorUuid, operatorName);
        log.info("充值比例修改成功: ratio={}, operator={}", req.getRatio(), operatorName);
        return Result.success("兑换比例修改成功");
    }

    /**
     * 管理员修改充值管理配置
     */
    @PostMapping("/v1/admin/recharge/settings")
    public Result<String> updateRechargeSettings(@RequestBody @Valid UpdateRechargeSettingsReq req) {
        ensureAdmin();
        String operatorUuid = RequestContext.getOperatorUuid();
        String operatorName = RequestContext.getOperatorName();

        log.info("修改充值管理配置: ratio={}, timeoutMinutes={}, subjectPrefix={}, operator={}",
                req.getRatio(), req.getPayTimeoutMinutes(), req.getPaySubjectPrefix(), operatorName);
        rechargeService.updateRechargeSettings(
                req.getRatio(), req.getPayTimeoutMinutes(), req.getPaySubjectPrefix(), operatorUuid, operatorName);
        return Result.success("充值管理配置修改成功");
    }

    @Data
    public static class CreateOrderReq {
        @NotNull(message = "充值金额不能为空")
        @DecimalMin(value = "0.01", message = "充值金额不能小于0.01元")
        private BigDecimal amount;

        @NotBlank(message = "支付方式不能为空")
        private String payType;
    }

    @Data
    public static class PrecreateReq {
        @NotBlank(message = "订单号不能为空")
        private String orderNo;
    }

    @Data
    public static class UpdateRatioReq {
        @NotNull(message = "兑换比例不能为空")
        @DecimalMin(value = "0.000001", message = "兑换比例不能小于0.000001")
        private BigDecimal ratio;
    }

    @Data
    public static class UpdateRechargeSettingsReq {
        @NotNull(message = "兑换比例不能为空")
        @DecimalMin(value = "0.000001", message = "兑换比例不能小于0.000001")
        private BigDecimal ratio;

        @NotNull(message = "支付超时时间不能为空")
        @Min(value = 1, message = "支付超时时间不能小于1分钟")
        @Max(value = 1440, message = "支付超时时间不能大于1440分钟")
        private Integer payTimeoutMinutes;

        @NotBlank(message = "支付宝支付名称不能为空")
        @Size(max = 80, message = "支付宝支付名称不能超过80个字符")
        private String paySubjectPrefix;
    }
}