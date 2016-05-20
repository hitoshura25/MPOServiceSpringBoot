package com.vviswana.mpo;

import com.rometools.rome.feed.synd.SyndEnclosure;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.feed.synd.SyndImage;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.vviswana.mpo.api.Episode;
import com.vviswana.mpo.api.PodCast;
import com.vviswana.mpo.api.PodCastDetails;
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

import java.net.URL;
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
    public Collection<PodCast> getPodCasts(@RequestParam(value="keyword") final String name) {
        return searchiTunes(name);
    }

    @RequestMapping("/podcastdetails")
    public PodCastDetails getPodCastDetails(@RequestParam(value="feedUrl") final String feedUrl,
                                            @RequestParam(value="maxEpisodes", required=false) Integer maxEpisodes) {
        SyndFeedInput input = new SyndFeedInput();
        PodCastDetails details;

        try {
            SyndFeed feed = input.build(new XmlReader(new URL(feedUrl)));
            SyndImage image = feed.getImage();
            details = new PodCastDetails(feed.getTitle(), feed.getDescription(), image != null ? image.getUrl() : null);
            int currentEpisode = 0;
            for (SyndEntry entry : feed.getEntries()) {
                Episode episode = new Episode();
                episode.name = entry.getTitle();
                episode.description = entry.getDescription().getValue();
                episode.published = entry.getPublishedDate();

                if (entry.getEnclosures() != null && entry.getEnclosures().size() > 0) {
                    SyndEnclosure enclosure = entry.getEnclosures().get(0);
                    episode.length = enclosure.getLength();
                    episode.type = enclosure.getType();
                    episode.downloadUrl = enclosure.getUrl();
                }

                details.addEpisode(episode);
                currentEpisode++;

                if (maxEpisodes != null && currentEpisode > maxEpisodes) {
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error getting podcast details");
        }
        return details;
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
