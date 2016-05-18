package com.vviswana.mpo;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RestController
public class PodCastController {
    @RequestMapping("/podcasts")
    public Collection<PodCast> podCasts(@RequestParam(required=true, value="keyword") String name) {
        List<PodCast> podCasts = new ArrayList<>();
        PodCast podCast = new PodCast();
        podCast.name = "A podcast";
        podCast.description = "Podcast description";
        podCasts.add(podCast);

        return podCasts;
    }
}
