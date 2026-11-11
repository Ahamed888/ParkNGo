package com.example.parkngo.config;

import com.example.parkngo.feedback.NotificationService;
import com.example.parkngo.user.User;
import com.example.parkngo.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(GlobalControllerAdvice.class);

    private final SecurityUtil securityUtil;
    private final NotificationService notificationService;

    public GlobalControllerAdvice(SecurityUtil securityUtil, NotificationService notificationService) {
        this.securityUtil = securityUtil;
        this.notificationService = notificationService;
    }

    @InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    @ModelAttribute("currentUser")
    public User currentUser() {
        try {
            return securityUtil.getCurrentUser();
        } catch (Exception e) {
            log.debug("currentUser model attribute skipped: {}", e.getMessage());
            return null;
        }
    }

    @ModelAttribute("unreadNotificationCount")
    public long unreadNotificationCount() {
        try {
            User user = securityUtil.getCurrentUser();
            if (user == null || !securityUtil.hasRole("CUSTOMER")) {
                return 0L;
            }
            return notificationService.getUnreadCount(user.getId());
        } catch (Exception e) {
            log.debug("unreadNotificationCount skipped: {}", e.getMessage());
            return 0L;
        }
    }
}
