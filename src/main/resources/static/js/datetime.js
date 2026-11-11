/**
 * ParkNGo Datetime Utilities
 * Handles local date/time formatting without UTC drift.
 */
(function() {
    'use strict';

    const DatetimeUtil = {
        /**
         * Pads single digit numbers with a leading zero.
         */
        pad(n) {
            return n < 10 ? '0' + n : String(n);
        },

        /**
         * Formats a Date object to 'YYYY-MM-DDTHH:mm' in local time.
         */
        toLocalISOString(date) {
            if (!(date instanceof Date) || isNaN(date.getTime())) return '';
            const year = date.getFullYear();
            const month = this.pad(date.getMonth() + 1);
            const day = this.pad(date.getDate());
            const hours = this.pad(date.getHours());
            const minutes = this.pad(date.getMinutes());
            return `${year}-${month}-${day}T${hours}:${minutes}`;
        },

        /**
         * Parses 'YYYY-MM-DDTHH:mm' as a LOCAL Date (not UTC).
         */
        parseLocal(isoStr) {
            if (!isoStr) return null;
            const parts = isoStr.split('T');
            if (parts.length !== 2) return new Date(isoStr);
            const dateParts = parts[0].split('-').map(Number);
            const timeParts = parts[1].split(':').map(Number);
            if (dateParts.length < 3 || timeParts.length < 2) return null;
            return new Date(dateParts[0], dateParts[1] - 1, dateParts[2], timeParts[0], timeParts[1], 0);
        },

        /**
         * Format to friendly string: "Wed, 23 Sep 2026, 14:30"
         */
        formatFriendly(dateOrIso) {
            const d = typeof dateOrIso === 'string' ? this.parseLocal(dateOrIso) : dateOrIso;
            if (!d || isNaN(d.getTime())) return '';
            return d.toLocaleDateString('en-GB', {
                weekday: 'short',
                day: 'numeric',
                month: 'short',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        },

        /**
         * Format to date only: "Wed, 23 Sep"
         */
        formatDate(dateOrIso) {
            const d = typeof dateOrIso === 'string' ? this.parseLocal(dateOrIso) : dateOrIso;
            if (!d || isNaN(d.getTime())) return '';
            return d.toLocaleDateString('en-GB', {
                weekday: 'short',
                day: 'numeric',
                month: 'short'
            });
        },

        /**
         * Format to time only: "14:30"
         */
        formatTime(dateOrIso) {
            const d = typeof dateOrIso === 'string' ? this.parseLocal(dateOrIso) : dateOrIso;
            if (!d || isNaN(d.getTime())) return '';
            return d.toLocaleTimeString('en-GB', {
                hour: '2-digit',
                minute: '2-digit'
            });
        },

        /**
         * Calculates duration between two ISO strings in minutes.
         */
        diffMinutes(startIso, endIso) {
            const start = this.parseLocal(startIso);
            const end = this.parseLocal(endIso);
            if (!start || !end) return 0;
            return Math.max(0, Math.round((end.getTime() - start.getTime()) / 60000));
        },

        /**
         * Format duration nicely: "2h 30m" or "4 hours"
         */
        formatDuration(minutes) {
            if (!minutes || minutes <= 0) return '0 min';
            const hours = Math.floor(minutes / 60);
            const mins = minutes % 60;
            if (hours === 0) return `${mins} min`;
            if (mins === 0) return hours === 1 ? '1 hour' : `${hours} hours`;
            return `${hours}h ${mins}m`;
        },

        /**
         * Gets next quarter hour from a reference date (default: now).
         */
        nextQuarterHour(refDate) {
            const d = refDate ? new Date(refDate) : new Date();
            const minutes = d.getMinutes();
            const remainder = minutes % 15;
            const diff = remainder === 0 ? 15 : 15 - remainder;
            d.setMinutes(minutes + diff, 0, 0);
            return d;
        },

        /**
         * Syncs a pair of datetime inputs so arrival is in the future
         * and departure is automatically kept after arrival.
         */
        syncDateTimeInputs(arrivalInput, departureInput, durationDisplayEl) {
            if (!arrivalInput || !departureInput) return;

            // Set min arrival to current local time (floored to minute)
            const now = new Date();
            now.setSeconds(0, 0);
            arrivalInput.min = this.toLocalISOString(now);

            function update() {
                const arrVal = arrivalInput.value;
                const depVal = departureInput.value;

                if (arrVal) {
                    // Departure min should be arrival
                    departureInput.min = arrVal;

                    if (!depVal || depVal <= arrVal) {
                        // Default departure to 2 hours after arrival
                        const arrDate = DatetimeUtil.parseLocal(arrVal);
                        if (arrDate) {
                            arrDate.setHours(arrDate.getHours() + 2);
                            departureInput.value = DatetimeUtil.toLocalISOString(arrDate);
                        }
                    }
                }

                if (durationDisplayEl && arrVal && departureInput.value) {
                    const mins = DatetimeUtil.diffMinutes(arrVal, departureInput.value);
                    durationDisplayEl.textContent = DatetimeUtil.formatDuration(mins);
                }
            }

            arrivalInput.addEventListener('change', update);
            departureInput.addEventListener('change', update);
        }
    };

    window.ParkNGo = window.ParkNGo || {};
    window.ParkNGo.Datetime = DatetimeUtil;

})();
