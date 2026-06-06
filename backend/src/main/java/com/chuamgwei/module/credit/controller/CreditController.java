package com.chuamgwei.module.credit.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chuamgwei.common.RequestContext;
import com.chuamgwei.common.Result;
import com.chuamgwei.module.credit.entity.CreditAccount;
import com.chuamgwei.module.credit.entity.CreditAccountVO;
import com.chuamgwei.module.credit.entity.CreditFlow;
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
 * 积分账户控制层接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class CreditController {

    private final CreditService creditService;

    /**
     * 查询当前用户积分余额
     */
    @GetMapping("/v1/credit/balance")
    public Result<CreditAccount> getBalance() {
        String userUuid = RequestContext.getOperatorUuid();
        if (userUuid == null || userUuid.trim().isEmpty()) {
            throw new RuntimeException("当前用户身份缺失");
        }
        log.info("查询当前用户余额: userUuid={}", userUuid);
        CreditAccount account = creditService.getBalance(userUuid);
        log.info("查询余额结果: userUuid={}, availablePoints={}", userUuid, account.getAvailablePoints());
        return Result.success(account);
    }

    /**
     * 分页查询积分账户列表（管理端）
     */
    @GetMapping("/v1/admin/credit/accounts")
    public Result<Page<CreditAccountVO>> listAccounts(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        log.info("查询账户列表: current={}, size={}, keyword={}", current, size, keyword);
        Page<CreditAccountVO> page = creditService.pageAccounts(current, size, keyword);
        log.info("查询账户列表结果: total={}, records={}", page.getTotal(), page.getRecords().size());
        return Result.success(page);
    }

    /**
     * 分页查询积分流水（管理端）
     */
    @GetMapping("/v1/admin/credit/flows")
    public Result<Page<CreditFlow>> listFlows(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String userUuid,
            @RequestParam(required = false) String bizType) {
        log.info("查询积分流水: current={}, size={}, userUuid={}, bizType={}", current, size, userUuid, bizType);
        Page<CreditFlow> page = creditService.pageFlows(current, size, userUuid, bizType);
        log.info("查询积分流水结果: total={}, records={}", page.getTotal(), page.getRecords().size());
        return Result.success(page);
    }

    /**
     * 管理员手动调账（操作人身份从请求头获取，不可伪造）
     */
    @PostMapping("/v1/admin/credit/adjust")
    public Result<String> adjustBalance(@RequestBody @Valid AdjustBalanceReq req) {
        String operatorUuid = RequestContext.getOperatorUuid();
        String operatorName = RequestContext.getOperatorName();

        log.info("调账请求: operatorUuid={}, operatorName={}, targetUser={}, type={}, points={}",
                operatorUuid, operatorName, req.getUserUuid(),
                req.getAdjustType() == 1 ? "加" : "扣", req.getPoints());

        creditService.adjustBalance(
                req.getUserUuid(),
                req.getAdjustType(),
                req.getPoints(),
                req.getReason(),
                operatorUuid,
                operatorName
        );
        log.info("调账成功: operator={}, targetUser={}, points={}",
                operatorName, req.getUserUuid(), req.getPoints());
        return Result.success("调账成功");
    }

    @Data
    public static class AdjustBalanceReq {
        @NotBlank(message = "用户UUID不能为空")
        private String userUuid;

        @NotNull(message = "调账方向不能为空")
        private Integer adjustType;

        @NotNull(message = "积分数量不能为空")
        @DecimalMin(value = "0.000001", message = "积分数量不能小于0.000001")
        private BigDecimal points;

        @NotBlank(message = "调账原因不能为空")
        private String reason;
    }
}