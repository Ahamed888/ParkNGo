package com.example.parkngo.subscription;

import com.example.parkngo.payment.Payment;
import com.example.parkngo.payment.PaymentRepository;
import com.example.parkngo.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentActivationTest {
    @Mock SubscriptionRepository subscriptions;
    @Mock PaymentRepository payments;

    @Test
    void settledPaymentActivatesPendingMembership() throws Exception {
        Subscription subscription = pendingSubscription(); Payment payment = new Payment(); payment.setStatus(Payment.PaymentStatus.PAID);
        when(subscriptions.findByIdForUpdate(7L)).thenReturn(Optional.of(subscription)); when(payments.findBySubscriptionId(7L)).thenReturn(List.of(payment)); when(subscriptions.save(any())).thenAnswer(i -> i.getArgument(0));
        Subscription activated = new SubscriptionService(subscriptions, payments).activateAfterPayment(7L, 4L);
        assertEquals(Subscription.SubscriptionStatus.ACTIVE, activated.getStatus());
    }

    @Test
    void failedOrUncompletedPaymentCannotActivateMembership() {
        Subscription subscription = pendingSubscription(); Payment payment = new Payment(); payment.setStatus(Payment.PaymentStatus.FAILED);
        when(subscriptions.findByIdForUpdate(7L)).thenReturn(Optional.of(subscription)); when(payments.findBySubscriptionId(7L)).thenReturn(List.of(payment));
        assertThrows(Exception.class, () -> new SubscriptionService(subscriptions, payments).activateAfterPayment(7L, 4L));
        assertEquals(Subscription.SubscriptionStatus.PENDING, subscription.getStatus()); verify(subscriptions, never()).save(any());
    }

    private Subscription pendingSubscription() { User user = new User(); user.setId(4L); Subscription subscription = new Subscription(); subscription.setId(7L); subscription.setCustomer(user); subscription.setStatus(Subscription.SubscriptionStatus.PENDING); return subscription; }
}
