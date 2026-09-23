package in.dukkan.web;

import in.dukkan.service.DiscoverySearchService;
import in.dukkan.service.DiscoverySearchService.SearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final DiscoverySearchService search;

    public SearchController(DiscoverySearchService search) {
        this.search = search;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String category) {
        return search.search(q, filter, lat, lng, category);
    }
}
