package com.chuamgwei;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 创维 AI 网关管理系统启动类
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({
        "com.chuamgwei.infrastructure.mapper",
        "com.chuamgwei.module.credit.mapper",
        "com.chuamgwei.module.recharge.mapper",
        "com.chuamgwei.module.task.mapper"
})
public class ChuamgweiAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChuamgweiAdminApplication.class, args);
    }
}