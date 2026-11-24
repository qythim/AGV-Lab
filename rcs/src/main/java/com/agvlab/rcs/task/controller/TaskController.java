package com.agvlab.rcs.task.controller;

import com.agvlab.rcs.task.dto.CreateTaskRequest;
import com.agvlab.rcs.task.dto.TaskDetailResponse;
import com.agvlab.rcs.task.dto.TaskResponse;
import com.agvlab.rcs.task.service.TaskService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 搬運任務 REST API 控制器
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * 建立新搬運任務 (支援 Auto Assign 與指定車輛)
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse createTask(@RequestBody CreateTaskRequest request) {
        return taskService.createTask(request);
    }

    /**
     * 查詢任務清單 (可選 status 過濾參數，例如 ?status=PENDING)
     */
    @GetMapping
    public List<TaskResponse> getAllTasks(@RequestParam(required = false) String status) {
        return taskService.getAllTasks(status);
    }

    /**
     * 查詢單筆任務詳情
     */
    @GetMapping("/{taskCode}")
    public TaskResponse getTask(@PathVariable String taskCode) {
        return taskService.getTask(taskCode);
    }

    /**
     * 查詢指定任務的各階段執行明細 (PICKUP / DELIVERY 時間軸)
     */
    @GetMapping("/{taskCode}/details")
    public List<TaskDetailResponse> getTaskDetails(@PathVariable String taskCode) {
        return taskService.getTaskDetails(taskCode);
    }

    /**
     * 取消指定任務
     */
    @PostMapping("/{taskCode}/cancel")
    public TaskResponse cancelTask(@PathVariable String taskCode) {
        return taskService.cancelTask(taskCode);
    }
}

