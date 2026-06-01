package com.sandbox.sandman.backend.adapters;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SearchCriteriaAdapterTest {
    @Test
    void userUrlAdapterAcceptsMultipleUrls() {
        UserUrlAdapter adapter = new UserUrlAdapter(null);
        JobSearchCriteria criteria = new JobSearchCriteria(
                "Frontend Developer",
                "Bangkok",
                10,
                "https://example.com/jobs/one",
                List.of("https://example.com/jobs/two", "https://example.com/jobs/one"),
                List.of(),
                List.of("user_url"),
                Map.of()
        );

        List<ExternalJobRef> refs = adapter.search(criteria);

        assertThat(refs).extracting(ExternalJobRef::url)
                .containsExactly("https://example.com/jobs/one", "https://example.com/jobs/two");
    }

    @Test
    void userUrlAdapterDeduplicatesTrackingUrlVariants() {
        UserUrlAdapter adapter = new UserUrlAdapter(null);
        JobSearchCriteria criteria = new JobSearchCriteria(
                "Frontend Developer",
                "Bangkok",
                10,
                null,
                List.of(
                        "https://Example.com/jobs/one/?utm_source=linkedin&b=2&a=1#apply",
                        "https://example.com/jobs/one?a=1&b=2"
                ),
                List.of(),
                List.of("user_url"),
                Map.of()
        );

        List<ExternalJobRef> refs = adapter.search(criteria);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).sourceJobKey()).isEqualTo("https://example.com/jobs/one?a=1&b=2");
    }

    @Test
    void officialAdaptersReadPerSearchTargetsFromSourceConfig() {
        JobSearchCriteria criteria = new JobSearchCriteria(
                null,
                null,
                10,
                null,
                List.of(),
                List.of(),
                List.of("greenhouse"),
                Map.of("boards", List.of("acme", "beta"))
        );

        assertThat(AdapterSupport.configuredBoards(criteria, "boards")).containsExactly("acme", "beta");
    }

    @Test
    void manualJobAdapterNormalizesPastedJobDescription() {
        ManualJobAdapter adapter = new ManualJobAdapter();
        var manualJob = new com.sandbox.sandman.backend.model.dto.ManualJobRequest(
                "React Engineer",
                "Acme",
                "Bangkok",
                13.7563,
                100.5018,
                50000,
                90000,
                "THB",
                "Full-time",
                "Hybrid",
                List.of("React"),
                "Build TypeScript and Next.js apps.",
                "https://example.com/apply"
        );
        JobSearchCriteria criteria = new JobSearchCriteria(
                null,
                null,
                10,
                null,
                List.of(),
                List.of(manualJob),
                List.of("manual_jd"),
                Map.of()
        );

        List<ExternalJobRef> refs = adapter.search(criteria);
        RawJobPayload raw = adapter.fetchDetail(refs.get(0));
        NormalizedJobRecord normalized = adapter.normalize(raw);

        assertThat(normalized.title()).isEqualTo("React Engineer");
        assertThat(normalized.company()).isEqualTo("Acme");
        assertThat(normalized.skills()).contains("React", "TypeScript", "Next.js");
        assertThat(normalized.locationLatitude()).isEqualTo(13.7563);
    }

    @Test
    void sitemapAdapterExtractsJobUrlsAndNestedSitemaps() {
        SitemapAdapter adapter = new SitemapAdapter(null);
        String urlSet = """
                <urlset>
                  <url><loc>https://example.com/jobs/frontend-engineer</loc></url>
                  <url><loc>https://example.com/about</loc></url>
                </urlset>
                """;
        String sitemapIndex = """
                <sitemapindex>
                  <sitemap><loc>https://example.com/jobs-sitemap.xml</loc></sitemap>
                </sitemapindex>
                """;

        assertThat(adapter.urlsFromSitemapXml(urlSet))
                .containsExactly("https://example.com/jobs/frontend-engineer", "https://example.com/about");
        assertThat(adapter.sitemapIndexUrlsFromXml(sitemapIndex))
                .containsExactly("https://example.com/jobs-sitemap.xml");
    }

    @Test
    void canonicalJobUrlRemovesTrackingParametersAndFragments() {
        assertThat(AdapterSupport.canonicalJobUrl("https://Example.com:443/jobs/frontend/?utm_campaign=x&b=2&a=1#apply"))
                .isEqualTo("https://example.com/jobs/frontend?a=1&b=2");
    }
}
