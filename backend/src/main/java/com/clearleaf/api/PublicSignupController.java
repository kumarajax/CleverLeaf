package com.clearleaf.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
public class PublicSignupController {
    private final SignupApprovalService signupApprovalService;

    public PublicSignupController(SignupApprovalService signupApprovalService) {
        this.signupApprovalService = signupApprovalService;
    }

    @PostMapping("/signup-requests")
    public SignupRequestStatusRecord submit(@RequestBody SignupRequestSubmission submission, HttpServletRequest request) {
        return signupApprovalService.submit(submission, clientIp(request), request.getHeader("User-Agent"));
    }

    @GetMapping("/signup-approvals/{token}")
    public SignupRequestStatusRecord tokenInfo(@PathVariable("token") String token) {
        return signupApprovalService.tokenInfo(token);
    }

    @PostMapping("/signup-approvals/{token}/approve")
    public SignupRequestStatusRecord approve(@PathVariable("token") String token) {
        return signupApprovalService.approve(token);
    }

    @PostMapping("/signup-approvals/{token}/reject")
    public SignupRequestStatusRecord reject(@PathVariable("token") String token, @RequestBody(required = false) RejectSignupRequest request) {
        return signupApprovalService.reject(token, request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        return forwardedFor == null || forwardedFor.isBlank()
                ? request.getRemoteAddr()
                : forwardedFor.split(",")[0].trim();
    }
}
