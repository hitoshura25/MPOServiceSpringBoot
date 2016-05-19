package com.vviswana.mpo;

import com.vviswana.mpo.itunes.Result;
import com.vviswana.mpo.itunes.SearchResults;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
public class PodCastController {
    private static final String ITUNES_URL = "https://itunes.apple.com/search?term={keyword}&entity=podcast";

    private RestTemplate restTemplate;

    public PodCastController() {
        restTemplate = new RestTemplate();
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(new MediaType("text", "javascript", Charset.forName("UTF-8")));

        jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
        converters.add(jsonConverter);
        restTemplate.setMessageConverters(converters);
    }

    @RequestMapping("/podcasts")
    public Collection<PodCast> podCasts(@RequestParam(value="keyword") String name) {
        return searchiTunes(name);
    }

    private Collection<PodCast> searchiTunes(final String keyword) {
        List<PodCast> podCasts = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<SearchResults> response = restTemplate.exchange(ITUNES_URL, HttpMethod.GET, entity,
                SearchResults.class, keyword);

        SearchResults results = response.getBody();
        if (results != null && results.getResults() != null) {
            for (Result result : results.getResults()) {
                PodCast podCast = new PodCast();
                podCast.name = result.getTrackName();
                podCast.artworkUrl = result.getArtworkUrl100();
                podCast.author = result.getArtistName();
                podCast.genres = result.getGenres();
                podCast.feedUrl = result.getFeedUrl();
                podCasts.add(podCast);
            }
        }

        return podCasts;
    }
}
