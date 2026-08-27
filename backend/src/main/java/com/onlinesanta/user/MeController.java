package com.onlinesanta.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.user.dto.CurrentUserView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/me")
@Tag(name = "帳號", description = "目前登入者的身分")
public class MeController {

    private final CurrentUserService currentUser;

    public MeController(CurrentUserService currentUser) {
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "取得自己的身分與角色",
            description = "首次呼叫會依 Firebase ID token 就地建立本地帳號")
    public CurrentUserView me() {
        return CurrentUserView.from(currentUser.require());
    }
}
