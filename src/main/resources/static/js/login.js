const form = document.querySelector("#loginForm");
const toggle = document.querySelector("#togglePassword");
const password = document.querySelector("#password");
const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

if (!reduceMotion) {
    const entranceTargets = [
        document.querySelector(".login-card")
    ].filter(Boolean);

    entranceTargets.forEach((element, index) => {
        element.animate([
            {opacity: 0, transform: "translateY(18px)"},
            {opacity: 1, transform: "translateY(0)"}
        ], {
            duration: 620,
            delay: index * 90,
            easing: "cubic-bezier(.22,.8,.25,1)",
            fill: "both"
        });
    });
}

if (toggle && password) {
    toggle.addEventListener("click", () => {
        const hidden = password.type === "password";
        password.type = hidden ? "text" : "password";
        toggle.textContent = hidden ? "隐藏" : "显示";
        if (!reduceMotion) {
            toggle.animate([
                {transform: "scale(1)"},
                {transform: "scale(.94)"},
                {transform: "scale(1)"}
            ], {duration: 220, easing: "ease-out"});
        }
    });
}

if (form) {
    form.addEventListener("submit", (event) => {
        const userName = document.querySelector("#userName").value.trim();
        const pwd = password.value.trim();
        document.querySelector("#userNameError").textContent = userName ? "" : "请输入用户名";
        document.querySelector("#passwordError").textContent = pwd ? "" : "请输入密码";
        if (!userName || !pwd) {
            event.preventDefault();
            if (!reduceMotion) {
                document.querySelector(".login-card")?.animate([
                    {transform: "translateX(0)"},
                    {transform: "translateX(-7px)"},
                    {transform: "translateX(7px)"},
                    {transform: "translateX(0)"}
                ], {duration: 300, easing: "ease-out"});
            }
        }
    });
}
