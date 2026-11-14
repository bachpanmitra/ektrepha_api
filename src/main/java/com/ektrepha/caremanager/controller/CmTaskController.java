package com.ektrepha.caremanager.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ektrepha.caremanager.CmCallerResolver;
import com.ektrepha.caremanager.dto.request.UpdateTaskRequest;
import com.ektrepha.caremanager.dto.response.TaskPageResponse;
import com.ektrepha.caremanager.dto.response.TaskResponse;
import com.ektrepha.caremanager.service.CmTaskService;
import com.ektrepha.model.CmTaskStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** docs/api/openapi.yaml tag "CM - summary and tasks" */
@RestController
@RequestMapping("/v1/cm")
@RequiredArgsConstructor
public class CmTaskController {

	private final CmCallerResolver cmCallerResolver;
	private final CmTaskService cmTaskService;

	@GetMapping("/me/tasks")
	public TaskPageResponse listMyTasks(
			@RequestParam(defaultValue = "today") String due,
			@RequestParam(required = false) CmTaskStatus status,
			@RequestParam(required = false) String cursor,
			@RequestParam(required = false) Integer limit,
			Authentication authentication) {
		return cmTaskService.listMyTasks(cmCallerResolver.resolveOwnCareManager(authentication), due, status, cursor, limit);
	}

	@PatchMapping("/tasks/{taskId}")
	public TaskResponse updateTask(@PathVariable String taskId, @Valid @RequestBody UpdateTaskRequest request,
			Authentication authentication) {
		return cmTaskService.updateTask(cmCallerResolver.resolve(authentication), taskId, request);
	}

}
