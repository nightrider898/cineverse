package com.cineverse.payment;

import com.cineverse.dto.PaymentDtos.PaymentMethodOption;
import com.cineverse.entity.enums.PaymentMethod;
import com.cineverse.entity.enums.PaymentProviderType;
import com.cineverse.exception.ApiException;
import com.cineverse.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Chooses which configured gateway processes each customer-facing payment method.
 * Google Pay and PhonePe are UPI apps: they are routed through a UPI-capable gateway, not a fake standalone API.
 */
@Component
public class PaymentRouter {
    private final Map<PaymentProviderType, PaymentProvider> providers = new EnumMap<>(PaymentProviderType.class);

    private static final Map<PaymentMethod, List<PaymentProviderType>> PREFERENCE = Map.of(
            PaymentMethod.CARD, List.of(PaymentProviderType.STRIPE, PaymentProviderType.RAZORPAY, PaymentProviderType.PHONEPE, PaymentProviderType.MOCK),
            PaymentMethod.UPI, List.of(PaymentProviderType.RAZORPAY, PaymentProviderType.PHONEPE, PaymentProviderType.MOCK),
            PaymentMethod.GPAY, List.of(PaymentProviderType.RAZORPAY, PaymentProviderType.MOCK),
            PaymentMethod.PHONEPE, List.of(PaymentProviderType.PHONEPE, PaymentProviderType.RAZORPAY, PaymentProviderType.MOCK),
            PaymentMethod.NETBANKING, List.of(PaymentProviderType.RAZORPAY, PaymentProviderType.PHONEPE, PaymentProviderType.MOCK),
            PaymentMethod.WALLET, List.of(PaymentProviderType.RAZORPAY, PaymentProviderType.MOCK));

    private static final Map<PaymentMethod, String[]> LABELS = new LinkedHashMap<>();
    static {
        LABELS.put(PaymentMethod.UPI, new String[]{"UPI", "Any UPI app — pay with your UPI ID or QR"});
        LABELS.put(PaymentMethod.GPAY, new String[]{"Google Pay (UPI)", "Pay with Google Pay through UPI"});
        LABELS.put(PaymentMethod.PHONEPE, new String[]{"PhonePe", "Pay with the PhonePe app"});
        LABELS.put(PaymentMethod.CARD, new String[]{"Credit / Debit Card", "Visa, Mastercard, RuPay, American Express where supported"});
        LABELS.put(PaymentMethod.NETBANKING, new String[]{"Net Banking", "All major Indian banks"});
        LABELS.put(PaymentMethod.WALLET, new String[]{"Wallets", "Supported mobile wallets"});
        LABELS.put(PaymentMethod.PAY_AT_VENUE, new String[]{"Pay at Venue", "Reserve now, pay at the box office counter"});
    }

    public PaymentRouter(List<PaymentProvider> all) {
        all.forEach(p -> providers.put(p.type(), p));
    }

    public PaymentProvider resolve(PaymentMethod method) {
        for (PaymentProviderType type : PREFERENCE.getOrDefault(method, List.of())) {
            PaymentProvider p = providers.get(type);
            if (p != null && p.isEnabled() && p.supportedMethods().contains(method)) return p;
        }
        throw new ApiException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE, method + " payments are not configured on this server");
    }

    public PaymentProvider provider(PaymentProviderType type) {
        PaymentProvider p = providers.get(type);
        if (p == null) throw new ApiException(ErrorCode.PAYMENT_PROVIDER_UNAVAILABLE);
        return p;
    }

    public List<PaymentMethodOption> options(boolean payAtVenueAllowed) {
        List<PaymentMethodOption> out = new ArrayList<>();
        LABELS.forEach((method, label) -> {
            if (method == PaymentMethod.PAY_AT_VENUE) {
                out.add(new PaymentMethodOption(method.name(), label[0], label[1], "VENUE", payAtVenueAllowed));
                return;
            }
            String provider = null;
            try { provider = resolve(method).type().name(); } catch (ApiException ignored) { }
            out.add(new PaymentMethodOption(method.name(), label[0], label[1], provider, provider != null));
        });
        return out;
    }

    public boolean isSandbox() {
        List<PaymentProvider> enabled = providers.values().stream().filter(PaymentProvider::isEnabled).toList();
        return !enabled.isEmpty() && enabled.stream().allMatch(p -> p.type() == PaymentProviderType.MOCK);
    }
}
