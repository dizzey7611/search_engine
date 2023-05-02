package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import searchengine.config.SitesList;
import searchengine.dto.BadRequest;
import searchengine.dto.SearchResults;
import searchengine.dto.search.StatisticsSearch;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.repository.SiteRepository;
import searchengine.services.impl.ApiService;
import searchengine.services.impl.LemmaService;
import searchengine.services.impl.SearchService;
import searchengine.services.impl.StatisticsService;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final ApiService apiService;
    private final LemmaService lemmaService;
    private final SiteRepository siteRepository;
    private final SearchService searchService;
    private final AtomicBoolean indexingProcessing = new AtomicBoolean(false);
    private final SitesList sitesList;

    @GetMapping("/search")
    public SearchResults search(@RequestParam(name = "query", required = false, defaultValue = "")
                                         String request, @RequestParam(name = "site", required = false, defaultValue = "") String site,
                                         @RequestParam(name = "offset", required = false, defaultValue = "0") int offset,
                                         @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) throws IOException {
        if (request.isEmpty()) {
            return null;
        } else {
            List<StatisticsSearch> searchData;
            if (!site.isEmpty()) {
                if (siteRepository.findByUrl(site) == null) {
                    return null;
                } else {
                    searchData = searchService.siteSearch(request, site, offset, limit);
                }
            } else {
                searchData = searchService.allSiteSearch(request, offset, limit);
            }
            searchData.forEach(System.out::println);
            return (new SearchResults(true, searchData.size(), searchData));
        }
    }


    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }
    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing() {
        if(indexingProcessing.get()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("'result' : false, " +
                    "'error' : Индексация уже запущена");
        } else {
            indexingProcessing.set(true);
            Runnable start = () -> apiService.startIndexing(indexingProcessing);
            new Thread(start).start();
            return ResponseEntity.status(HttpStatus.OK).body("'result' : true");
        }
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing() {
        if (!indexingProcessing.get()) {
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body("'result' : false, " +
                    "'error' : Индексация не запущена");
        } else {
            indexingProcessing.set(false);
            return ResponseEntity.status(HttpStatus.OK).body("'result' : true ");
        }
    }

    @GetMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam URL url) throws IOException {

        try {
            sitesList.getSites().stream().filter(site -> url.getHost().equals(site.getUrl().getHost())).findFirst().orElseThrow();
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("result: false " +
                    "error: Данная страница находится за пределами сайтов " +
                    "указанных в конфигурационном файле");
        }
        lemmaService.getLemmasFromUrl(url);
        return ResponseEntity.status(HttpStatus.OK).body("result: true");
    }
}
