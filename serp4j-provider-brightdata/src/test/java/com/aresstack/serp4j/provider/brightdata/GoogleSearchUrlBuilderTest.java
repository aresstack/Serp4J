package com.aresstack.serp4j.provider.brightdata;

import com.aresstack.serp4j.core.model.DeviceType;
import com.aresstack.serp4j.core.model.GeoLocation;
import com.aresstack.serp4j.core.model.SafeSearch;
import com.aresstack.serp4j.core.model.SearchLocale;
import com.aresstack.serp4j.core.model.SearchRequest;
import com.aresstack.serp4j.core.model.TimeRange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleSearchUrlBuilderTest {

    private static final com.aresstack.serp4j.core.model.id.ProviderId ID =
            com.aresstack.serp4j.core.model.id.ProviderId.of("brightdata");

    private final GoogleSearchUrlBuilder builder = new GoogleSearchUrlBuilder(GoogleSearchUrlBuilder.DEFAULT_BASE);

    @Test
    void plainQueryIsOnlyQ() {
        assertThat(builder.build(SearchRequest.builder("pizza").build(), Optional.<BrightDataOptions>empty()))
                .isEqualTo("https://www.google.com/search?q=pizza");
    }

    @Test
    void neutralParametersAreTranslated() {
        SearchRequest request = SearchRequest.builder("pizza near me")
                .offset(20)
                .locale(SearchLocale.builder().countryCode("DE").languageCode("de")
                        .location(GeoLocation.named("Berlin,Berlin,Germany")).build())
                .safeSearch(SafeSearch.STRICT)
                .timeRange(TimeRange.pastWeek())
                .device(DeviceType.MOBILE)
                .build();
        String url = builder.build(request, Optional.<BrightDataOptions>empty());
        assertThat(url).startsWith("https://www.google.com/search?q=pizza")
                .contains("start=20", "hl=de", "gl=de", "uule=Berlin", "safe=active", "tbs=qdr%3Aw", "brd_mobile=1")
                .doesNotContain("num=");
    }

    @Test
    void optionsWinOverDerivedValues() {
        SearchRequest request = SearchRequest.builder("pizza").device(DeviceType.MOBILE).build();
        BrightDataOptions options = BrightDataOptions.builder(ID).mobileProfile("ios").browser("safari")
                .uule("New York,New York,United States").brdJson("json").extraUrlParameter("lr", "lang_en").build();
        String url = builder.build(request, Optional.of(options));
        assertThat(url).contains("brd_mobile=ios", "brd_browser=safari", "brd_json=json", "lr=lang_en", "uule=New")
                .doesNotContain("brd_mobile=1");
    }

    @Test
    void customRangeUsesCdr() {
        assertThat(GoogleSearchUrlBuilder.tbs(TimeRange.between(Instant.parse("2026-01-02T00:00:00Z"),
                Instant.parse("2026-03-04T00:00:00Z")))).isEqualTo("cdr:1,cd_min:1/2/2026,cd_max:3/4/2026");
        assertThat(GoogleSearchUrlBuilder.mobile(DeviceType.TABLET)).isEqualTo("ipad");
        assertThat(GoogleSearchUrlBuilder.mobile(DeviceType.DESKTOP)).isEqualTo("0");
    }
}
