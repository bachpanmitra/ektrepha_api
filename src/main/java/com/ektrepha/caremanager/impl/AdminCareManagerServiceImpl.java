package com.ektrepha.caremanager.impl;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ektrepha.caremanager.ImageResizer;
import com.ektrepha.caremanager.ShiftWindow;
import com.ektrepha.caremanager.dto.request.ConnectRequest;
import com.ektrepha.caremanager.dto.request.CreateCareManagerRequest;
import com.ektrepha.caremanager.dto.request.UpdateCareManagerRequest;
import com.ektrepha.caremanager.dto.response.CareManagerAdminResponse;
import com.ektrepha.caremanager.dto.response.CareManagerListResponse;
import com.ektrepha.caremanager.dto.response.ConnectResponse;
import com.ektrepha.caremanager.dto.response.PhotoUrlResponse;
import com.ektrepha.caremanager.event.CareManagerCreatedEvent;
import com.ektrepha.caremanager.service.AdminCareManagerService;
import com.ektrepha.caremanager.service.CmAreaResolver;
import com.ektrepha.caremanager.service.CmAssignmentService;
import com.ektrepha.caremanager.service.CmNotificationService;
import com.ektrepha.exception.AlreadyAssignedException;
import com.ektrepha.exception.CmHasAssignmentsException;
import com.ektrepha.exception.CmOverCapException;
import com.ektrepha.exception.CmResourceNotFoundException;
import com.ektrepha.model.CareManager;
import com.ektrepha.model.CmAssignment;
import com.ektrepha.model.CmChangeRequestKind;
import com.ektrepha.model.CmTaskStatus;
import com.ektrepha.model.Nanny;
import com.ektrepha.model.Parent;
import com.ektrepha.model.User;
import com.ektrepha.model.UserSource;
import com.ektrepha.model.UserStatus;
import com.ektrepha.model.UserType;
import com.ektrepha.repository.CareManagerRepository;
import com.ektrepha.repository.CmAssignmentRepository;
import com.ektrepha.repository.CmChangeRequestRepository;
import com.ektrepha.repository.NannyRepository;
import com.ektrepha.repository.ParentRepository;
import com.ektrepha.repository.UserRepository;
import com.ektrepha.storage.GcsPhotoUrlService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminCareManagerServiceImpl implements AdminCareManagerService {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
	private static final int PHOTO_SIZE = 400;

	private final CareManagerRepository careManagerRepository;
	private final UserRepository userRepository;
	private final ParentRepository parentRepository;
	private final NannyRepository nannyRepository;
	private final CmAssignmentRepository cmAssignmentRepository;
	private final CmAssignmentService cmAssignmentService;
	private final CmChangeRequestRepository cmChangeRequestRepository;
	private final CmAreaResolver cmAreaResolver;
	private final CmNotificationService cmNotificationService;
	private final GcsPhotoUrlService gcsPhotoUrlService;
	private final ApplicationEventPublisher eventPublisher;

	@Override
	@Transactional(readOnly = true)
	public CareManagerListResponse list() {
		return new CareManagerListResponse(careManagerRepository.findAll().stream().map(this::toAdminResponse).toList());
	}

	@Override
	@Transactional
	public CareManagerAdminResponse create(CreateCareManagerRequest request) {
		if (userRepository.existsByEmail(request.email()) || userRepository.existsByPhone(request.phone())
				|| careManagerRepository.findByEmail(request.email()).isPresent()
				|| careManagerRepository.findByPhone(request.phone()).isPresent()) {
			throw new AlreadyAssignedException("A user with this phone or email already exists");
		}

		User user = userRepository.save(User.builder()
				.name(request.name())
				.email(request.email())
				.phone(request.phone())
				.userSource(UserSource.EMAIL)
				.userType(UserType.CARE_MANAGER)
				.active(true)
				.build());

		CareManager careManager = careManagerRepository.save(CareManager.builder()
				.user(user)
				.name(request.name())
				.phone(request.phone())
				.email(request.email())
				.area(request.area())
				.maxParents((short) request.maxParentsOrDefault())
				.shiftStart(LocalTime.parse(request.shiftStart(), TIME_FORMAT))
				.shiftEnd(LocalTime.parse(request.shiftEnd(), TIME_FORMAT))
				.build());

		eventPublisher.publishEvent(new CareManagerCreatedEvent(careManager.getId()));
		return toAdminResponse(careManager);
	}

	@Override
	@Transactional
	public CareManagerAdminResponse update(String careManagerPublicId, UpdateCareManagerRequest request) {
		CareManager careManager = findByPublicIdOrThrow(careManagerPublicId);

		if (request.name() != null) {
			careManager.setName(request.name());
		}
		if (request.phone() != null) {
			careManager.setPhone(request.phone());
		}
		if (request.email() != null) {
			careManager.setEmail(request.email());
		}
		if (request.maxParents() != null) {
			careManager.setMaxParents((short) request.maxParents().intValue());
		}
		if (request.shiftStart() != null) {
			careManager.setShiftStart(LocalTime.parse(request.shiftStart(), TIME_FORMAT));
		}
		if (request.shiftEnd() != null) {
			careManager.setShiftEnd(LocalTime.parse(request.shiftEnd(), TIME_FORMAT));
		}
		careManagerRepository.save(careManager);
		return toAdminResponse(careManager);
	}

	@Override
	@Transactional
	public PhotoUrlResponse uploadPhoto(String careManagerPublicId, byte[] content, String contentType) {
		CareManager careManager = findByPublicIdOrThrow(careManagerPublicId);
		byte[] resized = ImageResizer.toSquareJpeg(content, PHOTO_SIZE);
		String key = "care-managers/" + careManager.getId() + "/photo-" + Instant.now().toEpochMilli() + ".jpg";
		gcsPhotoUrlService.upload(key, resized, "image/jpeg");
		String photoUrl = gcsPhotoUrlService.presign(key);
		careManager.setPhotoUrl(photoUrl);
		careManagerRepository.save(careManager);
		return new PhotoUrlResponse(photoUrl);
	}

	@Override
	@Transactional
	public ConnectResponse connect(String careManagerPublicId, ConnectRequest request, User admin) {
		CareManager careManager = findByPublicIdOrThrow(careManagerPublicId);

		List<Parent> parentsToConnect = new ArrayList<>();
		List<ConnectResponse.SkippedItem> skipped = new ArrayList<>();
		for (String parentPublicId : request.parentIdsOrEmpty()) {
			Optional<Parent> parent = parentRepository.findByPublicId(parentPublicId);
			if (parent.isEmpty()) {
				skipped.add(new ConnectResponse.SkippedItem(parentPublicId, "NOT_FOUND"));
			} else if (isAlreadyWith(parent.get().getCareManager(), careManager)) {
				skipped.add(new ConnectResponse.SkippedItem(parentPublicId, "ALREADY_ASSIGNED"));
			} else if (cmAreaResolver.resolve(parent.get()).filter(a -> a == careManager.getArea()).isEmpty()) {
				skipped.add(new ConnectResponse.SkippedItem(parentPublicId, "WRONG_AREA"));
			} else {
				parentsToConnect.add(parent.get());
			}
		}

		List<Nanny> nanniesToConnect = new ArrayList<>();
		for (String nannyPublicId : request.nannyIdsOrEmpty()) {
			Optional<Nanny> nanny = nannyRepository.findByPublicId(nannyPublicId);
			if (nanny.isEmpty()) {
				skipped.add(new ConnectResponse.SkippedItem(nannyPublicId, "NOT_FOUND"));
			} else if (isAlreadyWith(nanny.get().getCareManager(), careManager)) {
				skipped.add(new ConnectResponse.SkippedItem(nannyPublicId, "ALREADY_ASSIGNED"));
			} else {
				nanniesToConnect.add(nanny.get());
			}
		}

		long currentActiveParents = cmAssignmentService.countActiveParents(careManager);
		if (currentActiveParents + parentsToConnect.size() > careManager.getMaxParents()) {
			throw new CmOverCapException("Connecting " + parentsToConnect.size() + " parents would push "
					+ careManager.getName() + " over their cap of " + careManager.getMaxParents());
		}

		// Best-effort, same convention as every other notification in this codebase (push/SMS never
		// throw) — attempted before the writes below, program-order, to stay as faithful as
		// reasonably possible to "send before the commit" without fighting that house-wide
		// best-effort design by inventing a throw-on-failure variant just for this one call site.
		if (request.notifyParentsOrDefault()) {
			for (Parent parent : parentsToConnect) {
				cmNotificationService.notifyCareManagerChanging(parent, careManager);
			}
		}

		Instant now = Instant.now();
		for (Parent parent : parentsToConnect) {
			CmAssignment assignment = cmAssignmentService.assignParentTo(parent, careManager, request.reason(), admin);
			if (request.notifyParentsOrDefault()) {
				assignment.setParentNotifiedAt(now);
				cmAssignmentRepository.save(assignment);
			}
			cmChangeRequestRepository.findByParentIdAndKindAndStatus(parent.getId(), CmChangeRequestKind.CM, CmTaskStatus.OPEN)
					.ifPresent(cr -> {
						cr.setStatus(CmTaskStatus.DONE);
						cmChangeRequestRepository.save(cr);
					});
		}
		for (Nanny nanny : nanniesToConnect) {
			cmAssignmentService.assignNannyTo(nanny, careManager, request.reason(), admin);
		}

		if (request.autoAssignNewSignups() != null) {
			careManager.setAutoAssignNewSignups(request.autoAssignNewSignups());
			careManagerRepository.save(careManager);
		}

		return new ConnectResponse(
				new ConnectResponse.ConnectedCounts(parentsToConnect.size(), nanniesToConnect.size()),
				skipped,
				toAdminResponse(careManager));
	}

	@Override
	@Transactional
	public CareManagerAdminResponse deactivate(String careManagerPublicId) {
		CareManager careManager = findByPublicIdOrThrow(careManagerPublicId);
		if (cmAssignmentService.countActiveParents(careManager) > 0 || cmAssignmentService.countActiveNannies(careManager) > 0) {
			throw new CmHasAssignmentsException(careManager.getName() + " still has parents or nannies assigned");
		}
		careManager.setActive(false);
		careManagerRepository.save(careManager);

		User user = careManager.getUser();
		user.setActive(false);
		user.setStatus(UserStatus.DEACTIVATED);
		userRepository.save(user);

		return toAdminResponse(careManager);
	}

	private boolean isAlreadyWith(CareManager current, CareManager target) {
		return current != null && current.getId().equals(target.getId());
	}

	private CareManager findByPublicIdOrThrow(String publicId) {
		return careManagerRepository.findByPublicId(publicId)
				.orElseThrow(() -> new CmResourceNotFoundException("No Care Manager with id " + publicId));
	}

	private CareManagerAdminResponse toAdminResponse(CareManager cm) {
		return new CareManagerAdminResponse(
				cm.getPublicId(),
				cm.getName(),
				cm.getPhone(),
				cm.getEmail(),
				cm.getPhotoUrl(),
				cm.getArea(),
				cm.isActive(),
				cm.getMaxParents(),
				cmAssignmentService.countActiveParents(cm),
				cmAssignmentService.countActiveNannies(cm),
				TIME_FORMAT.format(cm.getShiftStart()),
				TIME_FORMAT.format(cm.getShiftEnd()),
				ShiftWindow.isWithin(cm.getShiftStart(), cm.getShiftEnd(), Instant.now()),
				cm.getCreatedAt());
	}

}
