package com.chuamgwei.module.recharge.payment;

import cn.hutool.json.JSONUtil;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;

import com.chuamgwei.module.recharge.config.AlipayProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝沙箱支付实现，生成扫码支付二维码并验签异步通知
 */
@Slf4j
@Component
public class AlipayPaymentProvider implements PaymentProvider {

    private static final String TRADE_NOT_EXIST = "ACQ.TRADE_NOT_EXIST";

    private final AlipayProperties props;
    private AlipayClient alipayClient;

    public AlipayPaymentProvider(AlipayProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        this.alipayClient = new DefaultAlipayClient(
                props.getServerUrl(),
                props.getAppId(),
                props.getPrivateKey(),
                "json",
                "UTF-8",
                props.getAlipayPublicKey(),
                "RSA2"
        );
        log.info("支付宝沙箱客户端初始化完成, 网关: {}", props.getServerUrl());
    }

    @Override
    public String getType() {
        return "ALIPAY";
    }

    @Override
    public String createQrCode(String orderNo, BigDecimal amount, String subject, int timeoutMinutes) {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(props.getNotifyUrl());

        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", orderNo);
        bizContent.put("total_amount", amount.toString());
        bizContent.put("subject", subject);
        bizContent.put("timeout_express", timeoutMinutes + "m");

        request.setBizContent(JSONUtil.toJsonStr(bizContent));

        try {
            AlipayTradePrecreateResponse response = alipayClient.execute(request);
            if (!response.isSuccess()) {
                String message = firstNonBlank(response.getSubMsg(), response.getMsg(), response.getSubCode(), response.getCode(), "支付通道拒绝预下单");
                log.error("支付宝预下单失败: orderNo={}, code={}, msg={}, subCode={}, subMsg={}",
                        orderNo, response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
                throw PaymentGatewayException.definite("支付宝预下单失败: " + message);
            }
            log.info("支付宝预下单成功: orderNo={}, amount={}", orderNo, amount);
            return response.getQrCode();
        } catch (AlipayApiException e) {
            log.error("支付宝预下单异常: orderNo={}, errCode={}, errMsg={}",
                    orderNo, e.getErrCode(), e.getErrMsg(), e);
            throw PaymentGatewayException.uncertain(
                    "支付宝预下单异常: " + firstNonBlank(e.getErrMsg(), e.getMessage(), "支付网关异常"), e);
        }
    }

    @Override
    public PaymentQueryResult queryOrder(String orderNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        Map<String, Object> bizContent = new HashMap<>();
        bizContent.put("out_trade_no", orderNo);
        request.setBizContent(JSONUtil.toJsonStr(bizContent));

        try {
            AlipayTradeQueryResponse response = alipayClient.execute(request);
            PaymentQueryResult result = buildQueryResult(orderNo, response);
            if (response.isSuccess()) {
                log.info("支付宝查单成功: orderNo={}, tradeNo={}, status={}, amount={}",
                        result.getOrderNo(), result.getProviderTradeNo(), result.getRawStatus(), result.getAmount());
            } else {
                log.warn("支付宝查单未成功: orderNo={}, code={}, msg={}, subCode={}, subMsg={}",
                        orderNo, response.getCode(), response.getMsg(), response.getSubCode(), response.getSubMsg());
            }
            return result;
        } catch (AlipayApiException e) {
            log.error("支付宝查单异常: orderNo={}, errCode={}, errMsg={}",
                    orderNo, e.getErrCode(), e.getErrMsg(), e);
            throw PaymentGatewayException.uncertain(
                    "支付宝查单异常: " + firstNonBlank(e.getErrMsg(), e.getMessage(), "支付网关异常"), e);
        }
    }

    @Override
    public boolean verifyCallback(Map<String, String> params) {
        try {
            boolean valid = AlipaySignature.rsaCheckV1(
                    params,
                    props.getAlipayPublicKey(),
                    "UTF-8",
                    "RSA2"
            );
            if (valid) {
                log.info("支付宝回调验签通过: trade_no={}, out_trade_no={}",
                        params.get("trade_no"), params.get("out_trade_no"));
            } else {
                log.warn("支付宝回调验签失败: {}", params);
            }
            return valid;
        } catch (AlipayApiException e) {
            log.error("支付宝回调验签异常", e);
            return false;
        }
    }

    /**
     * 辅助确认第三方平台是否已经能识别本地订单号。
     * 支付宝扫码预下单场景下，二维码返回成功不代表交易立即可查询；用户扫码唤起收银台前可能返回 ACQ.TRADE_NOT_EXIST。
     * 该探测仅用于真实环境联调和异常诊断，不应作为二维码返回主流程的强阻断依据。
     */
    @Override
    public TradeVisibilityStatus ensureTradeVisible(String orderNo) {
        int maxRetries = 3;
        int retryIntervalMs = 300;
        // 沙箱网关存在短时不可见或 504 波动，只做有限探测，避免长时间占用请求线程。

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                PaymentQueryResult result = queryOrder(orderNo);
                TradeVisibilityStatus status = mapToVisibilityStatus(result);

                if (status.isVisibleAndWaiting()) {
                    log.info("第三方平台交易可见性确认成功: orderNo={}, attempt={}/{}", orderNo, attempt, maxRetries);
                    return status;
                }

                if (status == TradeVisibilityStatus.NOT_EXIST && attempt < maxRetries) {
                    // NOT_EXIST 可能只是用户尚未扫码，不直接等同于预下单失败。
                    log.warn("第三方平台交易暂不可见，等待重试: orderNo={}, attempt={}/{}", orderNo, attempt, maxRetries);
                    Thread.sleep(retryIntervalMs);
                    continue;
                }

                log.warn("第三方平台交易可见性确认异常: orderNo={}, status={}, attempt={}/{}",
                        orderNo, status, attempt, maxRetries);
                return status;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("第三方平台交易可见性确认被中断: orderNo={}", orderNo, e);
                return TradeVisibilityStatus.GATEWAY_ERROR;
            } catch (PaymentGatewayException e) {
                log.error("第三方平台交易可见性确认网关异常: orderNo={}, attempt={}/{}", orderNo, attempt, maxRetries, e);
                if (attempt == maxRetries) {
                    return TradeVisibilityStatus.GATEWAY_ERROR;
                }
                try {
                    Thread.sleep(retryIntervalMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return TradeVisibilityStatus.GATEWAY_ERROR;
                }
            }
        }

        return TradeVisibilityStatus.NOT_EXIST;
    }

