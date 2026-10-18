package com.onlinesanta.terms;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onlinesanta.terms.dto.TermsAcceptRequest;
import com.onlinesanta.terms.dto.TermsStatusView;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/me/terms")
@Tag(name = "參與須知", description = "目前登入者對各份須知的同意狀態")
public class TermsController {

    private final TermsService terms;

    public TermsController(TermsService terms) {
        this.terms = terms;
    }

    @GetMapping
    @Operation(summary = "各份須知的同意狀態",
            description = "upToDate=false 代表沒同意過，或同意的是舊版本，需要重新同意")
    public List<TermsStatusView> mine() {
        return terms.statusOfMine();
    }

    @PostMapping("/{type}/accept")
    @Operation(summary = "同意須知",
            description = "version 必須是目前版本；已同意同一版本則不重複新增")
    public TermsStatusView accept(@PathVariable TermsType type,
                                  @Valid @RequestBody TermsAcceptRequest request) {
        return terms.acceptMine(type, request.version());
    }
}
