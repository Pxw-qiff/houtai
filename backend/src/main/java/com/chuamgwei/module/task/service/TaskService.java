package com.chuamgwei.module.task.service;

import com.chuamgwei.module.task.entity.SysTask;

/**
 * 生图与生视频任务服务接口
 */
public interface TaskService {

    /**
     * 提交异步图片生成任务
     */
    SysTask submitImageTask(String userUuid, String model, String prompt, String negativePrompt, String size);

    /**
     * 提交异步视频生成任务
     */
    SysTask submitVideoTask(String userUuid, String model, String prompt, String negativePrompt, String aspectRatio, String duration);

    /**
     * 查询任务状态和结果
     */
    SysTask getTaskStatus(String taskId);

    /**
     * 轮询处理处于进行中的任务
     */
    void pollPendingTasks();
}