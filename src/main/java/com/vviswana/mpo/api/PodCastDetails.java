package com.vviswana.mpo.api;

import java.util.ArrayList;
import java.util.Collection;

public class PodCastDetails {
    public String name;
    public String description;
    public String imageUrl;
    public Collection<Episode> episodes;

    public PodCastDetails() {

    }

    public PodCastDetails(final String name, final String description, final String imageUrl) {
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public void addEpisode(final Episode episode) {
        if (episodes == null) {
            episodes = new ArrayList<>();
        }

        episodes.add(episode);
    }
}
