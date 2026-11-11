package com.example.parkngo.feedback;

import com.example.parkngo.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @InjectMocks
    private FeedbackService feedbackService;

    private User testCustomer;
    private Feedback testFeedback;

    @BeforeEach
    void setUp() {
        testCustomer = new User();
        testCustomer.setId(10L);
        testCustomer.setFullName("John Doe");

        testFeedback = new Feedback();
        testFeedback.setId(100L);
        testFeedback.setCustomer(testCustomer);
        testFeedback.setRating(5);
        testFeedback.setComment("Great parking service!");
        testFeedback.setStatus(Feedback.FeedbackStatus.PENDING);
    }

    @Test
    void deleteCustomerFeedback_successWhenNoManagementResponse() throws Exception {
        when(feedbackRepository.findById(100L)).thenReturn(Optional.of(testFeedback));

        assertDoesNotThrow(() -> feedbackService.deleteCustomerFeedback(100L, 10L));
        verify(feedbackRepository, times(1)).delete(testFeedback);
    }

    @Test
    void deleteCustomerFeedback_throwsExceptionWhenManagementResponseExists() {
        testFeedback.setAdminResponse("Thank you for your review!");
        testFeedback.setStatus(Feedback.FeedbackStatus.RESPONDED);
        when(feedbackRepository.findById(100L)).thenReturn(Optional.of(testFeedback));

        Exception exception = assertThrows(Exception.class, () -> feedbackService.deleteCustomerFeedback(100L, 10L));
        assertEquals("Feedback cannot be deleted after management has responded", exception.getMessage());
        verify(feedbackRepository, never()).delete(any(Feedback.class));
    }

    @Test
    void updateCustomerFeedback_successWhenNoManagementResponse() throws Exception {
        when(feedbackRepository.findById(100L)).thenReturn(Optional.of(testFeedback));
        when(feedbackRepository.save(any(Feedback.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Feedback updated = feedbackService.updateCustomerFeedback(100L, 10L, 4, "Updated comment here");
        assertEquals(4, updated.getRating());
        assertEquals("Updated comment here", updated.getComment());
        verify(feedbackRepository, times(1)).save(testFeedback);
    }

    @Test
    void updateCustomerFeedback_throwsExceptionWhenManagementResponseExists() {
        testFeedback.setAdminResponse("Thank you for your review!");
        testFeedback.setStatus(Feedback.FeedbackStatus.RESPONDED);
        when(feedbackRepository.findById(100L)).thenReturn(Optional.of(testFeedback));

        Exception exception = assertThrows(Exception.class, () -> feedbackService.updateCustomerFeedback(100L, 10L, 4, "Updated comment"));
        assertEquals("Feedback cannot be edited after management has responded", exception.getMessage());
        verify(feedbackRepository, never()).save(any(Feedback.class));
    }

    @Test
    void deleteFeedback_adminDeletionThrowsUnsupportedOperationException() {
        UnsupportedOperationException exception = assertThrows(
                UnsupportedOperationException.class,
                () -> feedbackService.deleteFeedback(100L)
        );
        assertEquals("Feedback records cannot be deleted by administrator", exception.getMessage());
        verify(feedbackRepository, never()).deleteById(anyLong());
    }
}
