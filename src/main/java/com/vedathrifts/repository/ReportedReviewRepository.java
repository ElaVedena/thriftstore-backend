package com.vedathrifts.repository;

import com.vedathrifts.model.ReportStatus;
import com.vedathrifts.model.ReportedReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportedReviewRepository extends JpaRepository<ReportedReview, Long> {
    
    Page<ReportedReview> findByStatus(ReportStatus status, Pageable pageable);
    
    List<ReportedReview> findByReviewId(Long reviewId);
    
    boolean existsByReviewIdAndReportedById(Long reviewId, Long userId);
}