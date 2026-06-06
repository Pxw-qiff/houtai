package com.chuamgwei.module.task.scheduler;

import com.chuamgwei.module.task.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 异步生图/生视频任务状态定时轮询调度器
 */
@Slf4j
@Component("sysTaskScheduler") // 显式指定 Bean 名称，防止与 Spring 的 taskScheduler 冲突
@ConditionalOnProperty(name = "task.scheduler.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SysTaskScheduler {

    private final TaskService taskService;

    /**
     * 每隔 5 秒执行一次，查询进行中的任务状态并进行扣费或退款处理
     * 使用 fixedDelay 可以保证上一次任务执行完毕后再推迟 5 秒执行下一次，避免并发轮询冲突
     */
    @Scheduled(fixedDelay = 5000)
    public void pollTasks() {
        try {
            taskService.pollPendingTasks();
        } catch (Exception e) {
            log.error("定时轮询异步任务状态发生异常: ", e);
        }
    }
}
