package in.dukkan.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.dukkan.web.dto.GeoDtos.ReverseGeocodeResponse;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeocodeService {

    private static final long MIN_INTERVAL_MS = 1100;
    private static final int CACHE_SOFT_LIMIT = 4000;

    private final RestClient nominatim;
    private final long cacheTtlMs;
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Object lock = new Object();
    private long lastRequestAtMs;

    public GeocodeService(
            @Value("${app.nominatim.base-url}") String baseUrl,
            @Value("${app.nominatim.user-agent}") String userAgent,
            @Value("${app.nominatim.cache-ttl-hours:24}") long cacheTtlHours,
            @Value("${app.nominatim.timeout-ms:8000}") int timeoutMs) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.nominatim = RestClient.builder()
                .baseUrl(trimSlash(baseUrl))
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.cacheTtlMs = Duration.ofHours(Math.max(1, cacheTtlHours)).toMillis();
    }

    public ReverseGeocodeResponse reverse(double lat, double lng) {
        validate(lat, lng);
        String key = cacheKey(lat, lng);
        ReverseGeocodeResponse cached = fromCache(key);
        if (cached != null) {
            return cached;
        }
        synchronized (lock) {
            cached = fromCache(key);
            if (cached != null) {
                return cached;
            }
            throttleLocked();
            ReverseGeocodeResponse resolved = fetchNominatim(lat, lng);
            store(key, resolved);
            return resolved;
        }
    }

    private ReverseGeocodeResponse fetchNominatim(double lat, double lng) {
        NominatimResponse raw;
        try {
            raw = nominatim.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/reverse")
                            .queryParam("lat", formatCoord(lat))
                            .queryParam("lon", formatCoord(lng))
                            .queryParam("format", "json")
                            .queryParam("addressdetails", 1)
                            .queryParam("zoom", 18)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        if (response.getStatusCode().value() == 404) {
                            throw new ResponseStatusException(
                                    HttpStatus.NOT_FOUND, "No address found for these coordinates");
                        }
                        if (response.getStatusCode().value() == 429) {
                            throw new ResponseStatusException(
                                    HttpStatus.TOO_MANY_REQUESTS,
                                    "Address lookup is busy. Try again in a moment.");
                        }
                        throw lookupFailed();
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        throw lookupFailed();
                    })
                    .body(NominatimResponse.class);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (RestClientException e) {
            throw lookupFailed();
        }

        if (raw == null || (raw.error() != null && !raw.error().isBlank())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No address found for these coordinates");
        }

        NominatimAddress address = raw.address();
        String formatted = formatAddress(address, raw.displayName());
        if (formatted.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No address found for these coordinates");
        }

        return new ReverseGeocodeResponse(
                formatted,
                blankToNull(address == null ? null : address.houseNumber()),
                blankToNull(address == null ? null : address.road()),
                blankToNull(address == null ? null : suburbOf(address)),
                blankToNull(address == null ? null : cityOf(address)),
                blankToNull(address == null ? null : address.state()),
                blankToNull(address == null ? null : address.postcode()),
                blankToNull(address == null ? null : address.country()),
                lat,
                lng,
                blankToNull(raw.displayName()));
    }

    private ReverseGeocodeResponse fromCache(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAtMs < System.currentTimeMillis()) {
            cache.remove(key, entry);
            return null;
        }
        return entry.value;
    }

    private void store(String key, ReverseGeocodeResponse value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + cacheTtlMs));
        if (cache.size() > CACHE_SOFT_LIMIT) {
            long now = System.currentTimeMillis();
            cache.entrySet().removeIf(item -> item.getValue().expiresAtMs < now);
        }
    }

    private void throttleLocked() {
        long now = System.currentTimeMillis();
        long wait = lastRequestAtMs + MIN_INTERVAL_MS - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Address lookup interrupted");
            }
        }
        lastRequestAtMs = System.currentTimeMillis();
    }

    private static void validate(double lat, double lng) {
        if (!Double.isFinite(lat) || !Double.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lat and lng must be valid coordinates");
        }
    }

    private static String cacheKey(double lat, double lng) {
        return String.format(Locale.US, "%.4f,%.4f", lat, lng);
    }

    private static String formatCoord(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String formatAddress(NominatimAddress address, String displayName) {
        if (address == null) {
            return displayName == null ? "" : displayName.trim();
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, joinPresent(address.houseNumber(), address.road()));
        addIfPresent(parts, suburbOf(address));
        addIfPresent(parts, cityOf(address));
        addIfPresent(parts, address.state());
        addIfPresent(parts, address.postcode());
        addIfPresent(parts, address.country());
        if (parts.isEmpty()) {
            return displayName == null ? "" : displayName.trim();
        }
        return String.join(", ", parts);
    }

    private static String suburbOf(NominatimAddress address) {
        return firstPresent(address.suburb(), address.neighbourhood(), address.neighborhood());
    }

    private static String cityOf(NominatimAddress address) {
        return firstPresent(
                address.city(), address.town(), address.village(), address.municipality(), address.cityDistrict());
    }

    private static String firstPresent(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String joinPresent(String left, String right) {
        boolean hasLeft = left != null && !left.isBlank();
        boolean hasRight = right != null && !right.isBlank();
        if (hasLeft && hasRight) {
            return left.trim() + " " + right.trim();
        }
        if (hasLeft) {
            return left.trim();
        }
        if (hasRight) {
            return right.trim();
        }
        return null;
    }

    private static void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.isBlank() && !parts.contains(value)) {
            parts.add(value.trim());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String trimSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Nominatim base URL is required");
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static ResponseStatusException lookupFailed() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not resolve address. Try again shortly.");
    }

    private record CacheEntry(ReverseGeocodeResponse value, long expiresAtMs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimResponse(
            @JsonProperty("display_name") String displayName,
            String lat,
            String lon,
            NominatimAddress address,
            String error) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record NominatimAddress(
            @JsonProperty("house_number") String houseNumber,
            String road,
            String suburb,
            String neighbourhood,
            String neighborhood,
            String city,
            String town,
            String village,
            String municipality,
            @JsonProperty("city_district") String cityDistrict,
            String state,
            String postcode,
            String country) {}
}
