(() => {
    const key = `scroll:${location.pathname}${location.search}`;

    const restore = () => {
        const saved = sessionStorage.getItem(key);
        if (saved) {
            window.scrollTo(0, parseInt(saved, 10));
        }
    };

    const save = () => {
        sessionStorage.setItem(key, window.scrollY.toString());
    };

    document.addEventListener('DOMContentLoaded', restore, { once: true });
    window.addEventListener('beforeunload', save);
    document.querySelectorAll('form').forEach(form => {
        form.addEventListener('submit', save);
    });
})();
