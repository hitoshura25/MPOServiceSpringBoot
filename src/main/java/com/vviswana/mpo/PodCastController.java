package com.vviswana.mpo;

import com.rometools.modules.itunes.EntryInformation;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Metadata;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.module.Module;
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
import org.springframework.util.StringUtils;
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
    public PodCastDetails getPodCastDetails(
            @RequestParam(value="feedUrl") final String feedUrl,
            @RequestParam(value="maxEpisodes", required=false) final Integer maxEpisodes) {
        final SyndFeedInput input = new SyndFeedInput();
        PodCastDetails details;

        try {
            final SyndFeed feed = input.build(new XmlReader(new URL(feedUrl)));
            final SyndImage image = feed.getImage();
            details = new PodCastDetails(feed.getTitle(), feed.getDescription(), image != null ? image.getUrl() : null);
            int currentEpisode = 0;
            for (SyndEntry entry : feed.getEntries()) {
                final Episode episode = createEpisode(entry);
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

    private Episode createEpisode(final SyndEntry entry) {
        final Episode episode = new Episode();
        episode.name = entry.getTitle();
        episode.description = entry.getDescription().getValue();
        episode.published = entry.getPublishedDate();

        processEnclosures(episode, entry);
        processModules(episode, entry);

        return episode;
    }

    private void processEnclosures(final Episode episode, final SyndEntry entry) {
        if (entry.getEnclosures() != null && entry.getEnclosures().size() > 0) {
            SyndEnclosure enclosure = entry.getEnclosures().get(0);
            episode.length = enclosure.getLength();
            episode.type = enclosure.getType();
            episode.downloadUrl = enclosure.getUrl();
        }
    }

    private void processModules(final Episode episode, final SyndEntry entry) {
        if (entry.getModules() != null) {
            for (Module module : entry.getModules()) {
                if (MediaEntryModule.URI.equals(module.getUri())) {
                    processRssMedia(episode, (MediaEntryModule) module);
                } else if (ITunes.URI.equals(module.getUri())) {
                    processiTunesEntry(episode, (EntryInformation) module);
                }

                if (!StringUtils.isEmpty(episode.artworkUrl)) {
                    break;
                }
            }
        }
    }

    private void processRssMedia(final Episode episode, final MediaEntryModule mediaEntryModule) {
        final MediaContent[] mediaContents = mediaEntryModule.getMediaContents();
        Metadata metadata = null;
        Thumbnail thumbnail = null;

        if (mediaContents != null && mediaContents.length > 0) {
            metadata = mediaContents[0].getMetadata();
        }

        if (metadata != null && metadata.getThumbnail() != null && metadata.getThumbnail().length > 0) {
            thumbnail = metadata.getThumbnail()[0];
        }

        if (thumbnail != null && thumbnail.getUrl() != null) {
            episode.artworkUrl = thumbnail.getUrl().toString();
        }
    }

    private void processiTunesEntry(Episode episode, EntryInformation entryInformation) {
        if (entryInformation.getImage() != null) {
            episode.artworkUrl = entryInformation.getImage().toString();
        }
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
