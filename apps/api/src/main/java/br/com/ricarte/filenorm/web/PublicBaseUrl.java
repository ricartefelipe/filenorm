package br.com.ricarte.filenorm.web;

import br.com.ricarte.filenorm.config.FilenormProperties;
import jakarta.servlet.http.HttpServletRequest;

public final class PublicBaseUrl {
    private PublicBaseUrl() {
    }

    public static String resolve(HttpServletRequest request, FilenormProperties properties) {
        if (!properties.auth().trustForwardedHost()) {
            return properties.auth().appBaseUrl().replaceAll("/$", "");
        }
        String host = firstNonBlank(request.getHeader("X-Forwarded-Host"), request.getHeader("Host"));
        if (host == null) {
            return properties.auth().appBaseUrl().replaceAll("/$", "");
        }
        String proto = firstNonBlank(request.getHeader("X-Forwarded-Proto"), request.getScheme());
        if (proto == null || proto.isBlank()) {
            proto = "https";
        }
        return proto + "://" + host.split(",")[0].trim();
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}
