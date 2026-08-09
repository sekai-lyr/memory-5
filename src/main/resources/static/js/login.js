const form = document.querySelector("#loginForm");
const toggle = document.querySelector("#togglePassword");
const password = document.querySelector("#password");

if (toggle && password) {
    toggle.addEventListener("click", () => {
        const hidden = password.type === "password";
        password.type = hidden ? "text" : "password";
        toggle.textContent = hidden ? "隐藏" : "显示";
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
        }
    });
}
