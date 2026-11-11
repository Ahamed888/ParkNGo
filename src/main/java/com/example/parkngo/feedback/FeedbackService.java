package com.example.parkngo.feedback;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class FeedbackService {
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public Feedback submitFeedback(Feedback feedback) throws Exception {
        if (feedback.getRating() < 1 || feedback.getRating() > 5) {
            throw new Exception("Rating must be between 1 and 5");
        }
        if (feedback.getComment() == null || feedback.getComment().trim().length() < 3 || feedback.getComment().trim().length() > 2000) {
            throw new Exception("Comment must be between 3 and 2000 characters");
        }
        if (feedback.getCustomer() == null || feedback.getReservation() == null) {
            throw new Exception("A customer and reservation are required");
        }
        if (feedbackRepository.existsByCustomerIdAndReservationId(
                feedback.getCustomer().getId(), feedback.getReservation().getId())) {
            throw new Exception("You have already submitted feedback for this reservation");
        }
        feedback.setStatus(Feedback.FeedbackStatus.PENDING);
        feedback.setCreatedAt(LocalDateTime.now());
        feedback.setComment(feedback.getComment().trim());
        return feedbackRepository.save(feedback);
    }

    public Feedback getFeedbackById(Long id) throws Exception {
        return feedbackRepository.findById(id)
                .orElseThrow(() -> new Exception("Feedback not found"));
    }

    public Feedback getCustomerFeedbackById(Long id, Long customerId) throws Exception {
        Feedback feedback = getFeedbackById(id);
        if (feedback.getCustomer() == null || !customerId.equals(feedback.getCustomer().getId())) {
            throw new Exception("Feedback not found");
        }
        return feedback;
    }

    public List<Feedback> getCustomerFeedback(Long customerId) {
        return feedbackRepository.findByCustomerId(customerId);
    }

    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAll();
    }

    public List<Feedback> getPendingFeedback() {
        return feedbackRepository.findByStatus(Feedback.FeedbackStatus.PENDING);
    }

    public Feedback respondToFeedback(Long id, String response) throws Exception {
        if (response == null || response.trim().isEmpty()) throw new Exception("Response is required");
        if (response.trim().length() > 2000) throw new Exception("Response must be 2000 characters or fewer");
        Feedback feedback = getFeedbackById(id);
        feedback.setAdminResponse(response.trim());
        feedback.setStatus(Feedback.FeedbackStatus.RESPONDED);
        feedback.setRespondedAt(LocalDateTime.now());
        return feedbackRepository.save(feedback);
    }

    public Feedback updateCustomerFeedback(Long id, Long customerId, Integer rating, String comment) throws Exception {
        Feedback feedback = getCustomerFeedbackById(id, customerId);
        if (feedback.getAdminResponse() != null && !feedback.getAdminResponse().trim().isEmpty()) {
            throw new Exception("Feedback cannot be edited after management has responded");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new Exception("Rating must be between 1 and 5");
        }
        if (comment == null || comment.trim().length() < 3 || comment.trim().length() > 2000) {
            throw new Exception("Comment must be between 3 and 2000 characters");
        }
        feedback.setRating(rating);
        feedback.setComment(comment.trim());
        return feedbackRepository.save(feedback);
    }

    public void deleteCustomerFeedback(Long id, Long customerId) throws Exception {
        Feedback feedback = getCustomerFeedbackById(id, customerId);
        if (feedback.getAdminResponse() != null && !feedback.getAdminResponse().trim().isEmpty()) {
            throw new Exception("Feedback cannot be deleted after management has responded");
        }
        feedbackRepository.delete(feedback);
    }

    public void deleteFeedback(Long id) {
        throw new UnsupportedOperationException("Feedback records cannot be deleted by administrator");
    }
}
