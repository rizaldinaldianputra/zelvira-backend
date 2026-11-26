package com.zelvira.zelvira.service;

import com.zelvira.zelvira.dto.TokenResponse;
import com.zelvira.zelvira.dto.UserProfileResponse;
import com.zelvira.zelvira.dto.LoginRequest;
import com.zelvira.zelvira.dto.RegisterRequest;
import com.zelvira.zelvira.entity.Role;
import com.zelvira.zelvira.entity.User;
import com.zelvira.zelvira.repository.RoleRepository;
import com.zelvira.zelvira.repository.UserRepository;
import com.zelvira.zelvira.security.CustomUserDetails;
import com.zelvira.zelvira.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final OtpService otpService;
    private final EmailService emailService;
    private final SystemRoleService systemRoleService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.google.client-id:}")
    private String googleClientId;

    @Value("${app.google.client-secret:}")
    private String googleClientSecret;

    @Value("${app.google.redirect-uri:https://api.zelvira.co.id/api/v1/auth/google/callback}")
    private String googleRedirectUri;

    @Value("${app.google.frontend-url:https://zelvira.co.id}")
    private String frontendUrl;

    public AuthService(UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtUtil jwtUtil,
            OtpService otpService,
            EmailService emailService,
            SystemRoleService systemRoleService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.otpService = otpService;
        this.emailService = emailService;
        this.systemRoleService = systemRoleService;
    }

    @Transactional
    public void requestForgotPasswordOtp(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email tidak boleh kosong.");
        }
        String cleanEmail = email.trim().toLowerCase();
        userRepository.findByEmail(cleanEmail)
                .orElseThrow(
                        () -> new IllegalArgumentException("Akun dengan email '" + cleanEmail + "' tidak ditemukan."));

        String code = otpService.generateEmailOtp(cleanEmail);
        try {
            emailService.sendOtpEmail(cleanEmail, code);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .error("Failed to send OTP email to {}: {}", cleanEmail, e.getMessage());
            throw new RuntimeException("Gagal mengirim email OTP: " + e.getMessage());
        }
    }

    public void verifyForgotPasswordOtp(String email, String code) {
        if (email == null || code == null) {
            throw new IllegalArgumentException("Email dan kode OTP wajib diisi.");
        }
        String cleanEmail = email.trim().toLowerCase();
        if (!otpService.verifyEmailOtp(cleanEmail, code)) {
            throw new IllegalArgumentException("Kode OTP salah atau sudah kedaluwarsa.");
        }
    }

    @Transactional
    public void resetPasswordWithOtp(String email, String code, String newPassword) {
        if (email == null || newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Email dan password baru wajib diisi.");
        }
        String cleanEmail = email.trim().toLowerCase();

        boolean verified = (code != null && otpService.verifyEmailOtp(cleanEmail, code))
                || otpService.isEmailOtpVerified(cleanEmail);

        if (!verified) {
            throw new IllegalArgumentException("Verifikasi OTP tidak valid atau sudah kedaluwarsa.");
        }

        User user = userRepository.findByEmail(cleanEmail)
                .orElseThrow(() -> new IllegalArgumentException("Pengguna tidak ditemukan."));

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);

        otpService.clearEmailOtpVerification(cleanEmail);
    }

    @Transactional
    public void requestOtp(String phoneNumber) {
        otpService.generateOtp(phoneNumber);
    }

    @Transactional
    public TokenResponse loginWithOtp(String phoneNumber, String code) {
        if (!otpService.verifyOtp(phoneNumber, code)) {
            throw new IllegalArgumentException("Invalid or expired OTP code.");
        }

        String normalized = OtpService.normalizePhone(phoneNumber);
        String withPlus = "+" + normalized;
        String localFormat = normalized.startsWith("62") ? "0" + normalized.substring(2) : normalized;
        String fallbackEmail = normalized + "@zelvira.com";

        // Check if user already exists by any phone number variation or fallback email
        User user = userRepository.findByPhoneNumber(normalized)
                .orElseGet(() -> userRepository.findByPhoneNumber(withPlus)
                        .orElseGet(() -> userRepository.findByPhoneNumber(localFormat)
                                .orElseGet(() -> userRepository.findByPhoneNumber(phoneNumber)
                                        .orElseGet(() -> userRepository.findByEmail(fallbackEmail).orElse(null)))));

        if (user == null) {
            // Auto-register new user via OTP
            Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
                Role newRole = new Role();
                newRole.setName("ROLE_USER");
                return roleRepository.save(newRole);
            });

            String shortDigits = normalized.length() >= 4 ? normalized.substring(normalized.length() - 4) : normalized;
            User newUser = User.builder()
                    .phoneNumber(normalized)
                    .fullName("User " + shortDigits)
                    .email(fallbackEmail) // Fallback email
                    .password("") // OTP users don't have passwords
                    .roles(Collections.singleton(userRole))
                    .isActive(true)
                    .build();
            user = userRepository.save(newUser);
        } else {
            if (Boolean.FALSE.equals(user.getIsActive())) {
                throw new IllegalArgumentException("Akun ini telah dinonaktifkan / dihapus.");
            }
            // Sync phone number format if needed
            if (user.getPhoneNumber() == null || !user.getPhoneNumber().equals(normalized)) {
                user.setPhoneNumber(normalized);
                user = userRepository.save(user);
            }
        }

        // Generate Token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtUtil.generateToken(userDetails);

        return new TokenResponse(token);
    }

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered.");
        }

        // Fetch or create default USER role
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName("ROLE_USER");
            return roleRepository.save(newRole);
        });

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(Collections.singleton(userRole))
                .build();

        userRepository.save(user);

        // Generate Token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtUtil.generateToken(userDetails);

        return new TokenResponse(token);
    }

    public TokenResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        // Generate Token
        CustomUserDetails userDetails = new CustomUserDetails(user);
        String token = jwtUtil.generateToken(userDetails);

        return new TokenResponse(token);
    }

    public UserProfileResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        return mapToUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, com.zelvira.zelvira.dto.ProfileUpdateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber());
        }

        return mapToUserProfileResponse(userRepository.save(user));
    }

    @Transactional
    public UserProfileResponse updateProfilePicture(String email, String imageUrl) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        user.setProfilePicture(imageUrl);
        return mapToUserProfileResponse(userRepository.save(user));
    }

    @Transactional
    public void updateFcmToken(String email, String fcmToken) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        user.setFcmToken(fcmToken);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Pengguna tidak ditemukan: " + email));

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
                throw new IllegalArgumentException("Password saat ini tidak sesuai.");
            }
        }

        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new IllegalArgumentException("Password baru minimal 6 karakter.");
        }

        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);
    }

    private UserProfileResponse mapToUserProfileResponse(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toList());

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .profilePicture(user.getProfilePicture())
                .phoneNumber(user.getPhoneNumber())
                .balance(user.getBalance() != null ? user.getBalance() : 0.0)
                .points(user.getPoints() != null ? user.getPoints() : 0L)
                .roles(roleNames)
                .systemRole(user.getSystemRole() != null ? systemRoleService.mapToResponse(user.getSystemRole()) : null)
                .build();
    }

    @Transactional
    public TokenResponse loginWithGoogle(String idTokenString) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(),
                    new GsonFactory())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                String pictureUrl = (String) payload.get("picture");

                User user = userRepository.findByEmail(email).orElse(null);
                if (user == null) {
                    Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
                        Role newRole = new Role();
                        newRole.setName("ROLE_USER");
                        return roleRepository.save(newRole);
                    });

                    User newUser = User.builder()
                            .email(email)
                            .password("") // Google OAuth users don't have passwords
                            .fullName(name)
                            .profilePicture(pictureUrl)
                            .roles(Collections.singleton(userRole))
                            .isActive(true)
                            .build();
                    user = userRepository.save(newUser);
                } else {
                    boolean updated = false;
                    if ((user.getFullName() == null || user.getFullName().trim().isEmpty()) && name != null) {
                        user.setFullName(name);
                        updated = true;
                    }
                    if ((user.getProfilePicture() == null || user.getProfilePicture().trim().isEmpty())
                            && pictureUrl != null) {
                        user.setProfilePicture(pictureUrl);
                        updated = true;
                    }
                    if (updated) {
                        user = userRepository.save(user);
                    }
                }

                CustomUserDetails userDetails = new CustomUserDetails(user);
                String token = jwtUtil.generateToken(userDetails);
                return new TokenResponse(token);
            } else {
                throw new IllegalArgumentException("Invalid Google ID token.");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify Google token", e);
        }
    }

    @Transactional
    public void deleteAndAnonymizeAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        // Generate dummy identifiers to preserve FK relations while preventing future
        // logins
        String randomSuffix = UUID.randomUUID().toString().substring(0, 8);
        user.setIsActive(false);
        user.setEmail("deleted_" + user.getId() + "_" + randomSuffix + "@deleted.zelvira.com");
        user.setPhoneNumber("deleted_" + user.getId() + "_" + randomSuffix);
        user.setFullName("Pengguna Dinonaktifkan");
        user.setProfilePicture(null);
        user.setFcmToken(null);
        // Scramble password
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

        userRepository.save(user);
    }

    public String getGoogleAuthorizationUrl(String state) {
        String cleanState = (state != null && !state.isBlank()) ? state : "web";
        return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", googleClientId)
                .queryParam("redirect_uri", googleRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "select_account")
                .queryParam("state", cleanState)
                .build()
                .toUriString();
    }

    @Transactional
    public String handleGoogleCallback(String code, String state, String error) {
        String defaultFrontend = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl
                : "https://zelvira.co.id";
        if (defaultFrontend.endsWith("/")) {
            defaultFrontend = defaultFrontend.substring(0, defaultFrontend.length() - 1);
        }

        boolean isMobile = (state != null && state.toLowerCase().startsWith("mobile"));

        // 1. Error returned from Google
        if (error != null && !error.isBlank()) {
            log.warn("Google OAuth callback error received: {}", error);
            String encodedError = URLEncoder.encode(error, StandardCharsets.UTF_8);
            if (isMobile) {
                return "zelvira://auth/callback?error=" + encodedError;
            }
            return defaultFrontend + "/login?error=" + encodedError;
        }

        // 2. Authorization code missing
        if (code == null || code.isBlank()) {
            log.warn("Google OAuth callback called without authorization code");
            String encodedError = URLEncoder.encode("code_missing", StandardCharsets.UTF_8);
            if (isMobile) {
                return "zelvira://auth/callback?error=" + encodedError;
            }
            return defaultFrontend + "/login?error=" + encodedError;
        }

        // 3. Client secret not configured
        if (googleClientSecret == null || googleClientSecret.isBlank()) {
            log.error("Google Client Secret (GOOGLE_CLIENT_SECRET) is not configured in backend environment.");
            String encodedError = URLEncoder.encode("client_secret_not_configured", StandardCharsets.UTF_8);
            if (isMobile) {
                return "zelvira://auth/callback?error=" + encodedError;
            }
            return defaultFrontend + "/login?error=" + encodedError;
        }

        // 4. Exchange authorization code for token from Google
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", code);
            params.add("client_id", googleClientId);
            params.add("client_secret", googleClientSecret);
            params.add("redirect_uri", googleRedirectUri);
            params.add("grant_type", "authorization_code");

            HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://oauth2.googleapis.com/token",
                    entity,
                    Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalStateException(
                        "Google token exchange failed with HTTP status: " + response.getStatusCode());
            }

            Map<String, Object> body = response.getBody();
            String idToken = (String) body.get("id_token");
            if (idToken == null || idToken.isBlank()) {
                throw new IllegalStateException("id_token was not returned in Google token response");
            }

            // 5. Login or register user with idToken
            TokenResponse tokenResponse = loginWithGoogle(idToken);
            String jwtToken = tokenResponse.getToken();

            // 6. Return appropriate redirect URL
            String encodedToken = URLEncoder.encode(jwtToken, StandardCharsets.UTF_8);
            if (isMobile) {
                return "zelvira://auth/callback?token=" + encodedToken;
            }

            String redirectParam = "";
            if (state != null && state.startsWith("web:") && state.length() > 4) {
                String targetPath = state.substring(4);
                if (targetPath.startsWith("/")) {
                    redirectParam = "&redirect=" + URLEncoder.encode(targetPath, StandardCharsets.UTF_8);
                }
            }

            return defaultFrontend + "/login/success?token=" + encodedToken + redirectParam;

        } catch (Exception e) {
            log.error("Failed to complete Google OAuth code exchange: {}", e.getMessage(), e);
            String encodedError = URLEncoder.encode("google_exchange_failed: " + e.getMessage(),
                    StandardCharsets.UTF_8);
            if (isMobile) {
                return "zelvira://auth/callback?error=" + encodedError;
            }
            return defaultFrontend + "/login?error=" + encodedError;
        }
    }
}
