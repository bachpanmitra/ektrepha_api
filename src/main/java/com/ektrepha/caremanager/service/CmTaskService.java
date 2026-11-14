package com.ektrepha.caremanager.service;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.dto.request.UpdateTaskRequest;
import com.ektrepha.caremanager.dto.response.TaskPageResponse;
import com.ektrepha.caremanager.dto.response.TaskResponse;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTaskStatus;

public interface CmTaskService {

	TaskPageResponse listMyTasks(CareManager caller, String due, CmTaskStatus status, String cursor, Integer limit);

	/** 403 NOT_YOUR_RECORD unless the caller owns the task's parent or is on duty; 409 TASK_CLOSED unless it's still OPEN. */
	TaskResponse updateTask(CmCaller caller, String taskPublicId, UpdateTaskRequest request);

}
