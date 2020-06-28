package com.vviswana.mpo;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.Weigher;
import com.rometools.modules.itunes.EntryInformation;
import com.rometools.modules.itunes.ITunes;
import com.rometools.modules.mediarss.MediaEntryModule;
import com.rometools.modules.mediarss.types.MediaContent;
import com.rometools.modules.mediarss.types.Metadata;
import com.rometools.modules.mediarss.types.Thumbnail;
import com.rometools.rome.feed.module.Module;
import com.rometools.rome.feed.synd.*;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.vviswana.mpo.api.Episode;
import com.vviswana.mpo.api.PodCast;
import com.vviswana.mpo.api.PodCastDetails;
import com.vviswana.mpo.itunes.Result;
import com.vviswana.mpo.itunes.SearchResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
public class PodCastController {
    private static final String ITUNES_URL = "https://itunes.apple.com/search?term={keyword}&entity=podcast";
    private static final Logger log = LoggerFactory.getLogger(PodCastController.class);
    private RestTemplate restTemplate;
    private LoadingCache<String, SyndFeed> feedCache;

    public PodCastController() {
        restTemplate = new RestTemplate();
        List<HttpMessageConverter<?>> converters = restTemplate.getMessageConverters();
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(new MediaType("text", "javascript", Charset.forName("UTF-8")));

        jsonConverter.setSupportedMediaTypes(supportedMediaTypes);
        converters.add(jsonConverter);
        restTemplate.setMessageConverters(converters);
        feedCache = createCache();
    }

    @RequestMapping(value = "/podcasts", produces=APPLICATION_JSON_VALUE)
    public Collection<PodCast> getPodCasts(@RequestParam(value="keyword") final String name) {
        return searchiTunes(name);
    }

    @RequestMapping(value = "/podcastdetails", produces=APPLICATION_JSON_VALUE)
    public PodCastDetails getPodCastDetails(
            @RequestParam(value="feedUrl") final String feedUrl,
            @RequestParam(value="maxEpisodes", required=false) final Integer maxEpisodes) {
        PodCastDetails details;

        try {
            final SyndFeed feed = feedCache.get(feedUrl);
            final SyndImage image = feed.getImage();
            final String feedImageUrl = image != null ? image.getUrl() : null;

            // Also get image if from modules (iTunes)
            final String moduleImageUrl = getImageUrlFromModules(feed);

            details = new PodCastDetails(feed.getTitle(), feed.getDescription(),
                    moduleImageUrl != null ? moduleImageUrl : feedImageUrl);
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
            log.warn("Error getting podcast details", e);
            throw new RuntimeException("Error getting podcast details");
        }
        return details;
    }

    @RequestMapping(value = "/podcastupdate",produces=APPLICATION_JSON_VALUE)
    public Episode getPodcastUpdate(
            @RequestParam(value="feedUrl") final String feedUrl,
            @RequestParam(value="publishTimestamp", required=false) final Long publishTimestamp) {
        Episode episode = null;
        try {
            final SyndFeed feed = feedCache.get(feedUrl);
            if (feed.getEntries() != null && feed.getEntries().size() > 0) {
                final SyndEntry entry = feed.getEntries().get(0);
                if (publishTimestamp == null || entry.getPublishedDate().compareTo(new Date(publishTimestamp)) > 0) {
                    episode = createEpisode(entry);
                }
            }
        } catch (Exception e) {
            log.warn("Error getting podcast update", e);
            throw new RuntimeException("Error getting podcast update");
        }

        return episode;
    }

    private LoadingCache<String, SyndFeed> createCache() {
        LoadingCache<String, SyndFeed> cache = CacheBuilder.newBuilder()
                .expireAfterWrite(15, TimeUnit.MINUTES)
                .maximumWeight(100000)
                .weigher(new Weigher<String, SyndFeed>() {
                    public int weigh(String feedUrl, SyndFeed feed) {
                        return feed.getEntries().size();
                    }
                })
                .build(
                        new CacheLoader<String, SyndFeed>() {
                            public SyndFeed load(String feedUrl) throws IOException, FeedException {
                                final SyndFeedInput input = new SyndFeedInput();
                                return input.build(new XmlReader(new URL(feedUrl)));
                            }
                        });

        return cache;
    }

    private Episode createEpisode(final SyndEntry entry) {
        final Episode episode = new Episode();
        episode.name = entry.getTitle();
        final SyndContent description = entry.getDescription();
        episode.description = (description != null) ? description.getValue() : null;
        episode.published = entry.getPublishedDate().getTime();

        processEnclosures(episode, entry);
        processModules(episode, entry);

        return episode;
    }

    private void processEnclosures(final Episode episode, final SyndEntry entry) {
        if (entry.getEnclosures() != null && entry.getEnclosures().size() > 0) {
            SyndEnclosure enclosure = entry.getEnclosures().get(0);
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
            }
        }
    }

    private String getImageUrlFromModules(SyndFeed feed) {
        String imageUrl = null;
        if (feed.getModules() != null) {
            for (Module module : feed.getModules()) {
                if (ITunes.URI.equals(module.getUri())) {
                    ITunes iTunesModule = (ITunes) module;
                    if (iTunesModule.getImage() != null) {
                        imageUrl = iTunesModule.getImage().toString();
                    }
                }
            }
        }

        return imageUrl;
    }

    private void processRssMedia(final Episode episode, final MediaEntryModule mediaEntryModule) {
        final MediaContent[] mediaContents = mediaEntryModule.getMediaContents();
        Metadata metadata = null;
        Thumbnail thumbnail = null;

        if (mediaContents != null && mediaContents.length > 0) {
            MediaContent mediaContent = mediaContents[0];
            metadata = mediaContent.getMetadata();

            if (mediaContent.getDuration() != null && mediaContent.getDuration() > 0) {
                episode.length = mediaContents[0].getDuration();
            }
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

        if (entryInformation.getDuration() != null) {
            episode.length = entryInformation.getDuration().getMilliseconds() / 1000;
        }
    }

    private Collection<PodCast> searchiTunes(final String keyword) {
        List<PodCast> podCasts = new ArrayList<>();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<SearchResults> response = restTemplate.exchange(ITUNES_URL, HttpMethod.GET, entity,
                SearchResults.class, keyword);

        SearchResults results = response.getBody();
        if (results != null && results.getResults() != null) {
            for (Result result : results.getResults()) {
                PodCast podCast = new PodCast();
                podCast.name = result.getTrackName();
                podCast.author = result.getArtistName();
                podCast.genres = result.getGenres();
                podCast.feedUrl = result.getFeedUrl();
                setArtworkUrls(podCast, result);
                podCasts.add(podCast);
            }
        }

        return podCasts;
    }

    private void setArtworkUrls(PodCast podCast, Result result) {
        podCast.artworkUrl = result.getArtworkUrl600() != null ?
                result.getArtworkUrl600() : result.getArtworkUrl100();
        podCast.smallArtworkUrl = result.getArtworkUrl100() != null ?
                result.getArtworkUrl100() : null;
    }
}
