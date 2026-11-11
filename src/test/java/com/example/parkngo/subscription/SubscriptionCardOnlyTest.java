package com.example.parkngo.subscription;

import com.example.parkngo.discount.Package;
import com.example.parkngo.discount.PackageService;
import com.example.parkngo.checkout.CheckoutService;
import com.example.parkngo.checkout.CheckoutQuote;
import com.example.parkngo.payment.Payment;
import com.example.parkngo.payment.PaymentService;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionCardOnlyTest {
    @Mock SubscriptionService subscriptionService;
    @Mock PackageService packageService;
    @Mock PaymentService paymentService;
    @Mock CheckoutService checkoutService;
    @Mock SecurityUtil securityUtil;
    @Mock Model model;
    @InjectMocks SubscriptionController controller;

    @Test
    void gatePaymentCannotCreateNewPackageSubscription() throws Exception {
        when(securityUtil.getCurrentUser()).thenReturn(new User());
        when(packageService.getPackageById(3L)).thenReturn(new Package());

        String result = controller.purchase(3L, "CASH_ON_ENTRY", null, null, null, null, null, model);

        assertEquals("redirect:/customer/packages?error=Card+is+the+only+payment+method+for+packages", result);
        verifyNoInteractions(subscriptionService, paymentService, checkoutService);
    }

    @Test
    void membershipIsActivatedOnlyAfterPaymentAndCheckoutSucceed() throws Exception {
        User user = new User(); user.setId(4L);
        Package pkg = new Package(); pkg.setId(3L); pkg.setPrice(java.math.BigDecimal.valueOf(18000));
        Subscription subscription = new Subscription(); subscription.setId(7L);
        subscription.setStatus(Subscription.SubscriptionStatus.PENDING);
        CheckoutQuote quote = new CheckoutQuote(); quote.setId(9L); quote.setFinalAmount(pkg.getPrice());
        Payment payment = new Payment(); payment.setStatus(Payment.PaymentStatus.PAID);
        when(securityUtil.getCurrentUser()).thenReturn(user);
        when(packageService.getPackageById(3L)).thenReturn(pkg);
        when(checkoutService.previewPackagePurchase(eq(4L), eq(pkg.getPrice()), isNull(), any())).thenReturn(quote);
        when(subscriptionService.subscribe(user, pkg)).thenReturn(subscription);
        when(paymentService.processSubscriptionPayment(user, 7L, pkg.getPrice(), "CREDIT_CARD", "4111111111111111")).thenReturn(payment);

        String result = controller.purchase(3L, "CREDIT_CARD", "4111111111111111", "Test Customer", "12/30", "123", null, model);

        assertEquals("redirect:/customer/subscription/receipt/7?success=Membership+activated!", result);
        var order = inOrder(subscriptionService, paymentService, checkoutService);
        order.verify(subscriptionService).subscribe(user, pkg);
        order.verify(paymentService).processSubscriptionPayment(user, 7L, pkg.getPrice(), "CREDIT_CARD", "4111111111111111");
        order.verify(checkoutService).commitCheckout(7L, 9L);
        order.verify(subscriptionService).activateAfterPayment(7L, 4L);
        verify(subscriptionService, never()).cancelSubscription(anyLong(), anyLong());
    }
}
