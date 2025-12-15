(function () {
    const page = document.documentElement.getAttribute("data-page") || "lectures";

    const navItems = document.querySelectorAll(".scp-nav-item");
    const sideLinks = document.querySelectorAll(".scp-sidebar-link");
    const mobileNavItems = document.querySelectorAll(".scp-mobile-nav-item");

    function syncActive(target) {
        navItems.forEach(btn =>
            btn.classList.toggle("is-active", btn.dataset.target === target)
        );
        sideLinks.forEach(btn =>
            btn.classList.toggle("is-active", btn.dataset.target === target)
        );
        mobileNavItems.forEach(li =>
            li.classList.toggle("is-active", li.dataset.target === target)
        );
    }

    syncActive(page);

    function handleNavClick(target) {
        syncActive(target);
        console.log("Navigate to page:", target);
    }

    navItems.forEach(btn => {
        btn.addEventListener("click", () => handleNavClick(btn.dataset.target));
    });

    sideLinks.forEach(btn => {
        btn.addEventListener("click", () => handleNavClick(btn.dataset.target));
    });

    mobileNavItems.forEach(li => {
        li.addEventListener("click", () => {
            handleNavClick(li.dataset.target);
            toggleMobileNav(false);
        });
    });

    // 모바일 메뉴 토글
    const navToggleBtn = document.querySelector(".scp-nav-toggle");
    const mobileNav = document.getElementById("scpMobileNav");
    let mobileNavOpen = false;

    function toggleMobileNav(openForce) {
        if (typeof openForce === "boolean") {
            mobileNavOpen = openForce;
        } else {
            mobileNavOpen = !mobileNavOpen;
        }
        if (mobileNav) {
            mobileNav.style.display = mobileNavOpen ? "block" : "none";
        }
    }

    if (navToggleBtn) {
        navToggleBtn.addEventListener("click", () => toggleMobileNav());
    }

    const logoutBtn = document.getElementById("scpLogoutBtn");
    if (logoutBtn) {
        logoutBtn.addEventListener("click", () => {
            console.log("TODO: /api/auth/logout 호출 후 로그인 페이지로 이동");
        });
    }
})();
