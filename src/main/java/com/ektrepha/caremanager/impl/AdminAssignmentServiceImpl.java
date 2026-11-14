package com.ektrepha.caremanager.impl;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.CmPersonRefMapper;
import com.ektrepha.caremanager.dto.request.ReassignRequest;
import com.ektrepha.caremanager.dto.response.AssignmentResponse;
import com.ektrepha.caremanager.service.AdminAssignmentService;
import com.ektrepha.caremanager.service.CmAssignmentService;
import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.exception.CmOverCapException;
import com.ektrepha.exception.CmResourceNotFoundException;
import com.ektrepha.exception.NannyNotFoundException;
import com.ektrepha.exception.ParentNotFoundException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmAssignment;
import com.ektrepha.model.CmChangeRequestKind;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmAssignmentRepository;
import com.ektrepha.repository.CmChangeRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminAssignmentServiceImpl implements AdminAssignmentService {

	private final ParentRepository parentRepository;
	private final NannyRepository nannyRepository;
	private final CareManagerRepository careManagerRepository;
	private final CmAssignmentRepository cmAssignmentRepository;
	private final CmChangeRequestRepository cmChangeRequestRepository;
	private final CmAssignmentService cmAssignmentService;
	private final CmNotificationService cmNotificationService;

	@Override
	@Transactional
	public AssignmentResponse reassignParent(String parentPublicId, ReassignRequest request, User admin) {
		Parent parent = parentRepository.findByPublicId(parentPublicId)
				.orElseThrow(() -> new ParentNotFoundException("No parent with id " + parentPublicId));
		CareManager careManager = careManagerRepository.findByPublicId(request.careManagerId())
				.orElseThrow(() -> new CmResourceNotFoundException("No Care Manager with id " + request.careManagerId()));

		boolean alreadyMoving = parent.getCareManager() == null || !parent.getCareManager().getId().equals(careManager.getId());
		if (alreadyMoving) {
			long currentActive = cmAssignmentService.countActiveParents(careManager);
			if (currentActive + 1 > careManager.getMaxParents()) {
				throw new CmOverCapException("Reassigning this parent would push " + careManager.getName()
						+ " over their cap of " + careManager.getMaxParents());
			}
		}

		if (request.notifyParentOrDefault()) {
			cmNotificationService.notifyCareManagerChanging(parent, careManager);
		}

		CmAssignment assignment = cmAssignmentService.assignParentTo(parent, careManager, request.reason(), admin);
		if (request.notifyParentOrDefault()) {
			assignment.setParentNotifiedAt(Instant.now());
			cmAssignmentRepository.save(assignment);
		}
		cmChangeRequestRepository.findByParentIdAndKindAndStatus(parent.getId(), CmChangeRequestKind.CM, CmTaskStatus.OPEN)
				.ifPresent(cr -> {
					cr.setStatus(CmTaskStatus.DONE);
					cmChangeRequestRepository.save(cr);
				});

		return toResponse(assignment);
	}

	@Override
	@Transactional
	public AssignmentResponse reassignNanny(String nannyPublicId, ReassignRequest request, User admin) {
		Nanny nanny = nannyRepository.findByPublicId(nannyPublicId)
				.orElseThrow(() -> new NannyNotFoundException("No nanny with id " + nannyPublicId));
		CareManager careManager = careManagerRepository.findByPublicId(request.careManagerId())
				.orElseThrow(() -> new CmResourceNotFoundException("No Care Manager with id " + request.careManagerId()));

		CmAssignment assignment = cmAssignmentService.assignNannyTo(nanny, careManager, request.reason(), admin);
		return toResponse(assignment);
	}

	private AssignmentResponse toResponse(CmAssignment assignment) {
		User assignedBy = assignment.getAssignedBy();
		return new AssignmentResponse(
				CmPersonRefMapper.of(assignment.getCareManager()),
				assignment.getAssignedAt(),
				assignment.getUnassignedAt(),
				assignedBy == null ? null : CmPersonRefMapper.of(assignedBy),
				assignment.getReason());
	}

}
