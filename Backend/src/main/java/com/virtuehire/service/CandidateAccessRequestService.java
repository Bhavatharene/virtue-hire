package com.virtuehire.service;

import com.virtuehire.model.Candidate;
import com.virtuehire.model.CandidateAccessRequest;
import com.virtuehire.model.CandidateAccessRequestStatus;
import com.virtuehire.model.Hr;
import com.virtuehire.repository.CandidateAccessRequestRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CandidateAccessRequestService {

    private final CandidateAccessRequestRepository repository;

    public CandidateAccessRequestService(CandidateAccessRequestRepository repository) {
        this.repository = repository;
    }

    public CandidateAccessRequest createOrRefreshRequest(Hr hr, Candidate candidate) {
        Optional<CandidateAccessRequest> existing = repository.findByHrIdAndCandidateId(hr.getId(), candidate.getId());
        if (existing.isPresent()) {
            CandidateAccessRequest request = existing.get();
            if (request.getStatus() == CandidateAccessRequestStatus.APPROVED
                    || request.getStatus() == CandidateAccessRequestStatus.PENDING) {
                return request;
            }

            request.setStatus(CandidateAccessRequestStatus.PENDING);
            request.setReviewedAt(null);
            return repository.save(request);
        }

        CandidateAccessRequest request = new CandidateAccessRequest();
        request.setHr(hr);
        request.setCandidate(candidate);
        request.setStatus(CandidateAccessRequestStatus.PENDING);
        return repository.save(request);
    }

    public boolean hasApprovedAccess(Long hrId, Long candidateId) {
        return findStatus(hrId, candidateId) == CandidateAccessRequestStatus.APPROVED;
    }

    public CandidateAccessRequestStatus findStatus(Long hrId, Long candidateId) {
        return repository.findByHrIdAndCandidateId(hrId, candidateId)
                .map(CandidateAccessRequest::getStatus)
                .orElse(null);
    }

    public Optional<CandidateAccessRequest> findRequest(Long hrId, Long candidateId) {
        return repository.findByHrIdAndCandidateId(hrId, candidateId);
    }

    public Map<Long, CandidateAccessRequestStatus> findStatusesForHr(Long hrId) {
        return repository.findByHrId(hrId).stream()
                .collect(Collectors.toMap(
                        request -> request.getCandidate().getId(),
                        CandidateAccessRequest::getStatus,
                        (first, second) -> second));
    }

    public List<CandidateAccessRequest> getRequests(CandidateAccessRequestStatus status) {
        if (status == null) {
            return repository.findAllByOrderByCreatedAtDesc();
        }
        return repository.findByStatusOrderByCreatedAtDesc(status);
    }

    public CandidateAccessRequest approve(Long requestId) {
        CandidateAccessRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Access request not found."));
        request.setStatus(CandidateAccessRequestStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now());
        return repository.save(request);
    }

    public CandidateAccessRequest reject(Long requestId) {
        CandidateAccessRequest request = repository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Access request not found."));
        request.setStatus(CandidateAccessRequestStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now());
        return repository.save(request);
    }

    public long countPending() {
        return repository.countByStatus(CandidateAccessRequestStatus.PENDING);
    }
}