    /**
     * 构建通用支付查单结果
     */
    private PaymentQueryResult buildQueryResult(String orderNo, AlipayTradeQueryResponse response) {
        PaymentQueryResult result = new PaymentQueryResult();
        result.setProviderType(getType());
        result.setQuerySuccess(response.isSuccess());
        result.setProviderCode(response.getCode());
        result.setProviderMessage(response.getMsg());
        result.setProviderSubCode(response.getSubCode());
        result.setProviderSubMessage(response.getSubMsg());
        result.setOrderNo(response.getOutTradeNo() == null ? orderNo : response.getOutTradeNo());
        result.setProviderTradeNo(response.getTradeNo());
        result.setRawStatus(response.getTradeStatus());
        result.setStatus(resolveQueryStatus(response));
        result.setFailureMessage(firstNonBlank(response.getSubMsg(), response.getMsg(), response.getSubCode(), response.getCode(), null));
        if (response.getTotalAmount() != null) {
            result.setAmount(new BigDecimal(response.getTotalAmount()));
        }
        return result;
    }

    /**
     * 将支付宝查单响应映射为通用支付状态
     */
    private PaymentQueryStatus resolveQueryStatus(AlipayTradeQueryResponse response) {
        if (!response.isSuccess()) {
            if (TRADE_NOT_EXIST.equals(response.getSubCode())) {
                return PaymentQueryStatus.NOT_EXIST;
            }
            return PaymentQueryStatus.GATEWAY_ERROR;
        }

        String tradeStatus = response.getTradeStatus();
        if (tradeStatus == null) {
            return PaymentQueryStatus.UNKNOWN;
        }

        switch (tradeStatus) {
            case "WAIT_BUYER_PAY":
                return PaymentQueryStatus.WAITING_PAY;
            case "TRADE_SUCCESS":
            case "TRADE_FINISHED":
                return PaymentQueryStatus.PAID;
            case "TRADE_CLOSED":
                return PaymentQueryStatus.CLOSED;
            default:
                log.warn("支付宝返回未知交易状态: orderNo={}, status={}", response.getOutTradeNo(), tradeStatus);
                return PaymentQueryStatus.UNKNOWN;
        }
    }

    /**
     * 将通用查单结果映射为交易可见性状态
     */
    private TradeVisibilityStatus mapToVisibilityStatus(PaymentQueryResult result) {
        switch (result.getStatus()) {
            case WAITING_PAY:
                return TradeVisibilityStatus.WAITING_PAY;
            case PAID:
                return TradeVisibilityStatus.PAID;
            case CLOSED:
                return TradeVisibilityStatus.CLOSED;
            case NOT_EXIST:
                return TradeVisibilityStatus.NOT_EXIST;
            case UNKNOWN:
            case GATEWAY_ERROR:
            default:
                return TradeVisibilityStatus.GATEWAY_ERROR;
        }
    }

    /**
     * 从支付宝 SDK 返回的多组错误描述里选择最适合展示和记录的一条。
     * SDK 异常在沙箱网关超时等场景下可能缺少 errMsg，需要回退到异常消息或默认文案。
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return "未知错误";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "未知错误";
    }
}