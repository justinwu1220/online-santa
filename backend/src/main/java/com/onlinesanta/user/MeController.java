package com.onlinesanta.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.auth.CurrentUserService;
import com.onlinesanta.user.dto.CurrentUserView;
import com.onlinesanta.user.dto.UserProfileUpdateRequest;
import com.onlinesanta.user.dto.UserProfileView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me")
@Tag(name = "帳號", description = "目前登入者的身分")
public class MeController {

    private final CurrentUserService currentUser;
    private final UserProfileService profile;

    public MeController(CurrentUserService currentUser, UserProfileService profile) {
        this.currentUser = currentUser;
        this.profile = profile;
    }

    @GetMapping
    @Operation(summary = "取得自己的身分與角色",
            description = "首次呼叫會依 Firebase ID token 就地建立本地帳號")
    public CurrentUserView me() {
        return CurrentUserView.from(currentUser.require());
    }

    @GetMapping("/profile")
    @Operation(summary = "取得自己的個人檔案")
    public UserProfileView getProfile() {
        return profile.getMine();
    }

    @PatchMapping("/profile")
    @Operation(summary = "更新自己的個人檔案")
    public UserProfileView updateProfile(@Valid @RequestBody UserProfileUpdateRequest request) {
        return profile.updateMine(request);
    }
}
