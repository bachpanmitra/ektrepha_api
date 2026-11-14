package com.ektrepha.caremanager.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.CmCaller;
import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.CursorCodec;
import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.dto.request.UpdateTaskRequest;
import com.ektrepha.caremanager.dto.response.TaskPageResponse;
import com.ektrepha.caremanager.dto.response.TaskResponse;
import com.ektrepha.caremanager.service.CareManagerAccessService;
import com.ektrepha.caremanager.service.CmTaskService;
import com.ektrepha.exception.CmResourceNotFoundException;
import com.ektrepha.exception.CmValidationException;
import com.ektrepha.exception.TaskClosedException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmTask;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.repository.CmTaskRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CmTaskServiceImpl implements CmTaskService {

	private static final int DEFAULT_LIMIT = 50;
	private static final int MAX_LIMIT = 100;

	private final CmTaskRepository cmTaskRepository;
	private final CareManagerAccessService careManagerAccessService;
	private final Clock clock;

	@Override
	@Transactional(readOnly = true)
	public TaskPageResponse listMyTasks(CareManager caller, String due, CmTaskStatus status, String cursor, Integer limit) {
		Instant now = Instant.now(clock);
		Instant dueFrom = null;
		Instant dueTo = null;
		if ("overdue".equals(due)) {
			dueTo = now;
		} else if ("today".equals(due)) {
			dueFrom = startOfTodayIst(now);
			dueTo = endOfTodayIst(now);
		}

		int pageSize = clamp(limit);
		CursorCodec.Cursor decoded = CursorCodec.decode(cursor);
		Instant afterDueAt = decoded == null ? null : Instant.parse(decoded.sortKey());
		Long afterId = decoded == null ? null : decoded.id();

		List<CmTask> rows = cmTaskRepository.findPageForCareManager(caller.getId(), status,
				dueFrom, dueTo, afterDueAt, afterId, PageRequest.of(0, pageSize + 1));

		boolean hasMore = rows.size() > pageSize;
		List<CmTask> page = hasMore ? rows.subList(0, pageSize) : rows;
		String nextCursor = hasMore
				? CursorCodec.encode(page.get(page.size() - 1).getDueAt().toString(), page.get(page.size() - 1).getId())
				: null;

		return new TaskPageResponse(page.stream().map(t -> toResponse(t, now)).toList(), nextCursor);
	}

	@Override
	@Transactional
	public TaskResponse updateTask(CmCaller caller, String taskPublicId, UpdateTaskRequest request) {
		if (request.status() == CmTaskStatus.OPEN) {
			throw new CmValidationException("status must be DONE or CANCELLED", java.util.Map.of("status", "must be DONE or CANCELLED"));
		}
		CmTask task = cmTaskRepository.findByPublicId(taskPublicId)
				.orElseThrow(() -> new CmResourceNotFoundException("No task with id " + taskPublicId));

		Long parentCmId = task.getParent().getCareManager() == null ? null : task.getParent().getCareManager().getId();
		careManagerAccessService.requireWrite(caller, parentCmId, "this family's tasks");

		if (task.getStatus() != CmTaskStatus.OPEN) {
			throw new TaskClosedException("This task is no longer open");
		}

		task.setStatus(request.status());
		task.setNote(request.note());
		task.setDoneAt(Instant.now(clock));
		cmTaskRepository.save(task);

		return toResponse(task, Instant.now(clock));
	}

	private TaskResponse toResponse(CmTask task, Instant now) {
		boolean overdue = task.getStatus() == CmTaskStatus.OPEN && task.getDueAt().isBefore(now);
		String bookingId = task.getBooking() == null ? null : task.getBooking().getPublicId();
		String complaintId = task.getComplaint() == null ? null : task.getComplaint().getPublicId();
		return new TaskResponse(
				task.getPublicId(),
				task.getType(),
				task.getPriority(),
				task.getStatus(),
				task.getDueAt(),
				overdue,
				task.getTitle(),
				task.getDetail(),
				CmPersonRefMapper.of(task.getParent()),
				task.getCareManager() == null ? null : CmPersonRefMapper.of(task.getCareManager()),
				bookingId,
				complaintId,
				task.getNote(),
				task.getCreatedAt(),
				task.getDoneAt());
	}

	private Instant startOfTodayIst(Instant now) {
		return now.atZone(ShiftWindow.INDIA_ZONE).toLocalDate().atStartOfDay(ShiftWindow.INDIA_ZONE).toInstant();
	}

	private Instant endOfTodayIst(Instant now) {
		return now.atZone(ShiftWindow.INDIA_ZONE).toLocalDate().plusDays(1).atStartOfDay(ShiftWindow.INDIA_ZONE).toInstant();
	}

	private int clamp(Integer limit) {
		if (limit == null) {
			return DEFAULT_LIMIT;
		}
		return Math.min(Math.max(limit, 1), MAX_LIMIT);
	}

}
