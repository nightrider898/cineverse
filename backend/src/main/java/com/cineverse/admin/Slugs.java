package com.cineverse.admin;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

final class Slugs {
    private Slugs() {}

    static String unique(String text, Predicate<String> exists) {
        String base = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "item";
        if (base.length() > 150) base = base.substring(0, 150);
        String slug = base;
        for (int i = 2; exists.test(slug); i++) slug = base + "-" + i;
        return slug;
    }
}
