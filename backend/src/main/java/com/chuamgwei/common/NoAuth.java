package com.chuamgwei.common;

import java.lang.annotation.*;

/**
 * 标记无需身份校验的接口方法。
 * 加在 Controller 方法上，AOP 切面会自动跳过身份检查。
 *
 * 典型用例：
 *   - 支付回调接口（外部系统调用，无操作人身份）
 *   - 公开查询接口
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NoAuth {
}