package io.github.chechelpo.frplm.sandboxMCP.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/account")
public class WebUiAccountController {

    private final WebUiAccountManager account;

    public WebUiAccountController(WebUiAccountManager account) {
        this.account = account;
    }

    @GetMapping
    public WebUiAccountManager.AccountDetails details() {
        return account.details();
    }

}
