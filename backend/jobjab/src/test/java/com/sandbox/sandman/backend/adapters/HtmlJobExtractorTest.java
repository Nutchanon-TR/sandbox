package com.sandbox.sandman.backend.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlJobExtractorTest {

    private final HtmlJobExtractor extractor = new HtmlJobExtractor(new ObjectMapper());

    @Test
    void extractsJobPostingJsonLd() {
        String html = """
                <html>
                  <head>
                    <script type="application/ld+json">
                    {
                      "@context": "https://schema.org",
                      "@type": "JobPosting",
                      "title": "Frontend Developer",
                      "description": "<p>Build React and TypeScript apps with REST API integrations.</p>",
                      "datePosted": "2026-05-01T09:00:00+07:00",
                      "employmentType": "FULL_TIME",
                      "hiringOrganization": { "name": "ABC Tech" },
                      "jobLocation": {
                        "@type": "Place",
                        "address": {
                          "streetAddress": "Asok",
                          "addressLocality": "Bangkok",
                          "addressCountry": "TH"
                        }
                      },
                      "baseSalary": {
                        "currency": "THB",
                        "value": {
                          "minValue": 45000,
                          "maxValue": 70000
                        }
                      }
                    }
                    </script>
                  </head>
                  <body>Frontend Developer opening</body>
                </html>
                """;

        ExtractedJobPage result = extractor.extract(html, "https://example.com/jobs/1", null, null);

        assertThat(result.title()).isEqualTo("Frontend Developer");
        assertThat(result.company()).isEqualTo("ABC Tech");
        assertThat(result.locationText()).contains("Asok", "Bangkok", "TH");
        assertThat(result.salaryMin()).isEqualTo(45000);
        assertThat(result.salaryMax()).isEqualTo(70000);
        assertThat(result.skills()).contains("React", "TypeScript", "REST API");
        assertThat(result.description()).contains("Build React and TypeScript apps");
    }
}
