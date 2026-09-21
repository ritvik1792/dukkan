package in.dukkan.web;

import in.dukkan.service.GeocodeService;
import in.dukkan.web.dto.GeoDtos.ReverseGeocodeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final GeocodeService geocode;

    public GeoController(GeocodeService geocode) {
        this.geocode = geocode;
    }

    @GetMapping("/reverse")
    public ReverseGeocodeResponse reverse(@RequestParam double lat, @RequestParam double lng) {
        return geocode.reverse(lat, lng);
    }
}
