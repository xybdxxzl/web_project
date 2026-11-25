import { postLoginUrl, request } from "./api.js";
import { currentUser } from "./nav.js";

const form = document.getElementById("auth-form");
const message = document.getElementById("form-message");
const submit = form.querySelector('button[type="submit"]');
const isRegistration = form.dataset.action === "register";
const destination = postLoginUrl();
const authQuery = new URLSearchParams({ redirect: destination });
document.querySelector(".account-link a").href =
    (isRegistration ? "/login.html?" : "/register.html?") + authQuery;

currentUser.then(user => {
    if (user) {
        window.location.replace(destination);
    }
});

if (!isRegistration && new URLSearchParams(window.location.search).has("registered")) {
    message.textContent = "注册成功，请登录。";
}

form.addEventListener("submit", async event => {
    event.preventDefault();
    message.textContent = "";
    submit.disabled = true;

    try {
        await request(form.action, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(Object.fromEntries(new FormData(form))),
            redirectOnUnauthorized: false
        });
        if (isRegistration) {
            authQuery.set("registered", "1");
            window.location.replace("/login.html?" + authQuery);
            return;
        }
        window.location.replace(destination);
    } catch (error) {
        message.textContent = error.message;
        submit.disabled = false;
    }
});
