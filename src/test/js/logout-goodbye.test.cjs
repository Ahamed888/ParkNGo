const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const script = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/logout-goodbye.js'), 'utf8');

function page({ reducedMotion = false, playRejects = false } = {}) {
    const classes = new Set();
    const handlers = {};
    const sourceHandlers = {};
    const content = { inert: false };
    const outroHandlers = {};
    const video = {
        playCalls: 0,
        paused: false,
        duration: 10,
        currentTime: 0,
        play() {
            this.playCalls++;
            return playRejects ? Promise.reject(new Error('Autoplay blocked')) : Promise.resolve();
        },
        pause() { this.paused = true; },
        addEventListener(name, callback) { handlers[name] = callback; },
        querySelector() { return { addEventListener(name, callback) { sourceHandlers[name] = callback; } }; }
    };
    const outro = {
        classList: { add(name) { classes.add(name); } },
        addEventListener(name, callback) { outroHandlers[name] = callback; }
    };
    const document = {
        getElementById(id) { return { outro, outroVideo: video, outroContent: content }[id]; }
    };
    const window = { matchMedia() { return { matches: reducedMotion }; } };
    vm.runInNewContext(script, { document, window });
    return { classes, handlers, sourceHandlers, outroHandlers, content, video };
}

test('natural ending reveals content over the final video frame', () => {
    const state = page();
    assert.equal(state.content.inert, true);
    state.handlers.ended();
    assert.equal(state.content.inert, false);
    assert.equal(state.classes.has('is-finished'), true);
    assert.equal(state.classes.has('is-fallback'), false);
    assert.equal(state.video.paused, false);
});

test('video failure reveals the static fallback', () => {
    const state = page();
    state.sourceHandlers.error();
    assert.equal(state.content.inert, false);
    assert.equal(state.classes.has('is-fallback'), true);
    assert.equal(state.video.paused, true);
});

test('clicking the video screen skips to the same final state as natural ending', () => {
    const state = page();
    state.outroHandlers.click();
    assert.equal(state.video.paused, true);
    assert.equal(state.video.currentTime, 9.95);
    assert.equal(state.content.inert, false);
    assert.equal(state.classes.has('is-finished'), true);
    assert.equal(state.classes.has('is-fallback'), false);
});

test('reduced motion skips video playback', () => {
    const state = page({ reducedMotion: true });
    assert.equal(state.video.playCalls, 0);
    assert.equal(state.classes.has('is-finished'), true);
    assert.equal(state.classes.has('is-fallback'), true);
});

test('autoplay rejection reveals the fallback', async () => {
    const state = page({ playRejects: true });
    await Promise.resolve();
    assert.equal(state.classes.has('is-fallback'), true);
    assert.equal(state.content.inert, false);
});
