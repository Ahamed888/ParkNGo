(function () {
    'use strict';

    const fieldSelector = 'input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="reset"]), select, textarea';
    const trimTypes = new Set(['text', 'email', 'tel', 'url', 'search']);

    function fieldContainer(field) {
        return field.closest('.field, .form-field, [data-field]') || field.parentElement;
    }

    function fieldLabel(field) {
        if (field.dataset.fieldLabel) return field.dataset.fieldLabel;
        let label = field.id ? document.querySelector(`label[for="${CSS.escape(field.id)}"]`) : null;
        label = label || field.closest('label') || fieldContainer(field)?.querySelector('label');
        if (label) return label.textContent.replace(/\s*\*\s*$/, '').replace(/\s*\(Optional\)\s*$/i, '').trim();
        return (field.name || 'This field').replace(/([a-z])([A-Z])/g, '$1 $2').replace(/[_-]+/g, ' ').replace(/^./, value => value.toUpperCase());
    }

    function ensureId(field) {
        if (!field.id) {
            const base = (field.name || 'field').replace(/[^A-Za-z0-9_-]/g, '-');
            field.id = `${base}-${Math.random().toString(36).slice(2, 8)}`;
        }
        const label = fieldContainer(field)?.querySelector('label:not([for])');
        if (label && !label.contains(field)) label.htmlFor = field.id;
    }

    function isEmpty(field) {
        if (field.type === 'checkbox' || field.type === 'radio') return !field.checked;
        return field.value.trim() === '';
    }

    function hasValidationRules(field) {
        return field.required || field.pattern || field.min !== '' || field.max !== '' || field.step !== ''
            || field.minLength > 0 || field.maxLength < 524288
            || ['email', 'url', 'number', 'date', 'datetime-local', 'month', 'week', 'time'].includes(field.type)
            || field.dataset.match || field.dataset.expiry === 'true' || field.validity.customError;
    }

    function messageFor(field) {
        const label = fieldLabel(field);
        const validity = field.validity;
        if (validity.customError) return field.validationMessage || `${label} is not valid.`;
        if (validity.valueMissing) return field.dataset.errorRequired || `${label} is required.`;
        if (validity.typeMismatch) {
            if (field.type === 'email') return 'Enter a valid email address, for example name@example.com.';
            if (field.type === 'url') return 'Enter a complete web address beginning with http:// or https://.';
            return `Enter a valid ${label.toLowerCase()}.`;
        }
        if (validity.patternMismatch) return field.dataset.errorPattern || field.title || `Use the required format for ${label.toLowerCase()}.`;
        if (validity.tooShort) return `${label} must be at least ${field.minLength} characters.`;
        if (validity.tooLong) return `${label} must be no more than ${field.maxLength} characters.`;
        if (validity.rangeUnderflow) return `${label} must be ${field.min} or greater.`;
        if (validity.rangeOverflow) return `${label} must be ${field.max} or less.`;
        if (validity.stepMismatch) return `${label} must use increments of ${field.step}.`;
        if (validity.badInput) return `Enter a valid ${label.toLowerCase()}.`;
        return '';
    }

    function messageElement(field) {
        ensureId(field);
        const container = fieldContainer(field);
        let message = container?.querySelector(`.form-validation-message[data-for="${CSS.escape(field.id)}"]`);
        if (!message) {
            message = document.createElement('small');
            message.id = `${field.id}-validation`;
            message.dataset.for = field.id;
            message.className = 'form-validation-message';
            message.setAttribute('aria-live', 'polite');
            container?.appendChild(message);
        }
        const describedBy = new Set((field.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean));
        describedBy.add(message.id);
        field.setAttribute('aria-describedby', Array.from(describedBy).join(' '));
        return message;
    }

    function validateRelatedRules(field) {
        field.setCustomValidity('');
        if (field.dataset.match) {
            const other = document.querySelector(field.dataset.match);
            if (other && field.value && field.value !== other.value) field.setCustomValidity(field.dataset.errorMatch || `${fieldLabel(field)} must match.`);
        }
        if (field.dataset.expiry === 'true' && field.value) {
            const match = /^(0[1-9]|1[0-2])\/(\d{2})$/.exec(field.value);
            if (!match) {
                field.setCustomValidity('Enter a valid future expiry date in MM/YY format.');
            } else {
                const expiry = new Date(2000 + Number(match[2]), Number(match[1]), 0, 23, 59, 59);
                if (expiry < new Date()) field.setCustomValidity('This card has expired. Enter a future expiry date.');
            }
        }
    }

    function updateField(field, force) {
        if (field.disabled || field.readOnly || (!force && !field.dataset.validationTouched)) return true;
        validateRelatedRules(field);
        if (!hasValidationRules(field)) return true;
        const empty = isEmpty(field);
        const valid = field.checkValidity();
        const message = messageElement(field);
        const showValid = field.dataset.showValid === 'true';
        field.classList.toggle('form-field-valid', showValid && valid && !empty);
        field.classList.toggle('form-field-invalid', !valid);
        field.setAttribute('aria-invalid', String(!valid));
        message.textContent = valid && showValid && !empty ? 'Looks good.' : (valid ? '' : messageFor(field));
        message.classList.toggle('is-valid', showValid && valid && !empty);
        message.classList.toggle('is-invalid', !valid);
        return valid;
    }

    function normalize(field) {
        if (field.dataset.uppercase === 'true') field.value = field.value.toUpperCase();
        if (field.dataset.digitsOnly === 'true') field.value = field.value.replace(/\D/g, '').slice(0, Number(field.maxLength) || undefined);
        if (field.dataset.cardNumber === 'true') {
            const maxDigits = Number(field.dataset.cardMaxDigits || 16);
            const digits = field.value.replace(/\D/g, '').slice(0, maxDigits);
            field.value = digits.replace(/(.{4})/g, '$1 ').trim();
        }
        if (field.dataset.expiry === 'true') {
            const digits = field.value.replace(/\D/g, '').slice(0, 4);
            field.value = digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits;
        }
    }

    function trimField(field) {
        if (field.dataset.trim === 'false') return;
        if (trimTypes.has(field.type) || field.tagName === 'TEXTAREA') field.value = field.value.trim();
    }

    function setSubmitting(form, submitter) {
        if (form.method.toLowerCase() === 'get' || form.dataset.submitLock === 'false') return;
        form.querySelectorAll('button:not([type]), button[type="submit"], input[type="submit"]').forEach(button => {
            button.dataset.originalDisabled = String(button.disabled);
            button.disabled = true;
            button.setAttribute('aria-disabled', 'true');
        });
        if (submitter) {
            submitter.classList.add('is-submitting');
            submitter.setAttribute('aria-busy', 'true');
            const loadingText = submitter.dataset.loadingText;
            if (loadingText && submitter.tagName === 'BUTTON') {
                submitter.dataset.originalText = submitter.textContent;
                submitter.textContent = loadingText;
            }
            if (submitter.name) {
                const hidden = document.createElement('input');
                hidden.type = 'hidden';
                hidden.name = submitter.name;
                hidden.value = submitter.value;
                form.appendChild(hidden);
            }
        }
        form.dataset.submitting = 'true';
    }

    function restoreForms() {
        document.querySelectorAll('form[data-submitting="true"]').forEach(form => {
            form.querySelectorAll('button:not([type]), button[type="submit"], input[type="submit"]').forEach(button => {
                button.disabled = button.dataset.originalDisabled === 'true';
                button.removeAttribute('aria-disabled');
                button.removeAttribute('aria-busy');
                button.classList.remove('is-submitting');
                if (button.dataset.originalText) button.textContent = button.dataset.originalText;
            });
            delete form.dataset.submitting;
        });
    }

    function initializeForm(form) {
        const fields = Array.from(form.querySelectorAll(fieldSelector));
        fields.forEach(field => {
            ensureId(field);
            if (field.type === 'password' && field.dataset.passwordToggle !== 'false') {
                const wrapper = document.createElement('div');
                wrapper.className = 'password-field';
                field.insertAdjacentElement('beforebegin', wrapper);
                wrapper.appendChild(field);
                const toggle = document.createElement('button');
                toggle.type = 'button';
                toggle.className = 'password-toggle';
                toggle.setAttribute('aria-controls', field.id);
                const label = field.autocomplete === 'cc-csc' ? 'security code' : 'password';
                function refreshToggle() {
                    const showing = field.type === 'text';
                    toggle.setAttribute('aria-label', `${showing ? 'Hide' : 'Show'} ${label}`);
                    toggle.setAttribute('title', `${showing ? 'Hide' : 'Show'} ${label}`);
                    toggle.setAttribute('aria-pressed', String(showing));
                    toggle.innerHTML = showing
                        ? '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m3 3 18 18M10.6 10.6a2 2 0 0 0 2.8 2.8M9.9 5.2A11 11 0 0 1 12 5c7 0 10 7 10 7a17 17 0 0 1-3 4M6.5 6.5C3.5 8.5 2 12 2 12s3 7 10 7a11 11 0 0 0 5.5-1.5"/></svg>'
                        : '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg>';
                }
                toggle.addEventListener('click', () => {
                    const showing = field.type === 'text';
                    field.type = showing ? 'password' : 'text';
                    refreshToggle();
                });
                refreshToggle();
                wrapper.appendChild(toggle);
            }
            if (field.classList.contains('input-error')) {
                field.dataset.validationTouched = 'true';
                field.setAttribute('aria-invalid', 'true');
            }
            const serverError = fieldContainer(field)?.querySelector('.field-error');
            if (serverError && serverError.textContent.trim()) {
                serverError.id = serverError.id || `${field.id}-server-error`;
                const describedBy = new Set((field.getAttribute('aria-describedby') || '').split(/\s+/).filter(Boolean));
                describedBy.add(serverError.id);
                field.setAttribute('aria-describedby', Array.from(describedBy).join(' '));
                field.setAttribute('aria-invalid', 'true');
            }
            field.addEventListener('input', () => {
                normalize(field);
                if (field.dataset.validationTouched) updateField(field);
                fields.filter(candidate => candidate.dataset.match === `#${field.id}`).forEach(candidate => updateField(candidate));
            });
            field.addEventListener('change', () => {
                field.dataset.validationTouched = 'true';
                updateField(field);
            });
            field.addEventListener('blur', () => {
                trimField(field);
                field.dataset.validationTouched = 'true';
                updateField(field);
            });
            if (field.type === 'number') field.addEventListener('keydown', event => {
                if (['e', 'E'].includes(event.key) || ((event.key === '-' || event.key === '+') && field.min !== '' && Number(field.min) >= 0)) event.preventDefault();
            });
        });
        form.addEventListener('submit', event => {
            if (event.defaultPrevented) return;
            if (form.dataset.submitting === 'true') {
                event.preventDefault();
                return;
            }
            let firstInvalid = null;
            fields.forEach(field => {
                trimField(field);
                field.dataset.validationTouched = 'true';
                if (!updateField(field, true) && !firstInvalid) firstInvalid = field;
            });
            if (firstInvalid) {
                event.preventDefault();
                firstInvalid.focus({preventScroll: true});
                firstInvalid.scrollIntoView({behavior: 'smooth', block: 'center'});
                return;
            }
            setSubmitting(form, event.submitter);
        });
    }

    document.addEventListener('DOMContentLoaded', () => document.querySelectorAll('form').forEach(initializeForm));
    window.addEventListener('pageshow', restoreForms);
}());
