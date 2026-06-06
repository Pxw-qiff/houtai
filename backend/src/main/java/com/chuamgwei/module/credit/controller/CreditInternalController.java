package com.chuamgwei.module.credit.controller;

import cn.hutool.core.util.StrUtil;
import com.chuamgwei.common.AuthException;
import com.chuamgwei.common.NoAuth;
import com.chuamgwei.common.Result;
import com.chuamgwei.module.credit.config.InternalCreditProperties;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.entity.CreditBillingResult;
import com.chuamgwei.module.credit.service.CreditService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 服务间积分计费接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/internal/credit")
public class CreditInternalController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final CreditService creditService;
    private final InternalCreditProperties internalCreditProperties;

    /**
     * 内部预扣积分
     */
    @NoAuth
    @PostMapping("/pre-consume")
    public Result<CreditBillingResult> preConsume(
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @RequestBody @Valid PreConsumeReq req) {
        requireInternalSecret(internalSecret);
        log.info("内部预扣请求: userUuid={}, bizType={}, bizOrderNo={}, estimatedPoints={}",
                req.getUserUuid(), req.getBizType(), req.getBizOrderNo(), req.getEstimatedPoints());
        return Result.success(creditService.preConsume(
                req.getUserUuid(),
                req.getBizType(),
                req.getBizOrderNo(),
                req.getEstimatedPoints(),
                req.getRemark()
        ));
    }

    /**
     * 内部结算积分
     */
    @NoAuth
    @PostMapping("/settle")
    public Result<CreditBillingResult> settle(
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @RequestBody @Valid SettleReq req) {
        requireInternalSecret(internalSecret);
        log.info("内部结算请求: userUuid={}, bizType={}, bizOrderNo={}, actualPoints={}",
                req.getUserUuid(), req.getBizType(), req.getBizOrderNo(), req.getActualPoints());
        return Result.success(creditService.settle(
                req.getUserUuid(),
                req.getBizType(),
                req.getBizOrderNo(),
                req.getActualPoints(),
                req.getRemark()
        ));
    }

    /**
     * 内部退款积分
     */
    @NoAuth
    @PostMapping("/refund")
    public Result<CreditBillingResult> refund(
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @RequestBody @Valid RefundReq req) {
        requireInternalSecret(internalSecret);
        log.info("内部退款请求: userUuid={}, bizType={}, bizOrderNo={}",
                req.getUserUuid(), req.getBizType(), req.getBizOrderNo());
        return Result.success(creditService.refund(
                req.getUserUuid(),
                req.getBizType(),
                req.getBizOrderNo(),
                req.getRemark()
        ));
    }

    /**
     * 内部查询余额
     */
    @NoAuth
    @GetMapping("/balance")
    public Result<CreditAccount> balance(
            @RequestHeader(value = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @RequestParam("userUuid") @NotBlank(message = "用户UUID不能为空") String userUuid) {
        requireInternalSecret(internalSecret);
        return Result.success(creditService.getBalance(userUuid));
    }

    /**
     * 校验服务间密钥
     */
    private void requireInternalSecret(String internalSecret) {
        String configuredSecret = internalCreditProperties.getSecret();
        if (StrUtil.isBlank(configuredSecret)) {
            throw new AuthException("内部扣费密钥未配置");
        }
        if (!StrUtil.equals(configuredSecret, internalSecret)) {
            throw new AuthException("内部扣费密钥无效");
        }
    }

    @Data
    public static class PreConsumeReq {
        @NotBlank(message = "用户UUID不能为空")
        private String userUuid;

        @NotBlank(message = "业务类型不能为空")
        private String bizType;

        @NotBlank(message = "业务单号不能为空")
        private String bizOrderNo;

        @NotNull(message = "预估积分不能为空")
        @DecimalMin(value = "0.000001", message = "预估积分不能小于0.000001")
        private BigDecimal estimatedPoints;

        private String remark;
    }

    @Data
    public static class SettleReq {
        @NotBlank(message = "用户UUID不能为空")
        private String userUuid;

        @NotBlank(message = "业务类型不能为空")
        private String bizType;

        @NotBlank(message = "业务单号不能为空")
        private String bizOrderNo;

        @NotNull(message = "实际积分不能为空")
        @DecimalMin(value = "0.000000", message = "实际积分不能小于0")
        private BigDecimal actualPoints;

        private String remark;
    }

    @Data
    public static class RefundReq {
        @NotBlank(message = "用户UUID不能为空")
        private String userUuid;

        @NotBlank(message = "业务类型不能为空")
        private String bizType;

        @NotBlank(message = "业务单号不能为空")
        private String bizOrderNo;

        private String remark;
    }
}