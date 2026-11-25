package com.zelvira.zelvira.controller;

import com.zelvira.zelvira.dto.ApiResponse;
import com.zelvira.zelvira.dto.TokenResponse;
import com.zelvira.zelvira.dto.UserProfileResponse;
import com.zelvira.zelvira.dto.LoginRequest;
import com.zelvira.zelvira.dto.OtpLoginRequest;
import com.zelvira.zelvira.dto.OtpRequest;
import com.zelvira.zelvira.dto.RegisterRequest;
import com.zelvira.zelvira.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping({ "/api/v1/auth", "/auth" })
@Tag(name = "Authentication", description = "API untuk Otentikasi, Login, Register, dan Profil Pengguna")
public class AuthController {

    private final AuthService authService;
    private final FileStorageService fileStorageService;

    public AuthController(AuthService authService, FileStorageService fileStorageService) {
        this.authService = authService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/register")
    @Operation(summary = "Daftar Akun Baru", description = "Mendaftarkan pengguna baru dengan email dan password. Mengembalikan Token murni.")
    public ResponseEntity<ApiResponse<TokenResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return new ResponseEntity<>(ApiResponse.success("User registered successfully", authService.register(request)),
                HttpStatus.CREATED);
    }

    @PostMapping("/login")
    @Operation(summary = "Login Email", description = "Login menggunakan email dan password. Mengembalikan Token murni.")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful", authService.login(request)));
    }

    @GetMapping("/google/login")
    @Operation(summary = "Redirect ke Google OAuth", description = "Mengarahkan browser pengguna ke halaman persetujuan login Google.")
    public ResponseEntity<Void> redirectToGoogle(@RequestParam(value = "state", required = false) String state) {
        String authUrl = authService.getGoogleAuthorizationUrl(state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .build();
    }

    @GetMapping("/google/callback")
    @Operation(summary = "Callback Google OAuth 2.0", description = "Menerima authorization code dari Google, menukarnya dengan token, dan me-redirect ke frontend/mobile.")
    public ResponseEntity<Void> handleGoogleCallback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "error", required = false) String error) {
        String redirectUrl = authService.handleGoogleCallback(code, state, error);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    @PostMapping("/google")
    @Operation(summary = "Login dengan Google (Direct ID Token)", description = "Login menggunakan ID Token dari Google (misalnya dari Flutter Native SDK). Mengembalikan Token murni.")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithGoogle(
            @Valid @RequestBody com.zelvira.zelvira.dto.GoogleLoginRequest request) {
        return ResponseEntity
                .ok(ApiResponse.success("Google Login successful", authService.loginWithGoogle(request.getIdToken())));
    }

    @GetMapping("/me")
    @Operation(summary = "Ambil Profil Pengguna", description = "Mengambil detail profil pengguna yang sedang login berdasarkan token.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMe(@AuthenticationPrincipal Object principal) {
        if (principal instanceof CustomUserDetails) {
            String email = ((CustomUserDetails) principal).getUsername();
            return ResponseEntity.ok(ApiResponse.success("User profile fetched successfully",
                    authService.getMe(email)));
        } else {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "Token is missing or invalid"));
        }
    }

    @PutMapping("/me")
    @Operation(summary = "Update Profil Pengguna", description = "Mengubah detail profil pengguna.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @AuthenticationPrincipal Object principal,
            @RequestBody com.zelvira.zelvira.dto.ProfileUpdateRequest request) {
        if (principal instanceof CustomUserDetails) {
            String email = ((CustomUserDetails) principal).getUsername();
            return ResponseEntity.ok(ApiResponse.success("Profile updated successfully",
                    authService.updateProfile(email, request)));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PutMapping("/me/password")
    @Operation(summary = "Ubah Password", description = "Mengubah password akun pengguna yang sedang login.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody com.zelvira.zelvira.dto.ChangePasswordRequest request) {
        if (principal instanceof CustomUserDetails) {
            String email = ((CustomUserDetails) principal).getUsername();
            authService.changePassword(email, request.getCurrentPassword(), request.getNewPassword());
            return ResponseEntity.ok(ApiResponse.success("Password berhasil diperbarui.", null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/me/picture")
    @Operation(summary = "Update Foto Profil", description = "Mengunggah dan mengubah foto profil pengguna.")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfilePicture(
            @AuthenticationPrincipal Object principal,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (principal instanceof CustomUserDetails) {
            String email = ((CustomUserDetails) principal).getUsername();
            String imageUrl = fileStorageService.store(file);
            return ResponseEntity.ok(ApiResponse.success("Profile picture updated successfully",
                    authService.updateProfilePicture(email, imageUrl)));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/me/fcm-token")
    @Operation(summary = "Update FCM Token", description = "Memperbarui Firebase Cloud Messaging Token untuk push notification.")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @AuthenticationPrincipal Object principal,
            @RequestBody java.util.Map<String, String> payload) {
        if (principal instanceof CustomUserDetails) {
            String email = ((CustomUserDetails) principal).getUsername();
            String fcmToken = payload.get("fcmToken");
            authService.updateFcmToken(email, fcmToken);
            return ResponseEntity.ok(ApiResponse.success("FCM token updated successfully", null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @PostMapping("/otp/request")
    @Operation(summary = "Minta Kode OTP", description = "Meminta pengiriman kode OTP login ke WhatsApp pengguna.")
    public ResponseEntity<ApiResponse<Void>> requestOtp(@Valid @RequestBody OtpRequest request) {
        authService.requestOtp(request.getPhoneNumber());
        return ResponseEntity.ok(ApiResponse.success("OTP sent successfully", null));
    }

    @PostMapping("/otp/login")
    @Operation(summary = "Login dengan OTP", description = "Login menggunakan nomor telepon dan kode OTP WhatsApp. Mengembalikan Token murni.")
    public ResponseEntity<ApiResponse<TokenResponse>> loginWithOtp(@Valid @RequestBody OtpLoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Login successful",
                authService.loginWithOtp(request.getPhoneNumber(), request.getCode())));
    }

    @PostMapping("/forgot-password/request")
    @Operation(summary = "Lupa Password - Minta OTP", description = "Mengirim kode OTP 6-digit untuk reset password ke email terdaftar.")
    public ResponseEntity<ApiResponse<Void>> forgotPasswordRequest(
            @Valid @RequestBody com.zelvira.zelvira.dto.ForgotPasswordRequest request) {
        authService.requestForgotPasswordOtp(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("Kode OTP reset password telah dikirim ke email Anda.", null));
    }

    @PostMapping("/forgot-password/verify")
    @Operation(summary = "Lupa Password - Verifikasi OTP", description = "Memverifikasi kode OTP 6-digit yang dikirim ke email.")
    public ResponseEntity<ApiResponse<Void>> forgotPasswordVerify(
            @Valid @RequestBody com.zelvira.zelvira.dto.ForgotPasswordVerifyRequest request) {
        authService.verifyForgotPasswordOtp(request.getEmail(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("Kode OTP valid.", null));
    }

    @PostMapping("/forgot-password/reset")
    @Operation(summary = "Lupa Password - Buat Password Baru", description = "Mengatur password baru setelah verifikasi OTP email berhasil.")
    public ResponseEntity<ApiResponse<Void>> forgotPasswordReset(
            @Valid @RequestBody com.zelvira.zelvira.dto.ResetPasswordRequest request) {
        authService.resetPasswordWithOtp(request.getEmail(), request.getCode(), request.getNewPassword());
        return ResponseEntity
                .ok(ApiResponse.success("Password berhasil diubah. Silakan login kembali dengan password baru.", null));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Hapus / Deaktivasi Akun", description = "Mengajukan penghapusan akun. Data pribadi disamarkan (anonymized) menjadi dummy dan akun dinonaktifkan.")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(@AuthenticationPrincipal Object principal) {
        if (principal instanceof CustomUserDetails) {
            String email = ((CustomUserDetails) principal).getUsername();
            authService.deleteAndAnonymizeAccount(email);
            return ResponseEntity.ok(ApiResponse.success("Akun berhasil dihapus dan dinonaktifkan.", null));
        }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}
