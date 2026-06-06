package com.chuamgwei.module.task.controller;

import com.chuamgwei.common.RequestContext;
import com.chuamgwei.common.Result;
import com.chuamgwei.module.task.entity.SysTask;
import com.chuamgwei.module.task.service.TaskService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/**
 * 生图与生视频任务控制层接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Validated
public class TaskController {

    private final TaskService taskService;

    /**
     * 提交视频生成任务
     */
    @PostMapping("/v1/video/generations")
    public Result<SysTask> submitVideoTask(@RequestBody @Valid VideoTaskReq req) {
        String userUuid = requireCurrentUserUuid();
        log.info("提交视频任务: userUuid={}, model={}, prompt={}",
                userUuid, req.getModel(), req.getPrompt());
        SysTask task = taskService.submitVideoTask(
                userUuid,
                req.getModel(),
                req.getPrompt(),
                req.getNegativePrompt(),
                req.getAspectRatio(),
                req.getDuration()
        );
        log.info("视频任务提交成功: taskId={}, userUuid={}", task.getTaskId(), userUuid);
        return Result.success(task);
    }

    /**
     * 查询视频任务状态
     */
    @GetMapping("/v1/video/generations/{taskId}")
    public Result<SysTask> getVideoTaskStatus(@PathVariable("taskId") @NotBlank String taskId) {
        log.info("查询视频任务: taskId={}", taskId);
        SysTask task = requireCurrentUserTask(taskId);
        log.info("视频任务状态: taskId={}, status={}", taskId, task.getStatus());
        return Result.success(task);
    }

    /**
     * 提交图片生成任务
     */
    @PostMapping("/v1/images/generations")
    public Result<SysTask> submitImageTask(@RequestBody @Valid ImageTaskReq req) {
        String userUuid = requireCurrentUserUuid();
        log.info("提交图片任务: userUuid={}, model={}, prompt={}",
                userUuid, req.getModel(), req.getPrompt());
        SysTask task = taskService.submitImageTask(
                userUuid,
                req.getModel(),
                req.getPrompt(),
                req.getNegativePrompt(),
                req.getSize()
        );
        log.info("图片任务提交成功: taskId={}, userUuid={}", task.getTaskId(), userUuid);
        return Result.success(task);
    }

    /**
     * 查询图片任务状态
     */
    @GetMapping("/v1/images/generations/{taskId}")
    public Result<SysTask> getImageTaskStatus(@PathVariable("taskId") @NotBlank String taskId) {
        log.info("查询图片任务: taskId={}", taskId);
        SysTask task = requireCurrentUserTask(taskId);
        log.info("图片任务状态: taskId={}, status={}", taskId, task.getStatus());
        return Result.success(task);
    }

    /** 获取当前登录用户UUID */
    private String requireCurrentUserUuid() {
        String userUuid = RequestContext.getOperatorUuid();
        if (userUuid == null || userUuid.trim().isEmpty()) {
            throw new RuntimeException("当前用户身份缺失");
        }
        return userUuid;
    }

    /** 获取当前用户有权访问的任务 */
    private SysTask requireCurrentUserTask(String taskId) {
        String userUuid = requireCurrentUserUuid();
        SysTask task = taskService.getTaskStatus(taskId);
        if (!userUuid.equals(task.getUserUuid())) {
            throw new RuntimeException("无权访问该任务");
        }
        return task;
    }

    @Data
    public static class VideoTaskReq {
        private String userUuid;

        @NotBlank(message = "模型不能为空")
        private String model;

        @NotBlank(message = "提示词不能为空")
        private String prompt;

        private String negativePrompt;
        private String aspectRatio;
        private String duration;
    }

    @Data
    public static class ImageTaskReq {
        private String userUuid;

        @NotBlank(message = "模型不能为空")
        private String model;

        @NotBlank(message = "提示词不能为空")
        private String prompt;

        private String negativePrompt;
        private String size;
    }
}