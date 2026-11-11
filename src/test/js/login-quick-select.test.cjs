const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const template = fs.readFileSync(
    path.join(__dirname, '../../main/resources/templates/auth/login.html'),
    'utf8'
);
const inlineScript = template.match(/<script>([\s\S]*?)<\/script>/)?.[1];

test('role selector fills credentials without submitting the login form', () => {
    assert.ok(inlineScript, 'login page inline script was not found');

    const events = [];
    const username = { value: '', dispatchEvent(event) { events.push(['username', event.type]); } };
    const password = { value: '', dispatchEvent(event) { events.push(['password', event.type]); } };
    let submitted = false;
    const form = { submit() { submitted = true; } };
    const document = {
        getElementById(id) { return { loginForm: form, username, password }[id]; },
        querySelectorAll() { return []; }
    };
    class Event {
        constructor(type) { this.type = type; }
    }

    const context = { document, Event, window: {} };
    vm.runInNewContext(inlineScript, context);
    context.quickLogin('staff@example.com', 'staff123');

    assert.equal(username.value, 'staff@example.com');
    assert.equal(password.value, 'staff123');
    assert.equal(submitted, false);
    assert.deepEqual(events, [
        ['username', 'input'], ['username', 'change'],
        ['password', 'input'], ['password', 'change']
    ]);
});
