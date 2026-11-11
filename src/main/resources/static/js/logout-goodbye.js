(() => {
    const outro = document.getElementById('outro');
    const video = document.getElementById('outroVideo');
    const content = document.getElementById('outroContent');
    if (!outro || !video || !content) return;

    content.inert = true;
    let finished = false;
    function showFinal(fallback = false) {
        if (finished) return;
        finished = true;
        if (fallback) {
            video.pause();
            outro.classList.add('is-fallback');
        }
        outro.classList.add('is-finished');
        content.inert = false;
    }

    function skipVideo() {
        if (finished) return;
        if (Number.isFinite(video.duration) && video.duration > 0) {
            video.currentTime = Math.max(0, video.duration - 0.05);
        }
        video.pause();
        showFinal();
    }

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        showFinal(true);
        return;
    }

    video.addEventListener('ended', () => showFinal());
    video.addEventListener('error', () => showFinal(true));
    outro.addEventListener('click', skipVideo);
    const source = video.querySelector('source');
    if (source) source.addEventListener('error', () => showFinal(true));
    video.play().catch(() => showFinal(true));
})();
