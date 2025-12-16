(function () {
    const html = document.documentElement;
    const pageId = (html.getAttribute("data-page") || "").toLowerCase();

    const tabs = document.querySelectorAll(".scp-tab");
    const logoutBtn = document.getElementById("scpLogoutBtn");

    function syncActiveTab() {
        tabs.forEach((tab) => {
            const target = (tab.dataset.target || "").toLowerCase();
            const isActive =
                pageId &&
                target &&
                (pageId === target || pageId.startsWith(target));
            tab.classList.toggle("is-active", isActive);
        });
    }

    // 탭 클릭 시 해당 URL로 이동
    tabs.forEach((tab) => {
        const href = tab.dataset.href;
        if (!href) return;

        tab.addEventListener("click", () => {
            window.location.href = href;
        });
    });

    if (logoutBtn) {
        logoutBtn.addEventListener("click", async () => {
            try {
                await fetch("/api/auth/logout", {
                    method: "POST",
                    credentials: "include"
                });
            } catch (e) {
                console.warn("logout error (ignored):", e);
            } finally {
                localStorage.removeItem("accessToken");
                window.location.href = "/member/login";
            }
        });
    }

    syncActiveTab();
})();
