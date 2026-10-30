package com.cineverse.notification;

import com.cineverse.config.AppProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders branded HTML emails (plus a plain-text alternative) from classpath templates:
 * {@code email/templates/layout.html} wraps a per-email content template. {@code {{name}}} values are HTML-escaped,
 * {@code {{{name}}}} values are inserted raw and must be built from escaped parts by the caller.
 * Images (logo, banners, QR code) are embedded inline and referenced as {@code cid:...}, so they display without
 * any public image hosting.
 */
@Component
public class EmailRenderer {
    private static final Pattern RAW = Pattern.compile("\\{\\{\\{(\\w+)}}}");
    private static final Pattern VAR = Pattern.compile("\\{\\{(\\w+)}}");

    public record Rendered(String subject, String html, String text, Map<String, byte[]> inlineImages) {}

    /** Which content template, banner and preheader each email uses. */
    private record Spec(String contentTemplate, String banner, String bannerAlt, Function<Map<String, Object>, String> preheader) {}

    private static final Map<String, Spec> SPECS = Map.of(
            "welcome", new Spec("welcome", "banner-welcome", "Welcome aboard", m -> "Thanks for registering. Your account is ready."),
            "signin-alert", new Spec("signin-alert", "banner-security", "Account security", m -> "We noticed a new sign-in to your account."),
            "booking-confirmed", new Spec("booking-confirmed", "banner-booking", "Your booking is confirmed",
                    m -> "Your tickets are confirmed. Order " + m.getOrDefault("reference", "") + "."),
            "password-reset", new Spec("notice", "banner-security", "Reset your password", m -> "Use this link to choose a new password."),
            "booking-cancelled", new Spec("notice", "banner-welcome", "Booking cancelled", m -> "Booking " + m.getOrDefault("reference", "") + " was cancelled."),
            "refund-update", new Spec("notice", "banner-welcome", "Refund update", m -> "An update on your refund for " + m.getOrDefault("reference", "") + "."));

    private static final List<String> SERVICES = List.of("Movies", "Events", "Sports", "Plays", "Activities", "Offers");

    private final AppProperties props;
    private final Map<String, String> templates = new ConcurrentHashMap<>();
    private final Map<String, byte[]> images = new ConcurrentHashMap<>();

    public EmailRenderer(AppProperties props) {
        this.props = props;
    }

    public Rendered render(OutboundEmail email) {
        Spec spec = SPECS.get(email.template());
        if (spec == null) throw new IllegalArgumentException("Unknown email template " + email.template());

        Map<String, Object> m = new HashMap<>(brandModel());
        m.putAll(email.model());
        m.put("subject", email.subject());
        m.put("recipient", email.to());
        m.putIfAbsent("firstName", firstName(String.valueOf(m.getOrDefault("name", ""))));
        m.put("bannerCid", "cv-" + spec.banner());
        m.put("bannerAlt", spec.bannerAlt());
        m.putIfAbsent("preheader", spec.preheader().apply(m));
        if ("notice".equals(spec.contentTemplate())) m.putAll(notice(email.template(), m));

        String content = fill(template(spec.contentTemplate()), m);
        m.put("content", content);
        String html = fill(template("layout"), m);

        Map<String, byte[]> inline = new LinkedHashMap<>();
        inline.put("cv-logo", image("logo"));
        inline.put("cv-logo-light", image("logo-light"));
        inline.put("cv-" + spec.banner(), image(spec.banner()));
        inline.putAll(email.inlineImages());
        return new Rendered(email.subject(), html, toText(content, m), inline);
    }

    // ------------------------------------------------------------------------------------------- notices

    private Map<String, Object> notice(String template, Map<String, Object> m) {
        String site = String.valueOf(m.get("siteUrl"));
        return switch (template) {
            case "password-reset" -> noticeModel("Reset your password", "Account security",
                    List.of("We received a request to reset the password for your " + m.get("brandName") + " account.",
                            "This link is valid for 30 minutes. If you didn't ask for it, you can safely ignore this email; your password won't change."),
                    "Choose a new password", String.valueOf(m.get("link")));
            case "booking-cancelled" -> noticeModel("Booking " + m.get("reference") + " cancelled", "Cancellation confirmed",
                    List.of("Your booking " + m.get("reference") + " has been cancelled and the seats have been released.",
                            String.valueOf(m.getOrDefault("refundNote", ""))),
                    "View booking", site + "/bookings");
            case "refund-update" -> noticeModel("Refund " + String.valueOf(m.getOrDefault("status", "")).toLowerCase(Locale.ROOT),
                    "Order " + m.get("reference"),
                    List.of("Your refund of ₹" + m.get("amount") + " for booking " + m.get("reference") + " is now "
                                    + String.valueOf(m.getOrDefault("status", "")).toLowerCase(Locale.ROOT) + ".",
                            "Refunds usually reach your account within 5–7 working days, depending on your bank."),
                    "View booking", site + "/bookings");
            default -> Map.of();
        };
    }

