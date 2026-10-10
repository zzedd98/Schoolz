package ma.schoolz.erp.globalsearch.api;

import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.globalsearch.application.GlobalSearchService;
import ma.schoolz.erp.globalsearch.application.GlobalSearchService.SearchResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class GlobalSearchController {
    private final GlobalSearchService search;

    public GlobalSearchController(GlobalSearchService search) {
        this.search = search;
    }

    @GetMapping
    public SearchResponse search(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam String q,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return search.search(actor, q, type, page, size);
    }

    @PostMapping("/refresh")
    public GlobalSearchService.RefreshResult refresh(
            @AuthenticationPrincipal SchoolPrincipal actor) {
        return search.refresh(actor);
    }
}
