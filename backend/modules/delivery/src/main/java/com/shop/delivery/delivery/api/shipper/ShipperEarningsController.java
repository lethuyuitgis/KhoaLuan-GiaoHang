package com.shop.delivery.delivery.api.shipper;

import com.shop.delivery.auth.api.CurrentUser;
import com.shop.delivery.auth.domain.Role;
import com.shop.delivery.auth.entity.TelegramUser;
import com.shop.delivery.auth.service.RoleResolver;
import com.shop.delivery.delivery.api.shipper.dto.DailyEarning;
import com.shop.delivery.delivery.api.shipper.dto.EarningsSummary;
import com.shop.delivery.delivery.api.shipper.dto.LedgerRow;
import com.shop.delivery.delivery.api.shipper.dto.ShipperSelfProfile;
import com.shop.delivery.delivery.api.shipper.dto.ShipperStatusRequest;
import com.shop.delivery.delivery.api.shipper.dto.ShipperStatusResponse;
import com.shop.delivery.delivery.domain.LedgerEntryType;
import com.shop.delivery.delivery.entity.ShipperLedgerEntry;
import com.shop.delivery.delivery.entity.ShipperProfile;
import com.shop.delivery.delivery.repository.ShipperProfileRepository;
import com.shop.delivery.delivery.service.ShipperLedgerService;
import com.shop.delivery.delivery.service.ShipperProfileService;
import com.shop.delivery.shared.exception.AuthenticationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/shipper/me")
public class ShipperEarningsController {

    private final ShipperLedgerService ledger;
    private final ShipperProfileRepository profileRepo;
    private final ShipperProfileService profileService;
    private final RoleResolver roleResolver;

    public ShipperEarningsController(ShipperLedgerService ledger,
                                     ShipperProfileRepository profileRepo,
                                     ShipperProfileService profileService,
                                     RoleResolver roleResolver) {
        this.ledger = ledger;
        this.profileRepo = profileRepo;
        this.profileService = profileService;
        this.roleResolver = roleResolver;
    }

    /** Trạng thái nhận đơn hiện tại (để hiện đúng vị trí nút gạt). */
    @GetMapping("/status")
    public ShipperStatusResponse getStatus(@CurrentUser TelegramUser user) {
        requireShipper(user);
        ShipperProfile p = profileRepo.findById(user.getId())
            .orElseThrow(() -> new AuthenticationException("NOT_SHIPPER", "Bạn không phải shipper"));
        return ShipperStatusResponse.of(p.getCurrentState());
    }

    /** Shipper tự bật/tắt nhận đơn (AVAILABLE ⇄ OFFLINE). Chặn khi đang giao (BUSY). */
    @PostMapping("/status")
    public ShipperStatusResponse setStatus(@CurrentUser TelegramUser user,
                                           @RequestBody ShipperStatusRequest req) {
        requireShipper(user);
        ShipperProfile p = profileService.setAvailability(user.getId(), req.online());
        return ShipperStatusResponse.of(p.getCurrentState());
    }

    @GetMapping("/earnings/summary")
    public EarningsSummary summary(@CurrentUser TelegramUser user) {
        requireShipper(user);
        Long shipperId = user.getId();

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime startToday = now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
        OffsetDateTime startWeek  = startToday.minusDays(6);
        OffsetDateTime startMonth = startToday.minusDays(29);

        BigDecimal today = sumCommission(shipperId, startToday, now);
        BigDecimal week  = sumCommission(shipperId, startWeek,  now);
        BigDecimal month = sumCommission(shipperId, startMonth, now);
        int todayOrders  = countCommissions(shipperId, startToday, now);
        int weekOrders   = countCommissions(shipperId, startWeek,  now);
        BigDecimal balance = ledger.balance(shipperId);

        return new EarningsSummary(today, week, month, balance, todayOrders, weekOrders);
    }

    @GetMapping("/earnings/daily")
    public List<DailyEarning> daily(@CurrentUser TelegramUser user,
                                    @RequestParam OffsetDateTime from,
                                    @RequestParam OffsetDateTime to) {
        requireShipper(user);
        Long shipperId = user.getId();
        return ledger.range(shipperId, from, to).stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION)
            .collect(java.util.stream.Collectors.groupingBy(
                e -> e.getCreatedAt().toLocalDate()))
            .entrySet().stream()
            .map(en -> new DailyEarning(
                en.getKey(),
                en.getValue().size(),
                en.getValue().stream()
                    .map(ShipperLedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)))
            .sorted(java.util.Comparator.comparing(DailyEarning::date))
            .toList();
    }

    @GetMapping("/ledger")
    public Page<LedgerRow> ledger(@CurrentUser TelegramUser user, Pageable pageable) {
        requireShipper(user);
        Long shipperId = user.getId();
        return ledger.page(shipperId, pageable).map(e ->
            new LedgerRow(e.getId(), e.getEntryType(), e.getAmount(),
                          e.getOrderId(), e.getNote(), e.getCreatedAt()));
    }

    @GetMapping("/profile")
    public ShipperSelfProfile profile(@CurrentUser TelegramUser user) {
        requireShipper(user);
        ShipperProfile p = profileRepo.findById(user.getId())
            .orElseThrow(() -> new AuthenticationException("NOT_SHIPPER", "Bạn không phải shipper"));
        String name = trim(user.getFirstName(), "") + " " + trim(user.getLastName(), "");
        return new ShipperSelfProfile(
            name.trim(),
            user.getPhone() != null ? user.getPhone() : "",
            p.getRatingAvg(),
            p.getTotalDeliveries(),
            user.getCreatedAt()         // Instant from BaseEntity
        );
    }

    // --- helpers ---

    private void requireShipper(TelegramUser user) {
        if (!roleResolver.hasRole(user.getId(), Role.SHIPPER)) {
            throw new AuthenticationException("NOT_SHIPPER", "Bạn không phải shipper");
        }
    }

    private BigDecimal sumCommission(Long shipperId, OffsetDateTime from, OffsetDateTime to) {
        return ledger.range(shipperId, from, to).stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION)
            .map(ShipperLedgerEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int countCommissions(Long shipperId, OffsetDateTime from, OffsetDateTime to) {
        return (int) ledger.range(shipperId, from, to).stream()
            .filter(e -> e.getEntryType() == LedgerEntryType.COMMISSION)
            .count();
    }

    private static String trim(String s, String fallback) {
        return s != null ? s : fallback;
    }
}
