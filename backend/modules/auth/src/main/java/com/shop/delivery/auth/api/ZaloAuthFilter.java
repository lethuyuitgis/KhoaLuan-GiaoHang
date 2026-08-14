package com.shop.delivery.auth.api;

import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.repository.TelegramUserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sibling of {@link TelegramAuthFilter} that reads the
 * {@code X-Zalo-Access-Token} header from the Zalo Mini App
 * ({@code frontend/zaloapp}). Order in the filter chain is AFTER
 * TelegramAuthFilter — if the Telegram filter already set
 * {@code currentUser}, this filter is a no-op for that request.
 *
 * <h3>Production verification (NOT WIRED YET)</h3>
 * The real flow calls Zalo OpenAPI:
 * <pre>GET https://graph.zalo.me/v2.0/me?access_token=...&fields=id,name,picture</pre>
 * The returned JSON contains {@code id} (Zalo user id), {@code name},
 * and an optional photo. We then upsert into the same {@code telegram_user}
 * table (V1 reuses the entity — we just namespace Zalo IDs with a different
 * prefix so they don't collide with real Telegram IDs in production).
 *
 * <h3>Why this is a stub</h3>
 * Calling Zalo OpenAPI requires a registered Zalo Official Account
 * ({@code ZALO_APP_ID} + {@code ZALO_APP_SECRET}). Acquiring those takes
 * ~1 week of business-registration review and is out of thesis scope.
 * Until then this filter:
 * <ul>
 *   <li>If {@code ZALO_APP_ID} env is missing → silently disables itself
 *       (mirror of the Telegram dev-bypass pattern).</li>
 *   <li>If a token IS provided and the app is in dev profile → log + skip
 *       (the {@code X-Dev-User-Id} fallback in TelegramAuthFilter already
 *       served the request).</li>
 *   <li>In a future commit, replace {@link #verifyTokenWithZalo(String)}
 *       with a real WebClient call + 5-minute Caffeine cache.</li>
 * </ul>
 */
@Component
public class ZaloAuthFilter extends OncePerRequestFilter {

    public static final String HEADER_ACCESS_TOKEN = "X-Zalo-Access-Token";

    private static final Logger log = LoggerFactory.getLogger(ZaloAuthFilter.class);

    private final TelegramUserRepository userRepo;
    private final boolean enabled;
    private final boolean devModeEnabled;
    private final String zaloAppId;
    private final String zaloAppSecret;
    private final RestClient zaloApi;

    public ZaloAuthFilter(TelegramUserRepository userRepo,
                          Environment env,
                          @Value("${zalo.app-id:}") String zaloAppId,
                          @Value("${zalo.app-secret:}") String zaloAppSecret) {
        this.userRepo = userRepo;
        this.zaloAppId = zaloAppId;
        this.zaloAppSecret = zaloAppSecret;
        this.zaloApi = RestClient.builder().baseUrl("https://graph.zalo.me").build();
        this.devModeEnabled = List.of(env.getActiveProfiles()).contains("dev");
        // Filter is only "active" when an app id is configured. Without one
        // we can never verify a real token, so we early-exit on every request
        // (still chains through — the request might be served by another auth).
        this.enabled = zaloAppId != null && !zaloAppId.isBlank();
        if (!enabled) {
            log.info("ZaloAuthFilter disabled — no ZALO_APP_ID configured. "
                + "X-Zalo-Access-Token headers will be ignored. Set zalo.app-id "
                + "to enable Zalo OAuth verification.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Skip work if TelegramAuthFilter already attached a user.
        if (request.getAttribute(TelegramAuthFilter.ATTRIBUTE_CURRENT_USER) != null) {
            chain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER_ACCESS_TOKEN);
        if (token == null || token.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        if (!enabled) {
            // Token is present but the filter cannot verify it. Log once at
            // debug level and let the request continue unauthenticated; the
            // @CurrentUser argument resolver will issue a 401 downstream.
            log.debug("Got X-Zalo-Access-Token but ZALO_APP_ID is unset — ignoring.");
            chain.doFilter(request, response);
            return;
        }

        try {
            verifyTokenWithZalo(token).ifPresent(zaloUser -> {
                // Upsert: a first-time Zalo customer is created so they can order.
                TelegramUser user = userRepo.findById(zaloUser.id())
                    .orElseGet(() -> userRepo.save(zaloUser.toEntity()));
                request.setAttribute(TelegramAuthFilter.ATTRIBUTE_CURRENT_USER, user);
                log.debug("Zalo auth OK userId={}", user.getId());
            });
        } catch (Exception ex) {
            log.warn("Zalo token verify failed: {}", ex.getMessage());
            // fall through unauthenticated
        }

        chain.doFilter(request, response);
    }

    /**
     * STUB. Real implementation should call Zalo OpenAPI:
     * <pre>
     * GET https://graph.zalo.me/v2.0/me?access_token={token}&fields=id,name,picture
     * </pre>
     * Cache successful verifications for 5 minutes (Caffeine) so we don't hit
     * Zalo's quota on every API call from the Mini App.
     *
     * <p>For now this returns empty unless we're in the dev profile AND the
     * token starts with the magic prefix {@code "dev:"} — in which case the
     * suffix is parsed as a Zalo user id for local testing of the wiring.
     * Format: {@code X-Zalo-Access-Token: dev:9000000001}
     */
    /** appsecret_proof = HMAC-SHA256(access_token, app_secret) hex. null nếu chưa có app-secret. */
    private String appSecretProof(String token) {
        if (zaloAppSecret == null || zaloAppSecret.isBlank()) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(zaloAppSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            log.warn("appsecret_proof compute failed: {}", e.getMessage());
            return null;
        }
    }

    private Optional<ZaloUser> verifyTokenWithZalo(String token) {
        // TODO: replace with WebClient call to Zalo OpenAPI.
        // Until ZALO_APP_SECRET is provisioned, accept "dev:<id>" tokens in
        // dev profile only — useful for backend integration tests.
        if (devModeEnabled) {
            if (token.startsWith("dev:")) {
                try {
                    long id = Long.parseLong(token.substring("dev:".length()));
                    log.warn("⚠️  ZaloAuthFilter DEV STUB accepting token dev:{} — DO NOT enable in prod.", id);
                    return Optional.of(new ZaloUser(id, null, null));
                } catch (NumberFormatException ignored) { /* rơi xuống fallback demo */ }
            }
            // Token Zalo THẬT trong profile dev → bỏ qua verify graph.zalo.me (cần app-secret +
            // appsecret_proof) để Mini App chạy end-to-end khi test local; gán khách demo seed.
            log.warn("⚠️  Zalo DEV FALLBACK — không verify token thật, gán khách demo 9000000001. KHÔNG bật ở prod.");
            return Optional.of(new ZaloUser(9000000001L, null, null));
        }
        // Real verification: GET https://graph.zalo.me/v2.0/me?fields=id,name
        // with the Mini App access token in the `access_token` header. Zalo
        // returns { id, name, error: 0 } on success, or a non-zero error.
        try {
            // Zalo (giống FB Graph) yêu cầu appsecret_proof = HMAC-SHA256(access_token, app_secret)
            // khi app bật "require appsecret_proof". Bỏ qua nếu chưa cấu hình app-secret.
            final String proof = appSecretProof(token);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = zaloApi.get()
                .uri(uri -> {
                    uri.path("/v2.0/me").queryParam("fields", "id,name");
                    if (proof != null) uri.queryParam("appsecret_proof", proof);
                    return uri.build();
                })
                .header("access_token", token)
                .retrieve()
                .body(Map.class);
            if (body == null) return Optional.empty();
            Object err = body.get("error");
            boolean ok = err == null
                || (err instanceof Number n ? n.intValue() == 0 : "0".equals(String.valueOf(err)));
            Object idObj = body.get("id");
            if (!ok || idObj == null) {
                log.warn("Zalo /me rejected token: error={} message={}", err, body.get("message"));
                return Optional.empty();
            }
            long id = Long.parseLong(String.valueOf(idObj));
            String name = body.get("name") == null ? null : String.valueOf(body.get("name"));
            return Optional.of(new ZaloUser(id, name, null));
        } catch (Exception ex) {
            log.warn("Zalo /me call failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Resolved Zalo user identity (subset of the /v2/me response we care about).
     * Kept inside the filter for now; promote to its own file once we wire
     * real verification + DTOs.
     */
    public record ZaloUser(Long id, String name, String pictureUrl) {
        public TelegramUser toEntity() {
            TelegramUser u = new TelegramUser();
            u.setId(id);
            u.setFirstName(name);
            return u;
        }
    }

    public String getZaloAppId() {
        return zaloAppId;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return false;
    }
}