    private static Map<String, Object> noticeModel(String heading, String kicker, List<String> paragraphs, String button, String url) {
        StringBuilder body = new StringBuilder();
        for (String p : paragraphs) {
            if (p == null || p.isBlank()) continue;
            body.append("<p style=\"margin:0 0 16px 0;\">").append(esc(p)).append("</p>\n");
        }
        String btn = """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:6px 0 26px 0;"><tr>
                  <td style="border-radius:999px;background:#EA580C;background-image:linear-gradient(90deg,#F97316,#EA580C);">
                    <a href="%s" style="display:inline-block;padding:13px 28px;font-family:Arial,Helvetica,sans-serif;font-size:15px;font-weight:bold;color:#FFFFFF;text-decoration:none;border-radius:999px;">%s</a>
                  </td></tr></table>
                """.formatted(esc(url), esc(button));
        Map<String, Object> out = new HashMap<>();
        out.put("heading", heading);
        out.put("kicker", kicker);
        out.put("bodyHtml", body.toString());
        out.put("buttonHtml", btn);
        return out;
    }

    // ------------------------------------------------------------------------------------------- brand

    private Map<String, Object> brandModel() {
        AppProperties.Brand b = props.brand();
        String name = b != null && b.name() != null ? b.name() : props.appName();
        String site = props.frontendUrl() == null ? "" : props.frontendUrl().replaceAll("/$", "");
        Map<String, Object> m = new HashMap<>();
        m.put("brandName", name);
        m.put("legalName", b != null && b.legalName() != null ? b.legalName() : name);
        m.put("motto", b != null && b.motto() != null ? b.motto() : "Every seat tells a story.");
        String support = b != null && b.supportEmail() != null ? b.supportEmail() : "support@example.com";
        m.put("supportEmail", support);
        String phone = b != null && b.supportPhone() != null ? b.supportPhone() : "";
        m.put("supportPhone", phone);
        m.put("supportPhoneTel", phone.replaceAll("[^+0-9]", ""));
        m.put("address", b != null && b.address() != null ? b.address() : "");
        m.put("siteUrl", site);
        m.put("year", Year.now().getValue());

        StringBuilder services = new StringBuilder();
        for (String s : SERVICES) {
            if (!services.isEmpty()) services.append(" &nbsp;·&nbsp; ");
            services.append("<a href=\"").append(esc(site + "/" + s.toLowerCase(Locale.ROOT)))
                    .append("\" style=\"color:#FED7AA;text-decoration:none;\">").append(s).append("</a>");
        }
        m.put("servicesHtml", services.toString());

        StringBuilder social = new StringBuilder();
        if (b != null) {
            addSocial(social, "Instagram", b.instagramUrl());
            addSocial(social, "X", b.xUrl());
            addSocial(social, "Facebook", b.facebookUrl());
            addSocial(social, "YouTube", b.youtubeUrl());
            addSocial(social, "LinkedIn", b.linkedinUrl());
        }
        m.put("socialHtml", social.toString());
        return m;
    }

    private static void addSocial(StringBuilder out, String label, String url) {
        if (url == null || url.isBlank()) return;
        out.append("<a href=\"").append(esc(url.trim())).append("\" style=\"display:inline-block;margin:4px;padding:7px 14px;")
                .append("border:1px solid #5C4636;border-radius:999px;font-family:Arial,Helvetica,sans-serif;font-size:12px;")
                .append("font-weight:bold;color:#FFFFFF;text-decoration:none;\">").append(label).append("</a>");
    }

    // ------------------------------------------------------------------------------------------- engine

    static String fill(String template, Map<String, Object> m) {
        String withRaw = replace(RAW, template, k -> str(m.get(k)));
        // escape braces too, so a value like "{{x}}" in a customer's name can never become a placeholder
        return replace(VAR, withRaw, k -> esc(str(m.get(k))).replace("{", "&#123;").replace("}", "&#125;"));
    }

    private static String replace(Pattern p, String text, Function<String, String> value) {
        Matcher matcher = p.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(sb, Matcher.quoteReplacement(value.apply(matcher.group(1))));
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    static String firstName(String full) {
        String t = full == null ? "" : full.trim();
        if (t.isEmpty()) return "there";
        return t.split("\\s+")[0];
    }

    /** Plain-text alternative: the content block with tags stripped, plus the support line. */
    private static String toText(String contentHtml, Map<String, Object> m) {
        String t = contentHtml
                .replaceAll("(?is)<(style|script)[^>]*>.*?</\\1>", "")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|tr|h1|h2|table)>", "\n")
                .replaceAll("(?i)</td>", "  ")
                .replaceAll("(?i)<a [^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", "$2 ($1)")
                .replaceAll("<[^>]+>", "");
        t = HtmlUtils.htmlUnescape(t).replaceAll("[ \\t]+", " ").replaceAll(" *\n *", "\n").replaceAll("\n{3,}", "\n\n").trim();
        return t + "\n\n--\n" + m.get("brandName") + " · " + m.get("motto") + "\nHelp: " + m.get("supportEmail")
                + (str(m.get("supportPhone")).isBlank() ? "" : " · " + m.get("supportPhone")) + "\n" + m.get("siteUrl");
    }

    private String template(String name) {
        return templates.computeIfAbsent(name, n -> read("email/templates/" + n + ".html"));
    }

    private byte[] image(String name) {
        return images.computeIfAbsent(name, n -> {
            try (InputStream in = new ClassPathResource("email/images/" + n + ".png").getInputStream()) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Missing email image " + n, e);
            }
        });
    }

    private static String read(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing email template " + path, e);
        }
    }

    /** Escapes only HTML-special characters (&lt; &gt; &amp; quotes); ₹, · and accented letters stay readable in UTF-8 mail. */
    static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
    }
}
