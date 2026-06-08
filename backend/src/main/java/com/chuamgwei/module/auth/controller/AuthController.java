package com.chuamgwei.module.auth.controller;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chuamgwei.common.NoAuth;
import com.chuamgwei.common.Result;
import com.chuamgwei.infrastructure.entity.User;
import com.chuamgwei.infrastructure.mapper.UserMapper;
import com.chuamgwei.module.auth.config.AuthProperties;
import com.chuamgwei.module.auth.service.AuthTokenService;
import com.chuamgwei.module.redis.service.RedisService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历史兼容：new-api 一次性 ticket 登录桥接入口，不作为普通用户主登录链路
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final RedisService redisService;
    private final UserMapper userMapper;
    private final AuthProperties authProperties;
    private final AuthTokenService authTokenService;

    /**
     * 历史兼容：使用 new-api 生成的一次性 ticket 换取积分系统登录态
     */
    @NoAuth
    @PostMapping("/v1/auth/ticket")
    public Result<Map<String, Object>> exchangeTicket(@RequestBody @Valid TicketReq req) {
        String redisKey = authProperties.getTicketPrefix() + req.getTicket().trim();
        String ticketPayload = redisService.take(redisKey);
        if (ticketPayload == null || ticketPayload.trim().isEmpty()) {
            throw new RuntimeException("ticket不存在或已过期");
        }

        JSONObject ticket = JSONUtil.parseObj(ticketPayload);
        String userUuid = firstNonBlank(
                ticket.getStr("userUuid"),
                ticket.getStr("user_uuid"),
                ticket.getStr("userId"),
                ticket.getStr("user_id")
        );
        if (userUuid == null) {
            throw new RuntimeException("ticket缺少用户标识");
        }

        User user = userMapper.selectAuthUserByUuid(userUuid);
        ensureUserAvailable(user);

        String token = authTokenService.createToken(user);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("userUuid", user.getUserUuid());
        data.put("username", user.getUsername());
        data.put("adminPermissions", user.getAdminPermissions());
        log.info("ticket登录成功: userUuid={}, username={}", user.getUserUuid(), user.getUsername());
        return Result.success(data);
    }

    /**
     * 校验用户是否可登录积分系统
     */
    private void ensureUserAvailable(User user) {
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (Integer.valueOf(1).equals(user.getIsDeleted())) {
            throw new RuntimeException("用户已删除");
        }
        if (user.getIsBanned() != null && user.getIsBanned() != 0) {
            throw new RuntimeException("用户已封禁");
        }
    }

    /**
     * 取第一项非空字符串
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    @Data
    public static class TicketReq {
        @NotBlank(message = "ticket不能为空")
        private String ticket;
    }
}