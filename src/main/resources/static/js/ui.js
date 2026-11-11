/**
 * ParkNGo UI Primitives & Interaction Controller
 * "Road & Marking" Design System
 */
(function() {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        initToasts();
        initConfirmDialogs();
        initDropdowns();
        initMobileDrawers();
        initClipboard();
        initLiveClocks();
        initSegmentedControls();
    });

    /**
     * 1. Toast & Flash Auto-Dismiss (5s)
     */
    function initToasts() {
        const toasts = document.querySelectorAll('[data-toast], .toast-auto');
        toasts.forEach(toast => {
            const duration = parseInt(toast.getAttribute('data-duration') || '5000', 10);
            let timer = null;

            function startTimer() {
                timer = setTimeout(() => {
                    dismissToast(toast);
                }, duration);
            }

            function clearTimer() {
                if (timer) clearTimeout(timer);
            }

            toast.addEventListener('mouseenter', clearTimer);
            toast.addEventListener('mouseleave', startTimer);
            startTimer();

            const closeBtn = toast.querySelector('[data-dismiss="toast"], [data-dismiss="alert"]');
            if (closeBtn) {
                closeBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    clearTimer();
                    dismissToast(toast);
                });
            }
        });
    }

    function dismissToast(toast) {
        toast.style.transition = 'opacity 0.3s ease, transform 0.3s ease';
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-8px)';
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    }

    /**
     * 2. Accessible Confirmation Dialog (Replaces native window.confirm)
     */
    let confirmModal = null;
    let pendingAction = null;

    function ensureConfirmModal() {
        if (confirmModal) return confirmModal;

        let existing = document.getElementById('parkngo-confirm-modal');
        if (!existing) {
            existing = document.createElement('div');
            existing.id = 'parkngo-confirm-modal';
            existing.className = 'fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/70 backdrop-blur-xs hidden transition-opacity';
            existing.setAttribute('role', 'dialog');
            existing.setAttribute('aria-modal', 'true');
            existing.setAttribute('aria-labelledby', 'confirm-modal-title');
            existing.innerHTML = `
                <div class="relative w-full max-w-md bg-white rounded-xl shadow-2xl border border-slate-200 overflow-hidden transform transition-all p-6">
                    <div class="flex items-start gap-4">
                        <div class="flex-shrink-0 w-11 h-11 rounded-full bg-amber-50 text-amber-600 border border-amber-200 flex items-center justify-center" id="confirm-modal-icon">
                            <i class="fa-solid fa-triangle-exclamation text-lg"></i>
                        </div>
                        <div class="flex-1">
                            <h3 class="text-base font-bold text-slate-900" id="confirm-modal-title">Please Confirm</h3>
                            <p class="mt-2 text-sm text-slate-600 leading-relaxed" id="confirm-modal-msg">Are you sure you want to proceed?</p>
                        </div>
                    </div>
                    <div class="mt-6 flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
                        <button type="button" id="confirm-modal-cancel" class="btn btn-secondary px-4 py-2 text-sm font-medium">Keep</button>
                        <button type="button" id="confirm-modal-proceed" class="btn btn-danger px-4 py-2 text-sm font-medium">Confirm</button>
                    </div>
                </div>
            `;
            document.body.appendChild(existing);
        }

        const cancelBtn = existing.querySelector('#confirm-modal-cancel');
        const proceedBtn = existing.querySelector('#confirm-modal-proceed');

        cancelBtn.addEventListener('click', closeConfirmModal);
        proceedBtn.addEventListener('click', () => {
            if (typeof pendingAction === 'function') {
                const act = pendingAction;
                pendingAction = null;
                closeConfirmModal();
                act();
            } else {
                closeConfirmModal();
            }
        });

        existing.addEventListener('click', (e) => {
            if (e.target === existing) {
                closeConfirmModal();
            }
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && !existing.classList.contains('hidden')) {
                closeConfirmModal();
            }
        });

        confirmModal = existing;
        return confirmModal;
    }

    function openConfirmModal(message, proceedText, isDanger, onProceed) {
        const modal = ensureConfirmModal();
        const msgEl = modal.querySelector('#confirm-modal-msg');
        const proceedBtn = modal.querySelector('#confirm-modal-proceed');
        const iconEl = modal.querySelector('#confirm-modal-icon');

        msgEl.textContent = message || 'Are you sure you want to proceed?';
        proceedBtn.textContent = proceedText || 'Confirm';

        if (isDanger) {
            proceedBtn.className = 'btn btn-danger px-4 py-2 text-sm font-medium';
            iconEl.className = 'flex-shrink-0 w-11 h-11 rounded-full bg-rose-50 text-rose-600 border border-rose-200 flex items-center justify-center';
            iconEl.innerHTML = '<i class="fa-solid fa-triangle-exclamation text-lg"></i>';
        } else {
            proceedBtn.className = 'btn btn-primary px-4 py-2 text-sm font-medium';
            iconEl.className = 'flex-shrink-0 w-11 h-11 rounded-full bg-blue-50 text-blue-600 border border-blue-200 flex items-center justify-center';
            iconEl.innerHTML = '<i class="fa-solid fa-circle-question text-lg"></i>';
        }

        pendingAction = onProceed;
        modal.classList.remove('hidden');
        proceedBtn.focus();
    }

    function closeConfirmModal() {
        if (confirmModal) {
            confirmModal.classList.add('hidden');
            pendingAction = null;
        }
    }

    function initConfirmDialogs() {
        // Intercept link clicks with data-confirm
        document.addEventListener('click', (e) => {
            // If inside a form, let form submit listener manage confirmation
            if (e.target.closest('form')) return;

            const trigger = e.target.closest('[data-confirm]');
            if (!trigger) return;

            e.preventDefault();
            const message = trigger.getAttribute('data-confirm');
            const proceedText = trigger.getAttribute('data-confirm-btn') || 'Proceed';
            const isDanger = trigger.classList.contains('btn-danger') || trigger.getAttribute('data-confirm-tone') === 'danger';

            openConfirmModal(message, proceedText, isDanger, () => {
                if (trigger.tagName === 'A' && trigger.href) {
                    window.location.href = trigger.href;
                }
            });
        });

        // Intercept form submissions with data-confirm
        document.addEventListener('submit', (e) => {
            const form = e.target;
            const confirmMsg = form.getAttribute('data-confirm') || (form.querySelector('[data-confirm]') ? form.querySelector('[data-confirm]').getAttribute('data-confirm') : null);
            if (confirmMsg && !form.dataset.confirmed) {
                e.preventDefault();
                const proceedText = form.getAttribute('data-confirm-btn') || (form.querySelector('[data-confirm-btn]') ? form.querySelector('[data-confirm-btn]').getAttribute('data-confirm-btn') : 'Proceed');
                const isDanger = form.getAttribute('data-confirm-tone') === 'danger' || form.querySelector('.btn-danger') !== null;

                openConfirmModal(confirmMsg, proceedText, isDanger, () => {
                    form.dataset.confirmed = 'true';
                    HTMLFormElement.prototype.submit.call(form);
                });
            }
        });
    }

    /**
     * 3. Accessible Dropdown Toggles
     */
    function initDropdowns() {
        document.addEventListener('click', (e) => {
            const toggle = e.target.closest('[data-dropdown-toggle]');
            const openDropdowns = document.querySelectorAll('[data-dropdown-menu]:not(.hidden)');

            if (toggle) {
                e.preventDefault();
                const targetId = toggle.getAttribute('data-dropdown-toggle');
                const targetMenu = document.getElementById(targetId);
                if (targetMenu) {
                    const isHidden = targetMenu.classList.contains('hidden');
                    // Close others
                    openDropdowns.forEach(d => {
                        if (d !== targetMenu) d.classList.add('hidden');
                    });
                    targetMenu.classList.toggle('hidden', !isHidden);
                    toggle.setAttribute('aria-expanded', String(isHidden));
                }
                return;
            }

            // Click outside closes all open menus
            if (!e.target.closest('[data-dropdown-menu]')) {
                openDropdowns.forEach(menu => {
                    menu.classList.add('hidden');
                    const opener = document.querySelector(`[data-dropdown-toggle="${menu.id}"]`);
                    if (opener) opener.setAttribute('aria-expanded', 'false');
                });
            }
        });
    }

    /**
     * 4. Mobile Menu / Drawer Toggle
     */
    function initMobileDrawers() {
        // Support both old #mobileBtn/#mobileMenu and new [data-mobile-menu-toggle]
        const legacyBtn = document.getElementById('mobileBtn');
        const legacyMenu = document.getElementById('mobileMenu');
        if (legacyBtn && legacyMenu) {
            legacyBtn.addEventListener('click', (e) => {
                e.preventDefault();
                legacyMenu.classList.toggle('hidden');
            });
        }

        document.addEventListener('click', (e) => {
            const btn = e.target.closest('[data-mobile-menu-toggle]');
            if (!btn) return;
            e.preventDefault();
            const targetId = btn.getAttribute('data-mobile-menu-toggle');
            const targetMenu = document.getElementById(targetId);
            if (targetMenu) {
                const drawer = targetMenu.classList.contains('transform');
                targetMenu.classList.toggle(drawer ? '-translate-x-full' : 'hidden');
                const isOpen = !targetMenu.classList.contains(drawer ? '-translate-x-full' : 'hidden');
                btn.setAttribute('aria-expanded', String(isOpen));
            }
        });
    }

    /**
     * 5. Copy to Clipboard
     */
    function initClipboard() {
        document.addEventListener('click', (e) => {
            const copyBtn = e.target.closest('[data-copy]');
            if (!copyBtn) return;
            e.preventDefault();

            const textToCopy = copyBtn.getAttribute('data-copy');
            if (!textToCopy) return;

            navigator.clipboard.writeText(textToCopy).then(() => {
                const originalHtml = copyBtn.innerHTML;
                copyBtn.innerHTML = '<i class="fa-solid fa-check text-emerald-500 mr-1"></i> Copied';
                copyBtn.classList.add('text-emerald-600');
                setTimeout(() => {
                    copyBtn.innerHTML = originalHtml;
                    copyBtn.classList.remove('text-emerald-600');
                }, 2000);
            }).catch(err => {
                console.error('Failed to copy text: ', err);
            });
        });
    }

    /**
     * 6. Live Clock (Staff console)
     */
    function initLiveClocks() {
        const clocks = document.querySelectorAll('[data-live-clock]');
        if (clocks.length === 0) return;

        function updateClocks() {
            const now = new Date();
            // Formats to Colombo / Sri Lanka time (Asia/Colombo)
            const timeString = now.toLocaleTimeString('en-GB', {
                hour: '2-digit',
                minute: '2-digit',
                second: '2-digit',
                timeZone: 'Asia/Colombo'
            });
            clocks.forEach(clock => {
                clock.textContent = timeString;
            });
        }

        updateClocks();
        setInterval(updateClocks, 1000);
    }

    /**
     * 7. Segmented Controls / Tabs
     */
    function initSegmentedControls() {
        document.addEventListener('click', (e) => {
            const btn = e.target.closest('[data-tab-target]');
            if (!btn) return;

            const group = btn.closest('[data-tab-group]');
            if (!group) return;

            const targetSelector = btn.getAttribute('data-tab-target');
            const targetPane = document.querySelector(targetSelector);
            if (!targetPane) return;

            // Deactivate other tabs in group
            group.querySelectorAll('[data-tab-target]').forEach(otherBtn => {
                otherBtn.classList.remove('active', 'bg-blue-600', 'text-white', 'shadow-xs');
                otherBtn.classList.add('text-slate-600');
                otherBtn.setAttribute('aria-selected', 'false');
            });

            // Activate clicked tab
            btn.classList.add('active', 'bg-blue-600', 'text-white', 'shadow-xs');
            btn.classList.remove('text-slate-600');
            btn.setAttribute('aria-selected', 'true');

            // Toggle panes
            const container = targetPane.parentElement;
            if (container) {
                container.querySelectorAll('[data-tab-pane]').forEach(pane => {
                    pane.classList.add('hidden');
                });
                targetPane.classList.remove('hidden');
            }
        });
    }

    // Expose helpers globally
    window.ParkNGo = window.ParkNGo || {};
    window.ParkNGo.confirm = openConfirmModal;
    window.ParkNGo.dismissToast = dismissToast;

})();
